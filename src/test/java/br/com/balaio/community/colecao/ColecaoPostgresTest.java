package br.com.balaio.community.colecao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import br.com.balaio.community.colecao.application.ColecaoService;
import br.com.balaio.community.colecao.domain.TipoColecao;

@EnabledIfEnvironmentVariable(named = "COMMUNITY_POSTGRES_TEST", matches = "true")
@SpringBootTest(properties = "balaio.worker.enabled=false")
@Import(ColecaoPostgresTest.Postgres.class)
class ColecaoPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired ColecaoService colecoes;
    @MockitoBean JwtDecoder decoder;

    @Test
    void cadaUsuarioVeSomenteAsPropriasColecoesNaOrdemGravada() {
        UUID ana = UUID.randomUUID();
        UUID bia = UUID.randomUUID();

        colecoes.substituir(ana, TipoColecao.FAVORITOS, List.of("vaso", "cesto", "vaso"));
        colecoes.substituir(bia, TipoColecao.FAVORITOS, List.of("rede"));
        colecoes.substituir(ana, TipoColecao.VIDEOS_CURTIDOS, List.of("v1"));

        assertThat(colecoes.consultar(ana, TipoColecao.FAVORITOS)).containsExactly("vaso", "cesto");
        assertThat(colecoes.consultar(bia, TipoColecao.FAVORITOS)).containsExactly("rede");
        assertThat(colecoes.consultar(bia, TipoColecao.VIDEOS_CURTIDOS)).isEmpty();
    }

    @Test
    void doisDispositivosAlterandoAoMesmoTempoNaoPerdemItens() throws Exception {
        UUID ana = UUID.randomUUID();
        colecoes.substituir(ana, TipoColecao.VIDEOS_SALVOS, List.of("v0"));
        int dispositivos = 8;
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(dispositivos)) {
            var envios = IntStream.range(1, dispositivos + 1).mapToObj(i -> executor.submit(() -> {
                largada.await();
                return colecoes.alterar(ana, TipoColecao.VIDEOS_SALVOS, List.of("v" + i), null);
            })).toList();
            largada.countDown();
            for (var envio : envios) {
                envio.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(colecoes.consultar(ana, TipoColecao.VIDEOS_SALVOS))
                .hasSize(dispositivos + 1).contains("v0", "v1", "v8");
    }

    @Test
    void novaGravacaoSubstituiAAnterior() {
        UUID ana = UUID.randomUUID();
        colecoes.substituir(ana, TipoColecao.HISTORICO_BUSCA, List.of("cerâmica", "renda"));

        colecoes.substituir(ana, TipoColecao.HISTORICO_BUSCA, List.of("palha"));

        assertThat(colecoes.consultar(ana, TipoColecao.HISTORICO_BUSCA)).containsExactly("palha");
    }
}
