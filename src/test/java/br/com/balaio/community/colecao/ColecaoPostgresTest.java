package br.com.balaio.community.colecao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

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
    void novaGravacaoSubstituiAAnterior() {
        UUID ana = UUID.randomUUID();
        colecoes.substituir(ana, TipoColecao.HISTORICO_BUSCA, List.of("cerâmica", "renda"));

        colecoes.substituir(ana, TipoColecao.HISTORICO_BUSCA, List.of("palha"));

        assertThat(colecoes.consultar(ana, TipoColecao.HISTORICO_BUSCA)).containsExactly("palha");
    }
}
