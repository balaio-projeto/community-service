package br.com.balaio.community.video.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.balaio.community.shared.config.WebConfig;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.SecurityConfig;
import br.com.balaio.community.shared.security.UsuarioAutenticadoArgumentResolver;
import br.com.balaio.community.video.application.ComentarioVideoService;
import br.com.balaio.community.video.application.VideoService;
import br.com.balaio.community.video.application.VideoService.VideoComNumeros;
import br.com.balaio.community.video.application.port.out.ArmazenamentoMidiaPort;
import br.com.balaio.community.video.domain.TipoMidia;
import br.com.balaio.community.video.domain.Video;

@WebMvcTest(VideosController.class)
@Import({ SecurityConfig.class, WebConfig.class, UsuarioAutenticadoArgumentResolver.class,
        VideosControllerTest.AsyncSincronoConfig.class })
class VideosControllerTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class AsyncSincronoConfig implements WebMvcConfigurer {
        @Override
        public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
            configurer.setTaskExecutor(new TaskExecutorAdapter(new SyncTaskExecutor()));
        }
    }

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 14, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired MockMvc mvc;
    @MockitoBean VideoService videos;
    @MockitoBean ComentarioVideoService comentarios;
    @MockitoBean JwtDecoder decoder;

    @Test
    void visitanteListaVideosNoFormatoDaTela() throws Exception {
        UUID artesao = UUID.randomUUID();
        var video = Video.rascunho(artesao, "vaso", "Asa do vaso", List.of("ceramica"), "0:48", AGORA);
        when(videos.listar(eq(false), eq(Optional.empty()), eq(Pagina.primeira())))
                .thenReturn(List.of(new VideoComNumeros(video, 3, 2)));

        mvc.perform(get("/api/v1/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados[0].artesao").value("artesao-" + artesao))
                .andExpect(jsonPath("$.dados[0].curtidas").value(3))
                .andExpect(jsonPath("$.dados[0].comentarios").value(2))
                .andExpect(jsonPath("$.dados[0].publicadoEm").value("Rascunho"))
                .andExpect(jsonPath("$.dados[0].url").isEmpty());
    }

    @Test
    void entregaSoOTrechoPedidoComRange() throws Exception {
        UUID id = UUID.randomUUID();
        when(videos.abrirMidia(eq(id), eq(TipoMidia.VIDEO), any())).thenReturn(midia("0123456789"));

        var envio = mvc.perform(get("/api/v1/videos/" + id + "/arquivo").header("Range", "bytes=2-5"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(envio))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().string("2345"));
    }

    @Test
    void semRangeEntregaOArquivoInteiro() throws Exception {
        UUID id = UUID.randomUUID();
        when(videos.abrirMidia(eq(id), eq(TipoMidia.VIDEO), any())).thenReturn(midia("0123456789"));

        var envio = mvc.perform(get("/api/v1/videos/" + id + "/arquivo"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(envio)).andExpect(status().isOk()).andExpect(content().string("0123456789"));
    }

    @Test
    void rangeForaDoArquivoRetorna416() throws Exception {
        UUID id = UUID.randomUUID();
        when(videos.abrirMidia(eq(id), eq(TipoMidia.VIDEO), any())).thenReturn(midia("0123456789"));

        mvc.perform(get("/api/v1/videos/" + id + "/arquivo").header("Range", "bytes=50-60"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */10"));
    }

    private static VideoService.MidiaParaEntrega midia(String conteudo) {
        byte[] bytes = conteudo.getBytes();
        return new VideoService.MidiaParaEntrega(new ArmazenamentoMidiaPort.MidiaArmazenada() {
            @Override
            public long tamanho() {
                return bytes.length;
            }

            @Override
            public java.io.InputStream ler(long primeiroByte, long ultimoByte) {
                return new java.io.ByteArrayInputStream(bytes, (int) primeiroByte,
                        (int) (ultimoByte - primeiroByte + 1));
            }
        }, "video/mp4");
    }

    @Test
    void visitanteRegistraVisualizacaoSemLogin() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/v1/videos/" + id + "/visualizacoes")).andExpect(status().isNoContent());

        verify(videos).registrarVisualizacao(id, Optional.empty());
    }

    @Test
    void listagemRepassaAPaginaPedida() throws Exception {
        when(videos.listar(eq(false), eq(Optional.empty()), eq(Pagina.de(3, 20)))).thenReturn(List.of());

        mvc.perform(get("/api/v1/videos").param("pagina", "3").param("tamanho", "20")).andExpect(status().isOk());

        verify(videos).listar(false, Optional.empty(), Pagina.de(3, 20));
    }

    @Test
    void rotaDeVideoNaoListadaComoPublicaExigeLogin() throws Exception {
        mvc.perform(get("/api/v1/videos/" + UUID.randomUUID() + "/rascunhos-internos"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/videos/" + UUID.randomUUID() + "/comentarios/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void envioConcorrenteNoMesmoVideoRetorna409() throws Exception {
        UUID id = UUID.randomUUID();
        when(videos.enviarMidia(eq(id), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Video.class, id));

        mvc.perform(put("/api/v1/videos/" + id + "/capa").with(jwt().jwt(t -> t.subject(UUID.randomUUID().toString())
                        .claim("papel", "artesao"))).contentType("image/png").content(new byte[] { 1, 2, 3 }))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro.codigo").value("ALTERACAO_CONCORRENTE"));
    }

    @Test
    void criarVideoExigeLogin() throws Exception {
        mvc.perform(post("/api/v1/videos").contentType(MediaType.APPLICATION_JSON).content("{\"legenda\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(videos);
    }

    @Test
    void comentarioCriadoTrazAutorEMarcaDoArtesao() throws Exception {
        var video = Video.rascunho(UUID.randomUUID(), null, "Legenda", List.of(), null, AGORA);
        var comentario = br.com.balaio.community.video.domain.ComentarioVideo.comentar(video, video.artesaoId(),
                "Mestre Nuca", "Obrigado!", AGORA);
        when(comentarios.comentar(eq(video.id()), eq("Obrigado!"), any())).thenReturn(comentario);

        mvc.perform(post("/api/v1/videos/" + video.id() + "/comentarios")
                        .with(jwt().jwt(t -> t.subject(video.artesaoId().toString()).claim("papel", "artesao")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Obrigado!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dados.autor").value("Mestre Nuca"))
                .andExpect(jsonPath("$.dados.artesao").value(true))
                .andExpect(jsonPath("$.dados.quando").value("14/11/2026 09:00"));
    }
}
