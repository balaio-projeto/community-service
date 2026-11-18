package br.com.balaio.community.shared.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

import com.sun.net.httpserver.HttpServer;

class ConsultasCommerceClientTest {
    private final AtomicReference<String> requisicaoRecebida = new AtomicReference<>();
    private HttpServer commerce;

    @AfterEach
    void encerrar() {
        if (commerce != null) {
            commerce.stop(0);
        }
    }

    @Test
    void consultaOPedidoComOTokenDoCompradorEDevolveOsDados() throws IOException {
        UUID pedidoId = UUID.randomUUID();
        var cliente = clienteRespondendo(200, "{\"dados\":{\"id\":\"" + pedidoId + "\",\"artesaoId\":\"artesao-1\"},\"erro\":null}");

        var pedido = cliente.consultarPedido(pedidoId, "token-comprador");

        assertThat(pedido.path("artesaoId").asString()).isEqualTo("artesao-1");
        assertThat(requisicaoRecebida.get()).isEqualTo("GET /api/v1/pedidos/" + pedidoId + " Bearer token-comprador");
    }

    @Test
    void pedidoInexistentePropagaONotFoundParaOAdapterTraduzir() throws IOException {
        var cliente = clienteRespondendo(404, "{\"dados\":null,\"erro\":{\"codigo\":\"PEDIDO_NAO_ENCONTRADO\"}}");

        assertThatThrownBy(() -> cliente.consultarPedido(UUID.randomUUID(), "token-comprador"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void respostaSemDadosEhTratadaComoCommerceIndisponivel() throws IOException {
        var cliente = clienteRespondendo(200, "{\"dados\":null,\"erro\":null}");

        assertThatThrownBy(() -> cliente.consultarPedido(UUID.randomUUID(), "token-comprador"))
                .isInstanceOf(RespostaIncompativelException.class);
    }

    private ConsultasCommerceClient clienteRespondendo(int status, String corpo) throws IOException {
        commerce = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        commerce.createContext("/", troca -> {
            requisicaoRecebida.set(troca.getRequestMethod() + " " + troca.getRequestURI().getRawPath() + " "
                    + troca.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().set("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length);
            try (var saida = troca.getResponseBody()) {
                saida.write(bytes);
            }
        });
        commerce.start();
        return new ConsultasCommerceClient("http://127.0.0.1:" + commerce.getAddress().getPort());
    }
}
