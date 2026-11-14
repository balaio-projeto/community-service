package br.com.balaio.community.avaliacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

import br.com.balaio.community.avaliacao.application.AvaliacaoService;
import br.com.balaio.community.avaliacao.application.AvaliacaoService.NovaAvaliacao;
import br.com.balaio.community.avaliacao.application.port.out.PedidosAvaliaveisPort;
import br.com.balaio.community.avaliacao.domain.PedidoAvaliavel;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@EnabledIfEnvironmentVariable(named = "COMMUNITY_POSTGRES_TEST", matches = "true")
@SpringBootTest(properties = "balaio.worker.enabled=false")
@Import(AvaliacaoPostgresTest.Postgres.class)
class AvaliacaoPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired AvaliacaoService avaliacoes;
    @MockitoBean PedidosAvaliaveisPort pedidos;
    @MockitoBean JwtDecoder decoder;

    @Test
    void persisteAvaliacoesCalculaReputacaoEBloqueiaRepeticao() {
        UUID artesao = UUID.randomUUID();
        var primeiro = avaliar(artesao, 5, List.of("Prazo", "Embalagem"));
        avaliar(artesao, 4, List.of());

        assertThat(avaliacoes.consultarDoPedido(primeiro.pedido(), primeiro.comprador()).aspectos())
                .containsExactly("Prazo", "Embalagem");
        var reputacao = avaliacoes.reputacaoDoArtesao("artesao-" + artesao);
        assertThat(reputacao.total()).isEqualTo(2);
        assertThat(reputacao.media()).isEqualByComparingTo("4.5");
        assertThatThrownBy(() -> avaliacoes.avaliar(primeiro.pedido(), new NovaAvaliacao(1, null, null),
                primeiro.comprador())).isInstanceOf(ConflitoException.class);
    }

    private Avaliado avaliar(UUID artesao, int nota, List<String> aspectos) {
        UUID pedido = UUID.randomUUID();
        var comprador = new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "t-" + pedido);
        when(pedidos.buscarDoComprador(any(), any()))
                .thenReturn(new PedidoAvaliavel(pedido, comprador.id(), artesao, "vaso", "entregue"));
        avaliacoes.avaliar(pedido, new NovaAvaliacao(nota, null, aspectos), comprador);
        return new Avaliado(pedido, comprador);
    }

    private record Avaliado(UUID pedido, UsuarioAutenticado comprador) {
    }
}
