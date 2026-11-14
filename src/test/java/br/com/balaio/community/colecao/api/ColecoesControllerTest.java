package br.com.balaio.community.colecao.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import br.com.balaio.community.colecao.application.ColecaoService;
import br.com.balaio.community.colecao.domain.TipoColecao;
import br.com.balaio.community.shared.config.WebConfig;
import br.com.balaio.community.shared.security.SecurityConfig;
import br.com.balaio.community.shared.security.UsuarioAutenticadoArgumentResolver;

@WebMvcTest(ColecoesController.class)
@Import({ SecurityConfig.class, WebConfig.class, UsuarioAutenticadoArgumentResolver.class })
class ColecoesControllerTest {
    private final UUID usuario = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockitoBean ColecaoService colecoes;
    @MockitoBean JwtDecoder decoder;

    @Test
    void exigeLogin() throws Exception {
        mvc.perform(get("/api/v1/estado/favoritos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void leFavoritosDoUsuarioDoToken() throws Exception {
        when(colecoes.consultar(usuario, TipoColecao.FAVORITOS)).thenReturn(List.of("cesto"));

        mvc.perform(get("/api/v1/estado/favoritos").with(logado()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados[0]").value("cesto"));
    }

    @Test
    void gravaNoMesmoFormatoDaFakeApi() throws Exception {
        when(colecoes.substituir(usuario, TipoColecao.VIDEOS_SALVOS, List.of("v1", "v1")))
                .thenReturn(List.of("v1"));

        mvc.perform(put("/api/v1/estado/videosSalvos").with(logado()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videosSalvos\":[\"v1\",\"v1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.length()").value(1));
        verify(colecoes).substituir(usuario, TipoColecao.VIDEOS_SALVOS, List.of("v1", "v1"));
    }

    @Test
    void corpoSemACampoDaColecaoEhInvalido() throws Exception {
        mvc.perform(put("/api/v1/estado/favoritos").with(logado()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outra\":[\"cesto\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro.mensagem").value("Estado inválido."));
        verifyNoInteractions(colecoes);
    }

    @Test
    void sacolaNaoPertenceAoCommunity() throws Exception {
        mvc.perform(get("/api/v1/estado/sacola").with(logado())).andExpect(status().isNotFound());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .JwtRequestPostProcessor logado() {
        return jwt().jwt(token -> token.subject(usuario.toString()).claim("papel", "comprador"));
    }
}
