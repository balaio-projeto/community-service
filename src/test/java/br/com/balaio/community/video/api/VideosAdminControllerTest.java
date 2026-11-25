package br.com.balaio.community.video.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.balaio.community.shared.config.WebConfig;
import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.SecurityConfig;
import br.com.balaio.community.shared.security.UsuarioAutenticadoArgumentResolver;
import br.com.balaio.community.video.application.ModeracaoVideoService;
import br.com.balaio.community.video.domain.ArquivoMidia;
import br.com.balaio.community.video.domain.FiltroModeracaoVideo;
import br.com.balaio.community.video.domain.SituacaoVideo;
import br.com.balaio.community.video.domain.TipoMidia;
import br.com.balaio.community.video.domain.Video;

@WebMvcTest(VideosAdminController.class)
@Import({ SecurityConfig.class, WebConfig.class, UsuarioAutenticadoArgumentResolver.class })
class VideosAdminControllerTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 25, 15, 30, 0, 0, ZoneOffset.UTC);
    private static final String MOTIVO = "Vídeo usa conteúdo de terceiros sem autorização.";

    @Autowired MockMvc mvc;
    @MockitoBean ModeracaoVideoService moderacao;
    @MockitoBean JwtDecoder decoder;

    private final UUID administrador = UUID.randomUUID();

    @Test
    void listaComEstadoEPaginaPedidos() throws Exception {
        var suspenso = suspenso();
        when(moderacao.listar(eq(FiltroModeracaoVideo.SUSPENSOS), eq(Pagina.de(1, 20)), any()))
                .thenReturn(List.of(suspenso));

        mvc.perform(get("/api/v1/admin/videos").param("estado", "suspensa").param("pagina", "1")
                        .param("tamanho", "20").with(papel("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados[0].id").value(suspenso.id().toString()))
                .andExpect(jsonPath("$.dados[0].artesao").value("artesao-" + suspenso.artesaoId()))
                .andExpect(jsonPath("$.dados[0].estado").value("suspensa"))
                .andExpect(jsonPath("$.dados[0].situacao").value("publicada"))
                .andExpect(jsonPath("$.dados[0].url").value("/api/v1/videos/" + suspenso.id() + "/arquivo"))
                .andExpect(jsonPath("$.dados[0].suspensao.motivo").value(MOTIVO))
                .andExpect(jsonPath("$.dados[0].suspensao.suspensoEm").value("2026-11-25T15:30Z"))
                .andExpect(jsonPath("$.dados[0].suspensoPor").value(administrador.toString()));
    }

    @Test
    void estadoDesconhecidoRetorna400() throws Exception {
        mvc.perform(get("/api/v1/admin/videos").param("estado", "rascunho").with(papel("admin")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(moderacao);
    }

    @Test
    void suspendeComOMotivoDoCorpo() throws Exception {
        var suspenso = suspenso();
        when(moderacao.suspender(eq(suspenso.id()), eq(MOTIVO), any())).thenReturn(suspenso);

        mvc.perform(post("/api/v1/admin/videos/" + suspenso.id() + "/suspensao").with(papel("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + MOTIVO + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.estado").value("suspensa"));
    }

    @Test
    void suspensaoSemCorpoChegaAoServicoSemMotivo() throws Exception {
        UUID id = UUID.randomUUID();
        when(moderacao.suspender(eq(id), isNull(), any()))
                .thenThrow(new RequisicaoInvalidaException("Explique o motivo da suspensão com 10 a 1000 caracteres."));

        mvc.perform(post("/api/v1/admin/videos/" + id + "/suspensao").with(papel("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro.mensagem").value("Explique o motivo da suspensão com 10 a 1000 caracteres."));
    }

    @Test
    void reativacaoDeVideoNaoSuspensoRetorna409() throws Exception {
        UUID id = UUID.randomUUID();
        when(moderacao.reativar(eq(id), any()))
                .thenThrow(new ConflitoException("VIDEO_NAO_SUSPENSO", "Este vídeo não está suspenso."));

        mvc.perform(post("/api/v1/admin/videos/" + id + "/reativacao").with(papel("admin")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro.codigo").value("VIDEO_NAO_SUSPENSO"));
    }

    @Test
    void reativaEDevolveOEstadoPublicado() throws Exception {
        var video = suspenso();
        video.reativar();
        when(moderacao.reativar(eq(video.id()), any())).thenReturn(video);

        mvc.perform(post("/api/v1/admin/videos/" + video.id() + "/reativacao").with(papel("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.estado").value("publicada"))
                .andExpect(jsonPath("$.dados.suspensao").isEmpty());
        verify(moderacao).reativar(eq(video.id()), any());
    }

    @Test
    void naoAdministradorRecebe403() throws Exception {
        UUID id = UUID.randomUUID();
        when(moderacao.suspender(eq(id), any(), any())).thenThrow(new AcessoNegadoException());

        mvc.perform(post("/api/v1/admin/videos/" + id + "/suspensao").with(papel("artesao"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + MOTIVO + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/v1/admin/videos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/videos/" + UUID.randomUUID() + "/reativacao"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(moderacao);
    }

    private RequestPostProcessor papel(String papel) {
        return jwt().jwt(token -> token.subject(administrador.toString()).claim("papel", papel));
    }

    private Video suspenso() {
        var video = Video.rascunho(UUID.randomUUID(), null, "Asa do vaso", List.of("ceramica"), "0:48", AGORA);
        video.anexarArquivo(ArquivoMidia.novo(video.id(), TipoMidia.VIDEO, "video/mp4", 10), null);
        video.mudarSituacao(SituacaoVideo.PUBLICADA, AGORA);
        video.suspender(MOTIVO, administrador, AGORA);
        return video;
    }
}
