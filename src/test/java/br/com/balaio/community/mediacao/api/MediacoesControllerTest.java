package br.com.balaio.community.mediacao.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.SecurityConfig;
import br.com.balaio.community.shared.security.UsuarioAutenticado;
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
    void organizadorEAdminNaoAbremMediacao() throws Exception {
        for (String papel : List.of("organizador", "admin")) {
            mvc.perform(post(URL).with(usuario(papel)).contentType(MediaType.APPLICATION_JSON)
                            .content(corpoAbertura(UUID.randomUUID())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.erro.codigo").value("SEM_PERMISSAO"));
        }
        verifyNoInteractions(mediacoes);
    }

    @Test
    void artesaoAbreMediacaoDaPropriaCompra() throws Exception {
        UUID artesao = UUID.randomUUID();
        UUID pedido = UUID.randomUUID();
        when(mediacoes.abrir(any())).thenReturn(mediacao(pedido, artesao));

        mvc.perform(post(URL).with(usuario("artesao", artesao)).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAbertura(pedido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dados.compradorId").value(artesao.toString()));
        verify(mediacoes).abrir(eq(new AberturaMediacao(pedido, artesao, "Peça quebrada", "Chegou trincada.",
                "token")));
    }

    @Test
    void artesaoListaComprasEVendasNumaSoLista() throws Exception {
        UUID artesao = UUID.randomUUID();
        var daCompra = mediacao(UUID.randomUUID(), artesao);
        var daVenda = mediacao(UUID.randomUUID(), UUID.randomUUID());
        when(mediacoes.listarDoArtesao(any(), eq(Pagina.primeira()))).thenReturn(List.of(daCompra, daVenda));

        mvc.perform(get(URL).with(usuario("artesao", artesao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.length()").value(2))
                .andExpect(jsonPath("$.dados[0].compradorId").value(artesao.toString()))
                .andExpect(jsonPath("$.dados[1].pedido").value(daVenda.pedidoId().toString()));
        verify(mediacoes, never()).listarDoComprador(any(), any());
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
        when(mediacoes.listarDoComprador(comprador, Pagina.primeira()))
                .thenReturn(List.of(mediacao(UUID.randomUUID(), comprador)));

        mvc.perform(get(URL).with(usuario("comprador", comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.length()").value(1));
        verify(mediacoes).listarDoComprador(comprador, Pagina.primeira());
    }

    @Test
    void administradorListaPaginadoEVeOMotivoDaFalhaDeEntrega() throws Exception {
        var falhou = mediacaoComEntregaRecusada(UUID.randomUUID());
        when(mediacoes.listarTodas(Pagina.de(2, 10))).thenReturn(List.of(falhou));

        mvc.perform(get(URL).param("pagina", "2").param("tamanho", "10").with(usuario("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados[0].estado").value("falha_entrega"))
                .andExpect(jsonPath("$.dados[0].ultimoErro").value("commerce recusou a decisão com HTTP 409"));
    }

    @Test
    void compradorNaoVeDetalheTecnicoDaFalha() throws Exception {
        UUID comprador = UUID.randomUUID();
        when(mediacoes.listarDoComprador(comprador, Pagina.primeira()))
                .thenReturn(List.of(mediacaoComEntregaRecusada(comprador)));

        mvc.perform(get(URL).with(usuario("comprador", comprador)))
                .andExpect(jsonPath("$.dados[0].estado").value("falha_entrega"))
                .andExpect(jsonPath("$.dados[0].ultimoErro").isEmpty());
    }

    @Test
    void tamanhoDePaginaAcimaDoLimiteRetorna400() throws Exception {
        mvc.perform(get(URL).param("tamanho", "1000").with(usuario("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro.codigo").value("REQUISICAO_INVALIDA"));
    }

    @Test
    void somenteAdministradorReprocessaEntrega() throws Exception {
        mvc.perform(post(URL + "/" + UUID.randomUUID() + "/reprocessar-entrega").with(usuario("comprador")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(entregas);
    }

    @Test
    void reprocessamentoConfirmadoRetorna200() throws Exception {
        var mediacao = mediacao(UUID.randomUUID(), UUID.randomUUID());
        mediacao.registrarDecisao(DecisaoMediacao.NEGAR, CRIADA_EM);
        mediacao.confirmarEntregaDaDecisao(CRIADA_EM);
        when(entregas.reprocessar(mediacao.id())).thenReturn(mediacao);

        mvc.perform(post(URL + "/" + mediacao.id() + "/reprocessar-entrega").with(usuario("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.estado").value("decidida"));
    }

    @Test
    void artesaoListaAsDosPedidosDeleSemDetalheTecnicoDaFalha() throws Exception {
        UUID artesao = UUID.randomUUID();
        var falhou = mediacaoComEntregaRecusada(UUID.randomUUID());
        when(mediacoes.listarDoArtesao(any(), eq(Pagina.primeira()))).thenReturn(List.of(falhou));

        mvc.perform(get(URL).with(usuario("artesao", artesao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.length()").value(1))
                .andExpect(jsonPath("$.dados[0].pedido").value(falhou.pedidoId().toString()))
                .andExpect(jsonPath("$.dados[0].decisao").value("negar"))
                .andExpect(jsonPath("$.dados[0].ultimoErro").isEmpty());
        verify(mediacoes).listarDoArtesao(eq(new UsuarioAutenticado(artesao, Papel.ARTESAO, "token")),
                eq(Pagina.primeira()));
        verify(mediacoes, never()).listarTodas(any());
    }

    @Test
    void organizadorNaoListaMediacoes() throws Exception {
        mvc.perform(get(URL).with(usuario("organizador")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro.codigo").value("SEM_PERMISSAO"));
        verifyNoInteractions(mediacoes);
    }

    @Test
    void artesaoNaoAnalisaNemDecide() throws Exception {
        mvc.perform(patch(URL + "/" + UUID.randomUUID()).with(usuario("artesao"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"emAnalise\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(URL + "/" + UUID.randomUUID() + "/decisao").with(usuario("artesao"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decisao\":\"negar\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mediacoes, entregas);
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
    void compradorDesisteDaMediacaoERecebeEstadoCancelado() throws Exception {
        UUID comprador = UUID.randomUUID();
        var mediacao = mediacao(UUID.randomUUID(), comprador);
        mediacao.cancelar(comprador, CRIADA_EM);
        when(mediacoes.cancelar(eq(mediacao.id()), any())).thenReturn(mediacao);

        mvc.perform(post(URL + "/" + mediacao.id() + "/cancelamento").with(usuario("comprador", comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.estado").value("cancelada"))
                .andExpect(jsonPath("$.dados.cancelada").value(true))
                .andExpect(jsonPath("$.dados.emAnalise").value(false))
                .andExpect(jsonPath("$.dados.canceladaEm").value("14/11/2026"))
                .andExpect(jsonPath("$.erro").isEmpty());
        verify(mediacoes).cancelar(mediacao.id(), new UsuarioAutenticado(comprador, Papel.COMPRADOR, "token"));
        verifyNoInteractions(entregas);
    }

    @Test
    void administradorEncerraSemDecisao() throws Exception {
        UUID admin = UUID.randomUUID();
        var mediacao = mediacao(UUID.randomUUID(), UUID.randomUUID());
        mediacao.iniciarAnalise(CRIADA_EM);
        mediacao.cancelar(admin, CRIADA_EM);
        when(mediacoes.cancelar(eq(mediacao.id()), any())).thenReturn(mediacao);

        mvc.perform(post(URL + "/" + mediacao.id() + "/cancelamento").with(usuario("admin", admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.estado").value("cancelada"))
                .andExpect(jsonPath("$.dados.decisao").isEmpty());
        verify(mediacoes).cancelar(mediacao.id(), new UsuarioAutenticado(admin, Papel.ADMIN, "token"));
    }

    @Test
    void cancelamentoDeMediacaoDeOutroCompradorRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(mediacoes.cancelar(eq(id), any())).thenThrow(new RecursoNaoEncontradoException(
                "MEDIACAO_NAO_ENCONTRADA", "Mediação não encontrada."));

        mvc.perform(post(URL + "/" + id + "/cancelamento").with(usuario("comprador")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro.codigo").value("MEDIACAO_NAO_ENCONTRADA"));
    }

    @Test
    void cancelamentoForaDeAbertaOuEmAnaliseRetorna409() throws Exception {
        UUID id = UUID.randomUUID();
        when(mediacoes.cancelar(eq(id), any())).thenThrow(new ConflitoException("MEDIACAO_CONFLITANTE",
                "Só é possível cancelar uma mediação aberta ou em análise."));

        mvc.perform(post(URL + "/" + id + "/cancelamento").with(usuario("admin")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro.codigo").value("MEDIACAO_CONFLITANTE"));
    }

    @Test
    void artesaoNaoCancelaMediacaoAlheia() throws Exception {
        UUID id = UUID.randomUUID();
        when(mediacoes.cancelar(eq(id), any())).thenThrow(new AcessoNegadoException());

        mvc.perform(post(URL + "/" + id + "/cancelamento").with(usuario("artesao")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void cancelamentoSemTokenRetorna401() throws Exception {
        mvc.perform(post(URL + "/" + UUID.randomUUID() + "/cancelamento"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(mediacoes);
    }

    @Test
    void mediacaoDecididaContinuaMarcadaComoEmAnalise() throws Exception {
        var mediacao = mediacao(UUID.randomUUID(), UUID.randomUUID());
        mediacao.registrarDecisao(DecisaoMediacao.NEGAR, CRIADA_EM);
        mediacao.confirmarEntregaDaDecisao(CRIADA_EM);
        when(mediacoes.listarTodas(Pagina.primeira())).thenReturn(List.of(mediacao));

        mvc.perform(get(URL).with(usuario("admin")))
                .andExpect(jsonPath("$.dados[0].emAnalise").value(true))
                .andExpect(jsonPath("$.dados[0].cancelada").value(false))
                .andExpect(jsonPath("$.dados[0].canceladaEm").isEmpty());
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

    private static Mediacao mediacaoComEntregaRecusada(UUID comprador) {
        var mediacao = mediacao(UUID.randomUUID(), comprador);
        mediacao.registrarDecisao(DecisaoMediacao.NEGAR, CRIADA_EM);
        mediacao.registrarFalhaDefinitivaNaEntrega("commerce recusou a decisão com HTTP 409", CRIADA_EM);
        return mediacao;
    }

    private static Mediacao mediacao(UUID pedido, UUID comprador) {
        return Mediacao.abrir(pedido, comprador, "Peça quebrada", "Chegou trincada.", CRIADA_EM);
    }
}
