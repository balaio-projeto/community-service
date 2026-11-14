package br.com.balaio.community.video.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.balaio.community.shared.config.WebConfig;
import br.com.balaio.community.shared.security.SecurityConfig;
import br.com.balaio.community.shared.security.UsuarioAutenticadoArgumentResolver;
import br.com.balaio.community.video.application.ComentarioVideoService;
import br.com.balaio.community.video.application.VideoService;
import br.com.balaio.community.video.application.VideoService.VideoComNumeros;
import br.com.balaio.community.video.domain.Video;

@WebMvcTest(VideosController.class)
@Import({ SecurityConfig.class, WebConfig.class, UsuarioAutenticadoArgumentResolver.class })
class VideosControllerTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 14, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired MockMvc mvc;
    @MockitoBean VideoService videos;
    @MockitoBean ComentarioVideoService comentarios;
    @MockitoBean JwtDecoder decoder;

    @Test
    void visitanteListaVideosNoFormatoDaTela() throws Exception {
        UUID artesao = UUID.randomUUID();
        var video = Video.rascunho(artesao, "vaso", "Asa do vaso", List.of("ceramica"), "0:48", AGORA);
        when(videos.listar(eq(false), eq(Optional.empty()))).thenReturn(List.of(new VideoComNumeros(video, 3, 2)));

        mvc.perform(get("/api/v1/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados[0].artesao").value("artesao-" + artesao))
                .andExpect(jsonPath("$.dados[0].curtidas").value(3))
                .andExpect(jsonPath("$.dados[0].comentarios").value(2))
                .andExpect(jsonPath("$.dados[0].publicadoEm").value("Rascunho"))
                .andExpect(jsonPath("$.dados[0].url").isEmpty());
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
