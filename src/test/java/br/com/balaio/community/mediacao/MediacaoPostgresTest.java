package br.com.balaio.community.mediacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.postgresql.PostgreSQLContainer;

import br.com.balaio.community.mediacao.application.AberturaMediacao;
import br.com.balaio.community.mediacao.application.EntregaDecisaoService;
import br.com.balaio.community.mediacao.application.MediacaoService;
import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort;
import br.com.balaio.community.mediacao.domain.DecisaoMediacao;
import br.com.balaio.community.mediacao.domain.EstadoMediacao;
import br.com.balaio.community.shared.exception.ConflitoException;

@EnabledIfEnvironmentVariable(named = "COMMUNITY_POSTGRES_TEST", matches = "true")
@SpringBootTest(properties = "balaio.worker.enabled=false")
@Import(MediacaoPostgresTest.Postgres.class)
class MediacaoPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired MediacaoService mediacoes;
    @Autowired EntregaDecisaoService entregas;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PedidosCommercePort commerce;
    @MockitoBean JwtDecoder decoder;

    @Test
    void persisteFluxoCompletoComRetentativaAposBackoff() {
        UUID pedido = UUID.randomUUID();
        var mediacao = mediacoes.abrir(new AberturaMediacao(pedido, UUID.randomUUID(), "Ajuda",
                "Preciso de mediação.", "token"));
        assertThat(mediacoes.iniciarAnalise(mediacao.id()).estado()).isEqualTo(EstadoMediacao.EM_ANALISE);

        mediacoes.registrarDecisao(mediacao.id(), DecisaoMediacao.CANCELAR_REEMBOLSAR);
        doThrow(new ResourceAccessException("timeout")).doNothing().when(commerce)
                .aplicarDecisao(eq(pedido), any(UUID.class), eq(DecisaoMediacao.CANCELAR_REEMBOLSAR));

        assertThat(entregas.entregar(mediacao.id()).tentativasEntrega()).isEqualTo(1);
        assertThat(entregas.aguardandoEntrega(50)).doesNotContain(mediacao.id());

        jdbc.update("UPDATE mediacoes SET atualizada_em = now() - interval '5 minutes' WHERE id = ?", mediacao.id());
        assertThat(entregas.aguardandoEntrega(50)).containsExactly(mediacao.id());

        assertThat(entregas.entregar(mediacao.id()).estado()).isEqualTo(EstadoMediacao.DECIDIDA);
        assertThat(mediacoes.registrarDecisao(mediacao.id(), DecisaoMediacao.CANCELAR_REEMBOLSAR).estado())
                .isEqualTo(EstadoMediacao.DECIDIDA);
    }

    @Test
    void bancoGaranteUmaMediacaoPorPedido() {
        UUID pedido = UUID.randomUUID();
        mediacoes.abrir(new AberturaMediacao(pedido, UUID.randomUUID(), "Ajuda", "Relato.", "token"));

        assertThatThrownBy(() -> mediacoes.abrir(new AberturaMediacao(pedido, UUID.randomUUID(), "Outra",
                "Relato.", "token"))).isInstanceOf(ConflitoException.class);
    }
}
