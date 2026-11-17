package br.com.balaio.community.mediacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.ResourceAccessException;

import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort;
import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort.EntregaRecusadaException;
import br.com.balaio.community.mediacao.domain.DecisaoMediacao;
import br.com.balaio.community.mediacao.domain.Mediacao;
import br.com.balaio.community.mediacao.domain.EstadoMediacao;
import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;

class MediacaoServiceTest {
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);
    private static final String TOKEN = "token-comprador";

    private MediacoesEmMemoria repositorio;
    private PedidosCommercePort commerce;
    private MediacaoService mediacoes;
    private EntregaDecisaoService entregas;

    @BeforeEach
    void preparar() {
        repositorio = new MediacoesEmMemoria();
        commerce = mock(PedidosCommercePort.class);
        when(commerce.entregaDeDecisoesConfigurada()).thenReturn(true);
        mediacoes = new MediacaoService(repositorio, commerce, RELOGIO);
        entregas = new EntregaDecisaoService(repositorio, commerce, TransactionOperations.withoutTransaction(), RELOGIO);
    }

    @Test
    void abreMediacaoDepoisDeConfirmarCompradorNoCommerce() {
        UUID pedido = UUID.randomUUID();

        var mediacao = mediacoes.abrir(abertura(pedido, UUID.randomUUID()));

        verify(commerce).confirmarCompradorDoPedido(pedido, TOKEN);
        assertThat(mediacao.estado()).isEqualTo(EstadoMediacao.ABERTA);
        assertThat(repositorio.existeParaPedido(pedido)).isTrue();
    }

    @Test
    void segundaMediacaoParaMesmoPedidoGeraConflitoSemConsultarCommerce() {
        UUID pedido = UUID.randomUUID();
        mediacoes.abrir(abertura(pedido, UUID.randomUUID()));

        assertThatThrownBy(() -> mediacoes.abrir(abertura(pedido, UUID.randomUUID())))
                .isInstanceOf(ConflitoException.class);
        verify(commerce, times(1)).confirmarCompradorDoPedido(eq(pedido), any());
    }

    @Test
    void naoRegistraMediacaoQuandoPedidoNaoEhDoComprador() {
        UUID pedido = UUID.randomUUID();
        doThrow(new AcessoNegadoException()).when(commerce).confirmarCompradorDoPedido(pedido, TOKEN);

        assertThatThrownBy(() -> mediacoes.abrir(abertura(pedido, UUID.randomUUID())))
                .isInstanceOf(AcessoNegadoException.class);
        assertThat(repositorio.existeParaPedido(pedido)).isFalse();
    }

    @Test
    void compradorListaApenasAsProprias() {
        UUID comprador = UUID.randomUUID();
        mediacoes.abrir(abertura(UUID.randomUUID(), comprador));
        mediacoes.abrir(abertura(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(mediacoes.listarDoComprador(comprador)).hasSize(1);
        assertThat(mediacoes.listarTodas()).hasSize(2);
    }

    @Test
    void mediacaoInexistenteRetornaNaoEncontrada() {
        assertThatThrownBy(() -> mediacoes.iniciarAnalise(UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void decisaoComFalhaDeRedeFicaPendenteERetentaComMesmoIdentificador() {
        UUID pedido = UUID.randomUUID();
        var mediacao = mediacoes.abrir(abertura(pedido, UUID.randomUUID()));
        mediacoes.iniciarAnalise(mediacao.id());
        mediacoes.registrarDecisao(mediacao.id(), DecisaoMediacao.CANCELAR_REEMBOLSAR);
        doThrow(new EntregaRecusadaException("commerce indisponível", new ResourceAccessException("timeout"))).doNothing()
                .when(commerce).aplicarDecisao(eq(pedido), any(UUID.class), eq(DecisaoMediacao.CANCELAR_REEMBOLSAR));

        var aposFalha = entregas.entregar(mediacao.id());
        assertThat(aposFalha.estado()).isEqualTo(EstadoMediacao.DECISAO_PENDENTE);
        assertThat(aposFalha.tentativasEntrega()).isEqualTo(1);
        assertThat(entregas.aguardandoEntrega(10)).containsExactly(mediacao.id());

        var aposRetentativa = entregas.entregar(mediacao.id());
        assertThat(aposRetentativa.estado()).isEqualTo(EstadoMediacao.DECIDIDA);

        var identificadores = ArgumentCaptor.forClass(UUID.class);
        verify(commerce, times(2)).aplicarDecisao(eq(pedido), identificadores.capture(),
                eq(DecisaoMediacao.CANCELAR_REEMBOLSAR));
        assertThat(identificadores.getAllValues()).containsOnly(identificadores.getAllValues().getFirst());
    }

    @Test
    void entregaIgnoraMediacaoQueNaoAguardaDecisao() {
        var mediacao = mediacoes.abrir(abertura(UUID.randomUUID(), UUID.randomUUID()));

        entregas.entregar(mediacao.id());

        verify(commerce, never()).aplicarDecisao(any(), any(), any());
    }

    @Test
    void semTokenInternoNaoChamaCommerceNemContaTentativa() {
        var mediacao = mediacaoDecidida(UUID.randomUUID());
        when(commerce.entregaDeDecisoesConfigurada()).thenReturn(false);

        var atual = entregas.entregar(mediacao.id());

        verify(commerce, never()).aplicarDecisao(any(), any(), any());
        assertThat(atual.estado()).isEqualTo(EstadoMediacao.DECISAO_PENDENTE);
        assertThat(atual.tentativasEntrega()).isZero();
        assertThat(entregas.aguardandoEntrega(10)).isEmpty();
    }

    @Test
    void erroDeProgramacaoNaoViraTentativaSilenciosa() {
        var mediacao = mediacaoDecidida(UUID.randomUUID());
        doThrow(new IllegalStateException("bug")).when(commerce).aplicarDecisao(any(), any(), any());

        assertThatThrownBy(() -> entregas.entregar(mediacao.id())).isInstanceOf(IllegalStateException.class);
        assertThat(repositorio.buscar(mediacao.id()).orElseThrow().tentativasEntrega()).isZero();
    }

    @Test
    void falhaNaoSobrescreveEntregaConfirmadaPorOutroWorker() {
        var mediacao = mediacaoDecidida(UUID.randomUUID());
        doAnswer(chamada -> {
            var concorrente = repositorio.buscarParaAtualizar(mediacao.id()).orElseThrow();
            concorrente.confirmarEntregaDaDecisao(OffsetDateTime.now(RELOGIO));
            repositorio.atualizar(concorrente);
            throw new EntregaRecusadaException("commerce indisponível", null);
        }).when(commerce).aplicarDecisao(any(), any(), any());

        var atual = entregas.entregar(mediacao.id());

        assertThat(atual.estado()).isEqualTo(EstadoMediacao.DECIDIDA);
        assertThat(atual.tentativasEntrega()).isZero();
    }

    private Mediacao mediacaoDecidida(UUID pedido) {
        var mediacao = mediacoes.abrir(abertura(pedido, UUID.randomUUID()));
        mediacoes.iniciarAnalise(mediacao.id());
        mediacoes.registrarDecisao(mediacao.id(), DecisaoMediacao.CANCELAR_REEMBOLSAR);
        return mediacao;
    }

    private static AberturaMediacao abertura(UUID pedido, UUID comprador) {
        return new AberturaMediacao(pedido, comprador, "Peça quebrada", "Chegou trincada.", TOKEN);
    }
}
