package br.com.balaio.community.conversa.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.balaio.community.conversa.application.ConversaService;
import br.com.balaio.community.conversa.domain.Mensagem;
import br.com.balaio.community.conversa.domain.ParteDaConversa;
import br.com.balaio.community.conversa.domain.PedidoDaConversa;
import br.com.balaio.community.conversa.domain.ResumoConversa;
import br.com.balaio.community.shared.config.WebConfig;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.security.SecurityConfig;
import br.com.balaio.community.shared.security.UsuarioAutenticadoArgumentResolver;

@WebMvcTest(ConversasController.class)
@Import({ SecurityConfig.class, WebConfig.class, UsuarioAutenticadoArgumentResolver.class })
class ConversasControllerTest {
    private static final OffsetDateTime MEIO_DIA_UTC = OffsetDateTime.of(2026, 11, 14, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired MockMvc mvc;
    @MockitoBean ConversaService conversas;
    @MockitoBean JwtDecoder decoder;

    @Test
    void conversaExigeLogin() throws Exception {
        mvc.perform(get("/api/v1/pedidos/" + UUID.randomUUID() + "/conversa")).andExpect(status().isUnauthorized());
    }

    @Test
    void mensagemSaiNoFormatoDaTelaComHoraLocal() throws Exception {
        UUID pedido = UUID.randomUUID();
        var mensagem = Mensagem.enviar(pedido, UUID.randomUUID(), ParteDaConversa.COMPRADOR, "Oi", MEIO_DIA_UTC);
        when(conversas.lerConversa(any(), any())).thenReturn(List.of(mensagem));

        mvc.perform(get("/api/v1/pedidos/" + pedido + "/conversa").with(usuario("comprador")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados[0].autor").value("comprador"))
                .andExpect(jsonPath("$.dados[0].hora").value("09:00"));
    }

    @Test
    void terceiroRecebe404SemVerMensagens() throws Exception {
        when(conversas.lerConversa(any(), any()))
                .thenThrow(new RecursoNaoEncontradoException("CONVERSA_NAO_ENCONTRADA", "Conversa não encontrada."));

        mvc.perform(get("/api/v1/pedidos/" + UUID.randomUUID() + "/conversa").with(usuario("artesao")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.dados").isEmpty());
    }

    @Test
    void confirmacaoDeLeituraRetorna204() throws Exception {
        UUID pedido = UUID.randomUUID();

        mvc.perform(post("/api/v1/pedidos/" + pedido + "/conversa/leitura").with(usuario("artesao")))
                .andExpect(status().isNoContent());

        verify(conversas).confirmarLeitura(eq(pedido), any());
    }

    @Test
    void painelDoArtesaoTrazOsCamposDaTela() throws Exception {
        var pedido = new PedidoDaConversa(UUID.randomUUID(), "Ana", "14/11/2026");
        when(conversas.conversasDoArtesao(any()))
                .thenReturn(List.of(new ResumoConversa(pedido, Optional.empty(), false)));

        mvc.perform(get("/api/v1/artesao/conversas").with(usuario("artesao")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados[0].pessoa").value("Ana"))
                .andExpect(jsonPath("$.dados[0].previa").value("Nova compra recebida"))
                .andExpect(jsonPath("$.dados[0].quando").value("14/11/2026"))
                .andExpect(jsonPath("$.dados[0].naoLida").value(false));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .JwtRequestPostProcessor usuario(String papel) {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("papel", papel));
    }
}
