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
import br.com.balaio.community.conversa.domain.PedidoDaConversa;
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
    void artesaoQueComprouConversaComoCompradorEQuemVendeuComoArtesao() throws Exception {
        UUID artesaoComprador = UUID.randomUUID();
        UUID artesaoVendedor = UUID.randomUUID();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/internal/v1/pedidos", troca -> {
            String corpo = "{\"dados\":{\"compradorId\":\"" + artesaoComprador
                    + "\",\"artesaoId\":\"" + artesaoVendedor + "\"},\"erro\":null}";
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
            assertThat(adapter.parteNoPedido(pedido, new UsuarioAutenticado(artesaoComprador, Papel.ARTESAO, "jwt")))
                    .contains(ParteDaConversa.COMPRADOR);
            assertThat(adapter.parteNoPedido(pedido, new UsuarioAutenticado(artesaoVendedor, Papel.ARTESAO, "jwt")))
                    .contains(ParteDaConversa.ARTESAO);
            assertThat(adapter.parteNoPedido(pedido,
                    new UsuarioAutenticado(artesaoComprador, Papel.ORGANIZADOR, "jwt"))).isEmpty();
            assertThat(adapter.parteNoPedido(pedido,
                    new UsuarioAutenticado(artesaoVendedor, Papel.ADMIN, "jwt"))).isEmpty();
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

    @Test
    void vendasTrazemNomeEFotoDoCompradorResolvidosPeloCommerce() throws Exception {
        UUID comPerfil = UUID.randomUUID();
        UUID semPerfil = UUID.randomUUID();
        UUID legado = UUID.randomUUID();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/v1/artesao/vendas", troca -> {
            assertThat(troca.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer jwt-artesao");
            String corpo = "{\"dados\":["
                    + "{\"id\":\"" + comPerfil + "\",\"data\":\"14/11/2026\",\"compradorNome\":\"Ana Souza\","
                    + "\"comprador\":{\"nome\":\"Ana Souza\",\"imagem\":\"/api/v1/arquivos/perfis/ana.webp\"}},"
                    + "{\"id\":\"" + semPerfil + "\",\"data\":\"15/11/2026\",\"compradorNome\":\"Comprador\","
                    + "\"comprador\":{\"nome\":null,\"imagem\":null}},"
                    + "{\"id\":\"" + legado + "\",\"data\":\"16/11/2026\",\"compradorNome\":\"Caio\"}"
                    + "],\"erro\":null}";
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
            assertThat(adapter.vendasDoArtesao(new UsuarioAutenticado(UUID.randomUUID(), Papel.ARTESAO, "jwt-artesao")))
                    .containsExactly(
                            new PedidoDaConversa(comPerfil, "Ana Souza", "14/11/2026", "/api/v1/arquivos/perfis/ana.webp"),
                            new PedidoDaConversa(semPerfil, null, "15/11/2026", null),
                            new PedidoDaConversa(legado, "Caio", "16/11/2026", null));
        } finally {
            servidor.stop(0);
        }
    }
}
