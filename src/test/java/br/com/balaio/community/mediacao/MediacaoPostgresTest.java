package br.com.balaio.community.mediacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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
import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort.FalhaNaEntregaException;
import br.com.balaio.community.mediacao.domain.DecisaoMediacao;
import br.com.balaio.community.mediacao.domain.EstadoMediacao;
import br.com.balaio.community.mediacao.domain.Mediacao;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

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
        when(commerce.entregaDeDecisoesConfigurada()).thenReturn(true);
        doThrow(FalhaNaEntregaException.temporaria("commerce indisponível", new ResourceAccessException("timeout")))
                .doNothing().when(commerce)
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
    void mediacaoComEntregaRecusadaPersisteEstadoTerminalEPodeSerReprocessada() {
        UUID pedido = UUID.randomUUID();
        var mediacao = mediacoes.abrir(new AberturaMediacao(pedido, UUID.randomUUID(), "Ajuda", "Relato.", "token"));
        mediacoes.registrarDecisao(mediacao.id(), DecisaoMediacao.NEGAR);
        when(commerce.entregaDeDecisoesConfigurada()).thenReturn(true);
        doThrow(FalhaNaEntregaException.definitiva("commerce recusou a decisão com HTTP 409", null)).doNothing()
                .when(commerce).aplicarDecisao(any(), any(), any());

        assertThat(entregas.entregar(mediacao.id()).estado()).isEqualTo(EstadoMediacao.FALHA_ENTREGA);
        jdbc.update("UPDATE mediacoes SET atualizada_em = now() - interval '5 minutes' WHERE id = ?", mediacao.id());
        assertThat(entregas.aguardandoEntrega(50)).doesNotContain(mediacao.id());
        assertThat(jdbc.queryForObject("SELECT estado FROM mediacoes WHERE id = ?", String.class, mediacao.id()))
                .isEqualTo("falha_entrega");

        assertThat(entregas.reprocessar(mediacao.id()).estado()).isEqualTo(EstadoMediacao.DECIDIDA);
    }

    @Test
    void compradorEArtesaoListamSomenteAsMediacoesDosPropriosPedidos() {
        UUID comprador = UUID.randomUUID();
        UUID artesao = UUID.randomUUID();
        UUID outroArtesao = UUID.randomUUID();
        UUID pedidoDoArtesao = UUID.randomUUID();
        UUID pedidoDeOutroArtesao = UUID.randomUUID();
        when(commerce.confirmarCompradorDoPedido(pedidoDoArtesao, "token")).thenReturn(artesao);
        when(commerce.confirmarCompradorDoPedido(pedidoDeOutroArtesao, "token")).thenReturn(outroArtesao);
        mediacoes.abrir(new AberturaMediacao(pedidoDoArtesao, comprador, "Ajuda", "Relato.", "token"));
        mediacoes.abrir(new AberturaMediacao(pedidoDeOutroArtesao, UUID.randomUUID(), "Ajuda", "Relato.", "token"));

        assertThat(mediacoes.listarDoComprador(comprador, Pagina.primeira()))
                .extracting(Mediacao::pedidoId).containsExactly(pedidoDoArtesao);
        assertThat(mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.primeira()))
                .extracting(Mediacao::pedidoId).containsExactly(pedidoDoArtesao);
        assertThat(mediacoes.listarDoArtesao(artesaoAutenticado(outroArtesao), Pagina.primeira()))
                .extracting(Mediacao::pedidoId).containsExactly(pedidoDeOutroArtesao);
        assertThat(jdbc.queryForObject("SELECT artesao_id FROM mediacoes WHERE pedido_id = ?", UUID.class,
                pedidoDoArtesao)).isEqualTo(artesao);
    }

    @Test
    void artesaoQueTambemCompraListaComprasEVendasSemDuplicarEPaginando() {
        UUID artesao = UUID.randomUUID();
        UUID venda = UUID.randomUUID();
        UUID compra = UUID.randomUUID();
        when(commerce.confirmarCompradorDoPedido(venda, "token")).thenReturn(artesao);
        when(commerce.confirmarCompradorDoPedido(compra, "token")).thenReturn(UUID.randomUUID());
        mediacoes.abrir(new AberturaMediacao(venda, UUID.randomUUID(), "Ajuda", "Relato.", "token"));
        mediacoes.abrir(new AberturaMediacao(compra, artesao, "Ajuda", "Relato.", "token"));

        assertThat(mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.primeira()))
                .extracting(Mediacao::pedidoId).containsExactlyInAnyOrder(venda, compra);
        var primeira = mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.de(0, 1));
        var segunda = mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.de(1, 1));
        assertThat(primeira).hasSize(1);
        assertThat(segunda).hasSize(1);
        assertThat(primeira.getFirst().id()).isNotEqualTo(segunda.getFirst().id());
        assertThat(mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.de(2, 1))).isEmpty();
    }

    @Test
    void mediacaoAnteriorAoVinculoDoArtesaoEhAssociadaPelasVendasDele() {
        UUID artesao = UUID.randomUUID();
        UUID pedidoAntigo = UUID.randomUUID();
        var antiga = mediacoes.abrir(new AberturaMediacao(pedidoAntigo, UUID.randomUUID(), "Ajuda", "Relato.",
                "token"));
        jdbc.update("UPDATE mediacoes SET artesao_id = NULL WHERE id = ?", antiga.id());
        when(commerce.pedidosVendidosPeloArtesao("token-artesao")).thenReturn(List.of(pedidoAntigo));

        assertThat(mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.primeira()))
                .extracting(Mediacao::id).containsExactly(antiga.id());
        assertThat(jdbc.queryForObject("SELECT artesao_id FROM mediacoes WHERE id = ?", UUID.class, antiga.id()))
                .isEqualTo(artesao);
        assertThat(mediacoes.listarDoArtesao(artesaoAutenticado(UUID.randomUUID()), Pagina.primeira()))
                .extracting(Mediacao::id).doesNotContain(antiga.id());
    }

    private static UsuarioAutenticado artesaoAutenticado(UUID artesao) {
        return new UsuarioAutenticado(artesao, Papel.ARTESAO, "token-artesao");
    }

    @Test
    void cancelamentoPersisteAutorEPermiteNovaMediacaoNoMesmoPedido() {
        UUID comprador = UUID.randomUUID();
        UUID artesao = UUID.randomUUID();
        UUID pedido = UUID.randomUUID();
        when(commerce.confirmarCompradorDoPedido(pedido, "token")).thenReturn(artesao);
        var primeira = mediacoes.abrir(new AberturaMediacao(pedido, comprador, "Ajuda", "Relato.", "token"));
        mediacoes.iniciarAnalise(primeira.id());

        var cancelada = mediacoes.cancelar(primeira.id(), new UsuarioAutenticado(comprador, Papel.COMPRADOR, "token"));

        assertThat(cancelada.estado()).isEqualTo(EstadoMediacao.CANCELADA);
        assertThat(jdbc.queryForObject("SELECT estado FROM mediacoes WHERE id = ?", String.class, primeira.id()))
                .isEqualTo("cancelada");
        assertThat(jdbc.queryForObject("SELECT cancelada_por FROM mediacoes WHERE id = ?", UUID.class, primeira.id()))
                .isEqualTo(comprador);
        assertThat(jdbc.queryForObject("SELECT cancelada_em IS NOT NULL FROM mediacoes WHERE id = ?", Boolean.class,
                primeira.id())).isTrue();
        assertThat(mediacoes.listarDoComprador(comprador, Pagina.primeira())).isEmpty();
        assertThat(mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.primeira())).isEmpty();
        assertThat(mediacoes.listarTodas(Pagina.de(0, 100))).extracting(Mediacao::id).doesNotContain(primeira.id());

        var nova = mediacoes.abrir(new AberturaMediacao(pedido, comprador, "De novo", "Relato.", "token"));

        assertThat(nova.estado()).isEqualTo(EstadoMediacao.ABERTA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mediacoes WHERE pedido_id = ?", Integer.class, pedido))
                .isEqualTo(2);
        assertThat(mediacoes.listarDoComprador(comprador, Pagina.primeira())).extracting(Mediacao::id)
                .containsExactly(nova.id());
        assertThat(mediacoes.listarTodas(Pagina.de(0, 100))).extracting(Mediacao::id).contains(nova.id());
        assertThat(mediacoes.cancelar(primeira.id(), new UsuarioAutenticado(UUID.randomUUID(), Papel.ADMIN, "t"))
                .canceladaPor()).isEqualTo(comprador);
    }

    @Test
    void indiceParcialRecusaDuasMediacoesAtivasNoMesmoPedidoMasAceitaCanceladas() {
        UUID pedido = UUID.randomUUID();
        var original = mediacoes.abrir(new AberturaMediacao(pedido, UUID.randomUUID(), "Ajuda", "Relato.", "token"));
        String insercao = "INSERT INTO mediacoes (id, pedido_id, comprador_id, assunto, relato, estado) "
                + "VALUES (?, ?, ?, 'Outra', 'Relato.', ?)";

        jdbc.update(insercao, UUID.randomUUID(), pedido, UUID.randomUUID(), "cancelada");
        jdbc.update(insercao, UUID.randomUUID(), pedido, UUID.randomUUID(), "cancelada");
        assertThatThrownBy(() -> jdbc.update(insercao, UUID.randomUUID(), pedido, UUID.randomUUID(), "aberta"))
                .isInstanceOf(DataIntegrityViolationException.class);

        mediacoes.cancelar(original.id(), new UsuarioAutenticado(UUID.randomUUID(), Papel.ADMIN, "token"));
        jdbc.update(insercao, UUID.randomUUID(), pedido, UUID.randomUUID(), "aberta");
    }

    @Test
    void bancoGaranteUmaMediacaoPorPedido() {
        UUID pedido = UUID.randomUUID();
        mediacoes.abrir(new AberturaMediacao(pedido, UUID.randomUUID(), "Ajuda", "Relato.", "token"));

        assertThatThrownBy(() -> mediacoes.abrir(new AberturaMediacao(pedido, UUID.randomUUID(), "Outra",
                "Relato.", "token"))).isInstanceOf(ConflitoException.class);
    }
}
