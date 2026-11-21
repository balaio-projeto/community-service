package br.com.balaio.community.conversa.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import br.com.balaio.community.conversa.domain.ParteDaConversa;
import br.com.balaio.community.shared.exception.ServicoIndisponivelException;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

class PedidosDaConversaHttpAdapterTest {
    @Test
    void validaParticipantesPeloContratoInternoSemExporPedido() throws Exception {
        UUID comprador = UUID.randomUUID();
        UUID artesao = UUID.randomUUID();
        AtomicInteger chamadas = new AtomicInteger();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/internal/v1/pedidos", troca -> {
            chamadas.incrementAndGet();
            assertThat(troca.getRequestHeaders().getFirst("X-Balaio-Service-Token")).isEqualTo("token-interno");
            assertThat(troca.getRequestHeaders().getFirst("Authorization")).isNull();
            String corpo = "{\"dados\":{\"compradorId\":\"" + comprador
                    + "\",\"artesaoId\":\"" + artesao + "\"},\"erro\":null}";
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
        try {
            var adapter = new PedidosDaConversaHttpAdapter(
                    "http://127.0.0.1:" + servidor.getAddress().getPort(), "token-interno");
            UUID pedido = UUID.randomUUID();
            assertThat(adapter.parteNoPedido(pedido, new UsuarioAutenticado(comprador, Papel.COMPRADOR, "jwt")))
                    .contains(ParteDaConversa.COMPRADOR);
            assertThat(adapter.parteNoPedido(pedido, new UsuarioAutenticado(artesao, Papel.ARTESAO, "jwt")))
                    .contains(ParteDaConversa.ARTESAO);
            assertThat(adapter.parteNoPedido(pedido,
                    new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "jwt"))).isEmpty();
            assertThat(chamadas.get()).isEqualTo(3);
        } finally {
            servidor.stop(0);
        }
    }

    @Test
    void falhaDeAutenticacaoDoServicoNaoLiberaConversa() throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/internal/v1/pedidos", troca -> {
            troca.sendResponseHeaders(403, -1);
            troca.close();
        });
        servidor.start();
        try {
            var adapter = new PedidosDaConversaHttpAdapter(
                    "http://127.0.0.1:" + servidor.getAddress().getPort(), "errado");
            assertThatThrownBy(() -> adapter.parteNoPedido(UUID.randomUUID(),
                    new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "jwt")))
                    .isInstanceOf(ServicoIndisponivelException.class);
        } finally {
            servidor.stop(0);
        }
    }

    @Test
    void respostaDeParticipantesIncompletaViraIndisponibilidade() throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/internal/v1/pedidos", troca -> {
            byte[] bytes = "{\"dados\":{\"compradorId\":\"invalido\"},\"erro\":null}"
                    .getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
        try {
            var adapter = new PedidosDaConversaHttpAdapter(
                    "http://127.0.0.1:" + servidor.getAddress().getPort(), "token-interno");
            assertThatThrownBy(() -> adapter.parteNoPedido(UUID.randomUUID(),
                    new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "jwt")))
                    .isInstanceOf(ServicoIndisponivelException.class);
        } finally {
            servidor.stop(0);
        }
    }
}
