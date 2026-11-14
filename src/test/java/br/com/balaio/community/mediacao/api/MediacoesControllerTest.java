package br.com.balaio.community.mediacao.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.balaio.community.mediacao.application.AberturaMediacao;
import br.com.balaio.community.mediacao.application.EntregaDecisaoService;
import br.com.balaio.community.mediacao.application.MediacaoService;
import br.com.balaio.community.mediacao.domain.DecisaoMediacao;
import br.com.balaio.community.mediacao.domain.Mediacao;
import br.com.balaio.community.shared.config.WebConfig;
import br.com.balaio.community.shared.security.SecurityConfig;
import br.com.balaio.community.shared.security.UsuarioAutenticadoArgumentResolver;

@WebMvcTest(MediacoesController.class)
@Import({ SecurityConfig.class, WebConfig.class, UsuarioAutenticadoArgumentResolver.class })
class MediacoesControllerTest {
    private static final OffsetDateTime CRIADA_EM = OffsetDateTime.of(2026, 11, 14, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String URL = "/api/v1/admin/mediacoes";

    @Autowired MockMvc mvc;
    @MockitoBean MediacaoService mediacoes;
    @MockitoBean EntregaDecisaoService entregas;
    @MockitoBean JwtDecoder decoder;

    @Test
    void semTokenRetorna401NoEnvelopePadrao() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.dados").isEmpty())
                .andExpect(jsonPath("$.erro.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void artesaoNaoAbreMediacao() throws Exception {
        mvc.perform(post(URL).with(usuario("artesao")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAbertura(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro.codigo").value("SEM_PERMISSAO"));
        verifyNoInteractions(mediacoes);
    }

    @Test
    void compradorAbreMediacaoComIdentidadeDoToken() throws Exception {
        UUID comprador = UUID.randomUUID();
        UUID pedido = UUID.randomUUID();
        when(mediacoes.abrir(any())).thenReturn(mediacao(pedido, comprador));

        mvc.perform(post(URL).with(usuario("comprador", comprador)).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAbertura(pedido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dados.pedido").value(pedido.toString()))
                .andExpect(jsonPath("$.dados.estado").value("aberta"))
                .andExpect(jsonPath("$.dados.emAnalise").value(false))
                .andExpect(jsonPath("$.dados.aberta").value("14/11/2026"))
                .andExpect(jsonPath("$.erro").isEmpty());
        verify(mediacoes).abrir(eq(new AberturaMediacao(pedido, comprador, "Peça quebrada", "Chegou trincada.",
                "token")));
    }

    @Test
    void compradorListaSomenteAsProprias() throws Exception {
        UUID comprador = UUID.randomUUID();
        when(mediacoes.listarDoComprador(comprador)).thenReturn(List.of(mediacao(UUID.randomUUID(), comprador)));

        mvc.perform(get(URL).with(usuario("comprador", comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.length()").value(1));
        verify(mediacoes).listarDoComprador(comprador);
    }

    @Test
    void artesaoNaoListaMediacoes() throws Exception {
        mvc.perform(get(URL).with(usuario("artesao"))).andExpect(status().isForbidden());
    }

    @Test
    void analiseExigeEstadoEmAnalise() throws Exception {
        mvc.perform(patch(URL + "/" + UUID.randomUUID()).with(usuario("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"decidida\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro.codigo").value("REQUISICAO_INVALIDA"));
    }

    @Test
    void decisaoAindaNaoConfirmadaPeloCommerceRetorna202() throws Exception {
        var mediacao = mediacao(UUID.randomUUID(), UUID.randomUUID());
        mediacao.registrarDecisao(DecisaoMediacao.NEGAR, CRIADA_EM);
        when(entregas.entregar(mediacao.id())).thenReturn(mediacao);

        mvc.perform(post(URL + "/" + mediacao.id() + "/decisao").with(usuario("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decisao\":\"negar\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.dados.estado").value("decisao_pendente"))
                .andExpect(jsonPath("$.dados.decisao").value("negar"));
    }

    @Test
    void decisaoDesconhecidaRetorna400() throws Exception {
        mvc.perform(post(URL + "/" + UUID.randomUUID() + "/decisao").with(usuario("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decisao\":\"apagar\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mediacoes);
    }

    @Test
    void idMalFormadoRetorna400() throws Exception {
        mvc.perform(patch(URL + "/nao-e-uuid").with(usuario("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"emAnalise\"}"))
                .andExpect(status().isBadRequest());
    }

    private static JwtRequestPostProcessor usuario(String papel) {
        return usuario(papel, UUID.randomUUID());
    }

    private static JwtRequestPostProcessor usuario(String papel, UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()).claim("papel", papel).tokenValue("token"));
    }

    private static String corpoAbertura(UUID pedido) {
        return "{\"pedido\":\"" + pedido + "\",\"assunto\":\"Peça quebrada\",\"relato\":\"Chegou trincada.\"}";
    }

    private static Mediacao mediacao(UUID pedido, UUID comprador) {
        return Mediacao.abrir(pedido, comprador, "Peça quebrada", "Chegou trincada.", CRIADA_EM);
    }
}
