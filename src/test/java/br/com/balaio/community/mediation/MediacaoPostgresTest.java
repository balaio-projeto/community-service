package br.com.balaio.community.mediation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.postgresql.PostgreSQLContainer;

@EnabledIfEnvironmentVariable(named = "COMMUNITY_POSTGRES_TEST", matches = "true")
@SpringBootTest(properties = "balaio.worker.intervalo-ms=3600000")
@Import(MediacaoPostgresTest.Postgres.class)
class MediacaoPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean @ServiceConnection PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired MediacaoService mediacoes;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CommercePedidoHttpAdapter commerce;
    @MockitoBean JwtDecoder decoder;

    @Test
    void decisaoDuravelRetentaComMesmoIdentificador() {
        UUID pedido = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        var criada = mediacoes.criar(pedido, comprador, "Ajuda", "Preciso de mediação.", "Bearer teste");
        UUID id = (UUID) criada.get("id");
        assertThat(mediacoes.abrir(id).get("estado")).isEqualTo("emAnalise");
        assertThatThrownBy(() -> mediacoes.criar(pedido, comprador, "Ajuda", "Outro relato.", "Bearer teste"))
                .isInstanceOf(MediacaoException.class);
        mediacoes.decidir(id, "cancelar_reembolsar");
        // A decisão é persistida antes da entrega; o mesmo ID é reutilizado após falha.
        doThrow(new ResourceAccessException("timeout")).doNothing()
                .when(commerce).aplicarDecisao(org.mockito.ArgumentMatchers.eq(pedido),
                        org.mockito.ArgumentMatchers.any(UUID.class),
                        org.mockito.ArgumentMatchers.eq("cancelar_reembolsar"));
        jdbc.update("UPDATE mediacoes SET atualizada_em = now() - interval '5 minutes' WHERE id = ?", id);
        mediacoes.entregarPendentes();
        assertThat(mediacoes.consultar(id).get("estado")).isEqualTo("decisao_pendente");
        mediacoes.entregar(id);
        assertThat(mediacoes.consultar(id).get("estado")).isEqualTo("decidida");
        assertThat(mediacoes.decidir(id, "cancelar_reembolsar").get("estado")).isEqualTo("decidida");
        assertThatThrownBy(() -> mediacoes.decidir(id, "negar")).isInstanceOf(MediacaoException.class);
        var ids = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(commerce, org.mockito.Mockito.times(2)).aplicarDecisao(
                org.mockito.ArgumentMatchers.eq(pedido), ids.capture(),
                org.mockito.ArgumentMatchers.eq("cancelar_reembolsar"));
        assertThat(ids.getAllValues()).containsOnly(ids.getAllValues().getFirst());
    }
}
