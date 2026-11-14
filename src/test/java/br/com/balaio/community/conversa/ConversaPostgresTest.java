package br.com.balaio.community.conversa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
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

import br.com.balaio.community.conversa.application.ConversaService;
import br.com.balaio.community.conversa.application.port.out.PedidosDaConversaPort;
import br.com.balaio.community.conversa.domain.Mensagem;
import br.com.balaio.community.conversa.domain.ParteDaConversa;
import br.com.balaio.community.conversa.domain.PedidoDaConversa;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@EnabledIfEnvironmentVariable(named = "COMMUNITY_POSTGRES_TEST", matches = "true")
@SpringBootTest(properties = "balaio.worker.enabled=false")
@Import(ConversaPostgresTest.Postgres.class)
class ConversaPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired ConversaService conversas;
    @MockitoBean PedidosDaConversaPort pedidos;
    @MockitoBean JwtDecoder decoder;

    @Test
    void mensagensEPrimeiraLeituraPersistemEntreSessoes() {
        var comprador = new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "c");
        var artesao = new UsuarioAutenticado(UUID.randomUUID(), Papel.ARTESAO, "a");
        var pedido = new PedidoDaConversa(UUID.randomUUID(), "Ana", "14/11/2026");
        when(pedidos.parteNoPedido(pedido.id(), comprador)).thenReturn(Optional.of(ParteDaConversa.COMPRADOR));
        when(pedidos.parteNoPedido(pedido.id(), artesao)).thenReturn(Optional.of(ParteDaConversa.ARTESAO));
        when(pedidos.vendasDoArtesao(artesao)).thenReturn(List.of(pedido));

        conversas.enviar(pedido.id(), "Primeira", comprador);
        conversas.enviar(pedido.id(), "Segunda", comprador);
        assertThat(conversas.conversasDoArtesao(artesao).getFirst().naoLida()).isTrue();

        assertThat(conversas.lerConversa(pedido.id(), artesao)).extracting(Mensagem::texto)
                .containsExactly("Primeira", "Segunda");
        assertThat(conversas.conversasDoArtesao(artesao).getFirst().naoLida()).isFalse();
    }
}
