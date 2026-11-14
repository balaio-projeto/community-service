package br.com.balaio.community.shared.servicos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import br.com.balaio.community.shared.servicos.CatalogoPort.PecaCatalogo;
import br.com.balaio.community.shared.exception.ServicoIndisponivelException;

class CatalogoHttpAdapterTest {
    private HttpServer catalogo;
    private final AtomicReference<String> autorizacaoRecebida = new AtomicReference<>();
    private CatalogoHttpAdapter adaptador;

    @BeforeEach
    void iniciar() throws IOException {
        catalogo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        responder("/api/v1/pecas/vaso", 200, "{\"dados\":{\"slug\":\"vaso\",\"artesao\":\"artesao-1\"},\"erro\":null}");
        responder("/api/v1/pecas/sumiu", 404, "{\"dados\":null,\"erro\":{\"codigo\":\"PECA_NAO_ENCONTRADA\"}}");
        responder("/api/v1/pecas/quebrado", 500, "{}");
        catalogo.start();
        adaptador = new CatalogoHttpAdapter("http://127.0.0.1:" + catalogo.getAddress().getPort());
    }

    @AfterEach
    void parar() {
        catalogo.stop(0);
    }

    @Test
    void lePecaERepassaTokenDoVisitante() {
        assertThat(adaptador.buscar("vaso", Optional.of("abc")))
                .contains(new PecaCatalogo("vaso", "artesao-1"));
        assertThat(autorizacaoRecebida.get()).isEqualTo("Bearer abc");
    }

    @Test
    void consultaPublicaNaoEnviaToken() {
        adaptador.buscar("vaso", Optional.empty());

        assertThat(autorizacaoRecebida.get()).isNull();
    }

    @Test
    void pecaInexistenteVoltaVazia() {
        assertThat(adaptador.buscar("sumiu", Optional.empty())).isEmpty();
    }

    @Test
    void erroDoCatalogoViraServicoIndisponivel() {
        assertThatThrownBy(() -> adaptador.buscar("quebrado", Optional.empty()))
                .isInstanceOf(ServicoIndisponivelException.class);
    }

    private void responder(String caminho, int status, String corpo) {
        catalogo.createContext(caminho, troca -> {
            autorizacaoRecebida.set(troca.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
    }
}
