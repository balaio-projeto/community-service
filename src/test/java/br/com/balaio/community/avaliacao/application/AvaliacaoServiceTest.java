package br.com.balaio.community.avaliacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.balaio.community.avaliacao.application.AvaliacaoService.NovaAvaliacao;
import br.com.balaio.community.avaliacao.application.port.out.AvaliacoesPort;
import br.com.balaio.community.avaliacao.application.port.out.PedidosAvaliaveisPort;
import br.com.balaio.community.avaliacao.domain.Avaliacao;
import br.com.balaio.community.avaliacao.domain.PedidoAvaliavel;
import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

class AvaliacaoServiceTest {
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);
    private static final NovaAvaliacao CINCO_ESTRELAS = new NovaAvaliacao(5, "Ótima", List.of("Prazo"));

    private final UUID pedidoId = UUID.randomUUID();
    private final UUID artesaoId = UUID.randomUUID();
    private final UsuarioAutenticado comprador = new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "t");

    private PedidosAvaliaveisPort pedidos;
    private AvaliacaoService avaliacoes;

    @BeforeEach
    void preparar() {
        pedidos = mock(PedidosAvaliaveisPort.class);
        avaliacoes = new AvaliacaoService(new AvaliacoesEmMemoria(), pedidos, RELOGIO);
        when(pedidos.buscarDoComprador(pedidoId, "t"))
                .thenReturn(new PedidoAvaliavel(pedidoId, comprador.id(), artesaoId, "vaso", "entregue"));
    }

    @Test
    void compradorAvaliaPedidoEntregueEReputacaoReflete() {
        avaliacoes.avaliar(pedidoId, CINCO_ESTRELAS, comprador);

        var reputacao = avaliacoes.reputacaoDoArtesao("artesao-" + artesaoId);
        assertThat(reputacao.total()).isEqualTo(1);
        assertThat(avaliacoes.consultarDoPedido(pedidoId, comprador).nota()).isEqualTo(5);
    }

    @Test
    void segundaAvaliacaoDoMesmoPedidoEhRecusada() {
        avaliacoes.avaliar(pedidoId, CINCO_ESTRELAS, comprador);

        assertThatThrownBy(() -> avaliacoes.avaliar(pedidoId, CINCO_ESTRELAS, comprador))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void artesaoNaoAvaliaPedido() {
        var artesao = new UsuarioAutenticado(artesaoId, Papel.ARTESAO, "t");

        assertThatThrownBy(() -> avaliacoes.avaliar(pedidoId, CINCO_ESTRELAS, artesao))
                .isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(pedidos);
    }

    @Test
    void outroCompradorNaoVeAvaliacaoAlheia() {
        avaliacoes.avaliar(pedidoId, CINCO_ESTRELAS, comprador);
        var outro = new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "x");

        assertThatThrownBy(() -> avaliacoes.consultarDoPedido(pedidoId, outro))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void slugDeArtesaoInvalidoRetornaNaoEncontrado() {
        assertThatThrownBy(() -> avaliacoes.reputacaoDoArtesao("maria"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private static final class AvaliacoesEmMemoria implements AvaliacoesPort {
        private final List<Avaliacao> armazenadas = new ArrayList<>();

        @Override
        public boolean existeParaPedido(UUID pedidoId) {
            return buscarDoPedido(pedidoId).isPresent();
        }

        @Override
        public Avaliacao registrar(Avaliacao avaliacao) {
            armazenadas.add(avaliacao);
            return avaliacao;
        }

        @Override
        public Optional<Avaliacao> buscarDoPedido(UUID pedidoId) {
            return armazenadas.stream().filter(a -> a.pedidoId().equals(pedidoId)).findFirst();
        }

        @Override
        public List<Integer> notasDoArtesao(UUID artesaoId) {
            return armazenadas.stream().filter(a -> a.artesaoId().equals(artesaoId)).map(Avaliacao::nota).toList();
        }
    }
}
