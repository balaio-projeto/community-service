package br.com.balaio.community.video.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import software.amazon.awssdk.services.s3.S3Client;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

@EnabledIfEnvironmentVariable(named = "COMMUNITY_S3_TEST", matches = "true")
class ArmazenamentoS3AdapterTest {
    private static final String BUCKET = "midia-teste";
    private static final int PORTA_S3 = 8333;
    private static final String CHAVE_ACESSO = "teste";
    private static final String CHAVE_SECRETA = "teste-segredo";
    private static final String IDENTIDADES = """
            {"identities":[{"name":"teste","credentials":[{"accessKey":"teste","secretKey":"teste-segredo"}],
            "actions":["Admin","Read","Write","List","Tagging"]}]}
            """;
    private static GenericContainer<?> seaweedfs;
    private static ArmazenamentoS3Adapter armazenamento;

    @BeforeAll
    static void iniciar() {
        seaweedfs = new GenericContainer<>("chrislusf/seaweedfs:4.45")
                .withCopyToContainer(Transferable.of(IDENTIDADES), "/etc/seaweedfs/s3.json")
                .withCommand("server", "-s3", "-s3.config=/etc/seaweedfs/s3.json")
                .withExposedPorts(PORTA_S3)
                .waitingFor(Wait.forListeningPort());
        seaweedfs.start();
        S3Client s3 = ArmazenamentoS3Adapter.criarCliente(
                "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(PORTA_S3), "auto",
                CHAVE_ACESSO, CHAVE_SECRETA);
        Awaitility.await().atMost(Duration.ofSeconds(60)).ignoreExceptions()
                .until(() -> s3.createBucket(pedido -> pedido.bucket(BUCKET)) != null);
        armazenamento = new ArmazenamentoS3Adapter(s3, BUCKET);
    }

    @AfterAll
    static void parar() {
        seaweedfs.stop();
    }

    @Test
    void gravaLeTrechoERemove() throws Exception {
        byte[] conteudo = "0123456789".getBytes();

        assertThat(armazenamento.gravar("video/a.mp4", new ByteArrayInputStream(conteudo), 100)).isEqualTo(10);
        var midia = armazenamento.abrir("video/a.mp4").orElseThrow();

        assertThat(midia.tamanho()).isEqualTo(10);
        try (var trecho = midia.ler(2, 5)) {
            assertThat(new String(trecho.readAllBytes())).isEqualTo("2345");
        }
        armazenamento.remover("video/a.mp4");
        assertThat(armazenamento.abrir("video/a.mp4")).isEmpty();
    }

    @Test
    void naoEnviaArquivoAcimaDoLimite() {
        assertThatThrownBy(() -> armazenamento.gravar("video/b.mp4", new ByteArrayInputStream(new byte[20]), 10))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThat(armazenamento.abrir("video/b.mp4")).isEmpty();
    }
}
