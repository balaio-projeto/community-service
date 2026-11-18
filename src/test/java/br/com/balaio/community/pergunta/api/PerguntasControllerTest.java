package br.com.balaio.community.pergunta.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.pergunta.application.PerguntaService;
import br.com.balaio.community.pergunta.domain.Pergunta;
import br.com.balaio.community.shared.config.WebConfig;
import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.security.SecurityConfig;
import br.com.balaio.community.shared.security.UsuarioAutenticadoArgumentResolver;

@WebMvcTest(PerguntasController.class)
@Import({ SecurityConfig.class, WebConfig.class, UsuarioAutenticadoArgumentResolver.class })
class PerguntasControllerTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 14, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String URL = "/api/v1/pecas/vaso-de-barro/perguntas";

    @Autowired MockMvc mvc;
    @MockitoBean PerguntaService perguntas;
    @MockitoBean JwtDecoder decoder;

    @Test
    void visitanteListaPerguntasNoFormatoDaFakeApi() throws Exception {
        var pergunta = Pergunta.fazer("vaso-de-barro", UUID.randomUUID(), "Ana", "Serve para planta?", AGORA);
        when(perguntas.listarDaPeca("vaso-de-barro", Pagina.primeira())).thenReturn(List.of(pergunta));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados[0].pecaSlug").value("vaso-de-barro"))
                .andExpect(jsonPath("$.dados[0].pergunta").value("Serve para planta?"))
                .andExpect(jsonPath("$.dados[0].autor").value("Ana"))
                .andExpect(jsonPath("$.erro").isEmpty());
    }

    @Test
    void pecaInexistenteRetorna404() throws Exception {
        when(perguntas.listarDaPeca("vaso-de-barro", Pagina.primeira()))
                .thenThrow(new RecursoNaoEncontradoException("PECA_NAO_ENCONTRADA", "Peça não encontrada."));

        mvc.perform(get(URL)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro.codigo").value("PECA_NAO_ENCONTRADA"));
    }

    @Test
    void perguntarSemLoginRetorna401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"Oi?\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro.codigo").value("NAO_AUTENTICADO"));
        verifyNoInteractions(perguntas);
    }

    @Test
    void perguntaCriadaRetorna201() throws Exception {
        var pergunta = Pergunta.fazer("vaso-de-barro", UUID.randomUUID(), "Ana", "Oi?", AGORA);
        when(perguntas.perguntar(eq("vaso-de-barro"), eq("Oi?"), any())).thenReturn(pergunta);

        mvc.perform(post(URL).with(usuario("comprador")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"Oi?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dados.id").value(pergunta.id().toString()));
    }

    @Test
    void respostaDeQuemNaoEhDonoRetorna403() throws Exception {
        UUID id = UUID.randomUUID();
        when(perguntas.responder(eq("vaso-de-barro"), eq(id), eq("Sim."), any()))
                .thenThrow(new AcessoNegadoException("Somente o artesão responsável pode responder."));

        mvc.perform(patch(URL + "/" + id).with(usuario("artesao")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resposta\":\"Sim.\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro.codigo").value("SEM_PERMISSAO"));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .JwtRequestPostProcessor usuario(String papel) {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("papel", papel));
    }
}
