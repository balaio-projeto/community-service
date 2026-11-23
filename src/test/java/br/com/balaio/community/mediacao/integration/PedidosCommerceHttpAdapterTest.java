package br.com.balaio.community.mediacao.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.net.httpserver.HttpServer;

import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort.FalhaNaEntregaException;
import br.com.balaio.community.mediacao.domain.DecisaoMediacao;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.ServicoIndisponivelException;
import br.com.balaio.community.shared.integration.ConsultasCommerceClient;

class PedidosCommerceHttpAdapterTest {
    private static final String TOKEN_INTERNO = "token-interno-com-pelo-menos-32-caracteres";
    private static final String ENVELOPE_VALIDO = "{\"dados\":{\"id\":\"pedido\"},\"erro\":null}";

    private final AtomicReference<String> requisicaoRecebida = new AtomicReference<>();
    private HttpServer commerce;

    @AfterEach
    void encerrar() {
        if (commerce != null) {
            commerce.stop(0);
        }
    }

    @Test
    void decisaoAceitaEnviaTokenInternoEIdentificadorIdempotente() throws IOException {
        UUID pedido = UUID.randomUUID();
        UUID decisao = UUID.randomUUID();
        var adapter = adapterRespondendo(200, ENVELOPE_VALIDO, 0);

        assertThatCode(() -> adapter.aplicarDecisao(pedido, decisao, DecisaoMediacao.NEGAR)).doesNotThrowAnyException();

        assertThat(requisicaoRecebida.get())
                .startsWith("POST /internal/v1/pedidos/" + pedido + "/decisao-mediacao " + TOKEN_INTERNO)
                .contains(decisao.toString()).contains("\"negar\"");
    }

    @ParameterizedTest
    @ValueSource(ints = { 500, 502, 503, 504, 408, 429 })
    void indisponibilidadeDoCommerceAdmiteNovaTentativa(int status) throws IOException {
        var falha = falhaAoEntregar(adapterRespondendo(status, "{\"dados\":null}", 0));

        assertThat(falha.admiteNovaTentativa()).isTrue();
        assertThat(falha.getMessage()).contains("HTTP " + status);
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, 401, 403, 404, 409, 422 })
    void recusaDoCommerceEhDefinitiva(int status) throws IOException {
        var falha = falhaAoEntregar(adapterRespondendo(status, "{\"dados\":null}", 0));

        assertThat(falha.admiteNovaTentativa()).isFalse();
        assertThat(falha.getMessage()).contains("HTTP " + status);
    }

    @Test
    void respostaDeSucessoSemDadosEhViolacaoDeContratoEnaoConsomeRetentativas() throws IOException {
        var falha = falhaAoEntregar(adapterRespondendo(200, "{\"dados\":null,\"erro\":null}", 0));

        assertThat(falha.admiteNovaTentativa()).isFalse();
        assertThat(falha.getMessage()).contains("contrato");
    }

    @Test
    void jsonMalformadoEhViolacaoDeContrato() throws IOException {
        var falha = falhaAoEntregar(adapterRespondendo(200, "{\"dados\": [", 0));

        assertThat(falha.admiteNovaTentativa()).isFalse();
    }

    @Test
    void timeoutDeLeituraAdmiteNovaTentativa() throws IOException {
        var falha = falhaAoEntregar(adapterRespondendo(200, ENVELOPE_VALIDO, 3_500));

        assertThat(falha.admiteNovaTentativa()).as("%s / %s", falha.getMessage(), falha.getCause()).isTrue();
        assertThat(falha.getMessage()).contains("inacessível");
    }

    @Test
    void conexaoRecusadaAdmiteNovaTentativa() throws IOException {
        int portaFechada;
        try (var socket = new ServerSocket(0)) {
            portaFechada = socket.getLocalPort();
        }
        var adapter = adapterPara("http://127.0.0.1:" + portaFechada);

        assertThat(falhaAoEntregar(adapter).admiteNovaTentativa()).isTrue();
    }

    @Test
    void confirmacaoDoCompradorDevolveOArtesaoDoPedido() throws IOException {
        UUID pedido = UUID.randomUUID();
        UUID artesao = UUID.randomUUID();
        var adapter = adapterRespondendo(200,
                "{\"dados\":{\"id\":\"" + pedido + "\",\"artesaoId\":\"" + artesao + "\",\"estado\":\"producao\"},\"erro\":null}", 0);

        assertThat(adapter.confirmarCompradorDoPedido(pedido, "token-comprador")).isEqualTo(artesao);
        assertThat(requisicaoRecebida.get()).startsWith("GET /api/v1/pedidos/" + pedido + " ");
    }

    @Test
    void pedidoSemPagamentoConfirmadoNaoAceitaMediacao() throws IOException {
        UUID pedido = UUID.randomUUID();
        var adapter = adapterRespondendo(200,
                "{\"dados\":{\"id\":\"" + pedido + "\",\"artesaoId\":\"" + UUID.randomUUID()
                        + "\",\"estado\":\"falha_pagamento\"},\"erro\":null}", 0);

        var recusa = catchThrowableOfType(ConflitoException.class,
                () -> adapter.confirmarCompradorDoPedido(pedido, "token-comprador"));
        assertThat(recusa.codigo()).isEqualTo("PEDIDO_SEM_MEDIACAO");
    }

    @Test
    void vendasDoArtesaoViramIdsDePedido() throws IOException {
        UUID primeiro = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        var adapter = adapterRespondendo(200,
                "{\"dados\":[{\"id\":\"" + primeiro + "\"},{\"id\":\"" + segundo + "\"}],\"erro\":null}", 0);

        assertThat(adapter.pedidosVendidosPeloArtesao("token-artesao")).containsExactly(primeiro, segundo);
        assertThat(requisicaoRecebida.get()).startsWith("GET /api/v1/artesao/vendas ");
    }

    @Test
    void vendaSemIdentificadorValidoEhCommerceIndisponivel() throws IOException {
        var adapter = adapterRespondendo(200, "{\"dados\":[{\"id\":\"nao-e-uuid\"}],\"erro\":null}", 0);

        assertThat(catchThrowableOfType(ServicoIndisponivelException.class,
                () -> adapter.pedidosVendidosPeloArtesao("token-artesao")).codigo()).isEqualTo("COMMERCE_INDISPONIVEL");
    }

    private static FalhaNaEntregaException falhaAoEntregar(PedidosCommerceHttpAdapter adapter) {
        return catchThrowableOfType(FalhaNaEntregaException.class,
                () -> adapter.aplicarDecisao(UUID.randomUUID(), UUID.randomUUID(), DecisaoMediacao.NEGAR));
    }

    private PedidosCommerceHttpAdapter adapterRespondendo(int status, String corpo, long atrasoMs)
            throws IOException {
        commerce = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        commerce.createContext("/", troca -> {
            String corpoRecebido = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requisicaoRecebida.set(troca.getRequestMethod() + " " + troca.getRequestURI().getRawPath() + " "
                    + troca.getRequestHeaders().getFirst("X-Balaio-Service-Token") + " " + corpoRecebido);
            esperar(atrasoMs);
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().set("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length);
            try (var saida = troca.getResponseBody()) {
                saida.write(bytes);
            } catch (IOException clienteDesistiu) {
                troca.close();
            }
        });
        commerce.start();
        return adapterPara("http://127.0.0.1:" + commerce.getAddress().getPort());
    }

    private static PedidosCommerceHttpAdapter adapterPara(String url) {
        return new PedidosCommerceHttpAdapter(url, TOKEN_INTERNO, new ConsultasCommerceClient(url));
    }

    private static void esperar(long milissegundos) {
        if (milissegundos <= 0) {
            return;
        }
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
        }
    }
}
