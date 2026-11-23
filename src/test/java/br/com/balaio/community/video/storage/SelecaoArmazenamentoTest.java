package br.com.balaio.community.video.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import br.com.balaio.community.video.application.port.out.ArmazenamentoMidiaPort;

class SelecaoArmazenamentoTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ArmazenamentoS3Adapter.class, ArmazenamentoLocalAdapter.class);

    @Test
    void armazenamentoS3SobePeloSpringComAsPropriedadesDoAmbiente() {
        contexto.withPropertyValues(
                        "balaio.midia.armazenamento=s3",
                        "balaio.midia.s3.endpoint=https://conta.r2.cloudflarestorage.com",
                        "balaio.midia.s3.regiao=auto",
                        "balaio.midia.s3.bucket=balaio-midia",
                        "balaio.midia.s3.chave-acesso=chave",
                        "balaio.midia.s3.chave-secreta=segredo")
                .run(app -> {
                    assertThat(app).hasNotFailed();
                    assertThat(app).getBean(ArmazenamentoMidiaPort.class).isInstanceOf(ArmazenamentoS3Adapter.class);
                });
    }
}
