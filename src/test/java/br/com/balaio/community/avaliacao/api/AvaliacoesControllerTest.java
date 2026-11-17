package br.com.balaio.community.avaliacao.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import br.com.balaio.community.avaliacao.application.AvaliacaoService;
import br.com.balaio.community.avaliacao.domain.Avaliacao;
import br.com.balaio.community.avaliacao.domain.Reputacao;
import br.com.balaio.community.shared.config.WebConfig;
import br.com.balaio.community.shared.security.SecurityConfig;
import br.com.balaio.community.shared.security.UsuarioAutenticadoArgumentResolver;

@WebMvcTest(AvaliacoesController.class)
@Import({ SecurityConfig.class, WebConfig.class, UsuarioAutenticadoArgumentResolver.class })
class AvaliacoesControllerTest {
    private final UUID pedido = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockitoBean AvaliacaoService avaliacoes;
    @MockitoBean JwtDecoder decoder;

    @Test
    void avaliarExigeLogin() throws Exception {
        mvc.perform(post("/api/v1/pedidos/" + pedido + "/avaliacao").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void avaliacaoCriadaRetorna201ComAspectos() throws Exception {
        var avaliacao = new Avaliacao(UUID.randomUUID(), pedido, UUID.randomUUID(), UUID.randomUUID(), "vaso", 5,
                "Ótima", List.of("Prazo"), OffsetDateTime.of(2026, 11, 14, 12, 0, 0, 0, ZoneOffset.UTC));
        when(avaliacoes.avaliar(eq(pedido), any(), any())).thenReturn(avaliacao);

        mvc.perform(post("/api/v1/pedidos/" + pedido + "/avaliacao")
                        .with(jwt().jwt(t -> t.subject(UUID.randomUUID().toString()).claim("papel", "comprador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":5,\"comentario\":\"Ótima\",\"aspectos\":[\"Prazo\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dados.nota").value(5))
                .andExpect(jsonPath("$.dados.aspectos[0]").value("Prazo"));
    }

    @Test
    void reputacaoEhPublica() throws Exception {
        when(avaliacoes.reputacaoDoArtesao("artesao-1")).thenReturn(Reputacao.das(List.of(4, 5)));

        mvc.perform(get("/api/v1/artesaos/artesao-1/reputacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.media").value(4.5))
                .andExpect(jsonPath("$.dados.total").value(2))
                .andExpect(jsonPath("$.dados.distribuicao['5']").value(1));
    }

    @Test
    void rotaSemRegraExplicitaExigeAutenticacao() throws Exception {
        mvc.perform(get("/api/v1/rota-sem-regra"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro.codigo").value("NAO_AUTENTICADO"));
    }
}
