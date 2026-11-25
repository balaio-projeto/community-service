package br.com.balaio.community.shared.servicos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import br.com.balaio.community.shared.servicos.NotificacoesPort.Notificacao;

class NotificacoesHttpAdapterTest {
    private static final String TOKEN = "t".repeat(32);

    private HttpServer perfil;
    private final AtomicReference<String> tokenRecebido = new AtomicReference<>();
    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger chamadas = new AtomicInteger();

    @BeforeEach
    void iniciar() throws IOException {
        perfil = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        perfil.createContext("/internal/v1/notificacoes", troca -> {
            chamadas.incrementAndGet();
            tokenRecebido.set(troca.getRequestHeaders().getFirst("X-Balaio-Service-Token"));
            corpoRecebido.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resposta = "{\"dados\":{\"criada\":true},\"erro\":null}".getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status.get(), resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        perfil.start();
    }

    @AfterEach
    void parar() {
        perfil.stop(0);
    }

    @Test
    void enviaANotificacaoComOTokenInterno() {
        UUID conta = UUID.randomUUID();

        adaptador(TOKEN).notificar(new Notificacao(conta, "video_suspenso", "Vídeo suspenso", "Motivo: plágio",
                "/dashboard/videos/1", "video:1:suspenso:10"));

        assertThat(tokenRecebido.get()).isEqualTo(TOKEN);
        assertThat(corpoRecebido.get())
                .contains("\"conta\":\"" + conta + "\"")
                .contains("\"tipo\":\"video_suspenso\"")
                .contains("\"titulo\":\"Vídeo suspenso\"")
                .contains("\"mensagem\":\"Motivo: plágio\"")
                .contains("\"link\":\"/dashboard/videos/1\"")
                .contains("\"chave\":\"video:1:suspenso:10\"");
    }

    @Test
    void falhaDoPerfilNaoPropaga() {
        status.set(500);

        assertThatCode(() -> adaptador(TOKEN).notificar(notificacao())).doesNotThrowAnyException();
        assertThat(chamadas.get()).isEqualTo(1);
    }

    @Test
    void semTokenInternoNaoChamaOPerfil() {
        adaptador("").notificar(notificacao());

        assertThat(chamadas.get()).isZero();
    }

    private NotificacoesHttpAdapter adaptador(String token) {
        return new NotificacoesHttpAdapter("http://127.0.0.1:" + perfil.getAddress().getPort(), token);
    }

    private static Notificacao notificacao() {
        return new Notificacao(UUID.randomUUID(), "video_suspenso", "Vídeo suspenso", "Mensagem", "/dashboard/videos/1",
                "video:1:suspenso:10");
    }
}
