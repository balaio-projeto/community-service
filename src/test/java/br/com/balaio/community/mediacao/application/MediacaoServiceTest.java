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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.ResourceAccessException;

import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort;
import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort.FalhaNaEntregaException;
import br.com.balaio.community.mediacao.domain.DecisaoMediacao;
import br.com.balaio.community.mediacao.domain.Mediacao;
import br.com.balaio.community.mediacao.domain.EstadoMediacao;
import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

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

        assertThat(mediacoes.listarDoComprador(comprador, Pagina.primeira())).hasSize(1);
        assertThat(mediacoes.listarTodas(Pagina.primeira())).hasSize(2);
        assertThat(mediacoes.listarTodas(Pagina.de(1, 1))).hasSize(1);
    }

    @Test
    void aberturaGuardaOArtesaoInformadoPeloCommerce() {
        UUID pedido = UUID.randomUUID();
        UUID artesao = UUID.randomUUID();
        when(commerce.confirmarCompradorDoPedido(pedido, TOKEN)).thenReturn(artesao);

        var mediacao = mediacoes.abrir(abertura(pedido, UUID.randomUUID()));

        assertThat(mediacao.artesaoId()).isEqualTo(artesao);
    }

    @Test
    void artesaoListaApenasAsDosPedidosDeleSemConsultarCommerceQuandoTodasTemArtesao() {
        UUID artesao = UUID.randomUUID();
        UUID pedidoDoArtesao = UUID.randomUUID();
        UUID pedidoDeOutro = UUID.randomUUID();
        when(commerce.confirmarCompradorDoPedido(pedidoDoArtesao, TOKEN)).thenReturn(artesao);
        when(commerce.confirmarCompradorDoPedido(pedidoDeOutro, TOKEN)).thenReturn(UUID.randomUUID());
        mediacoes.abrir(abertura(pedidoDoArtesao, UUID.randomUUID()));
        mediacoes.abrir(abertura(pedidoDeOutro, UUID.randomUUID()));

        var lista = mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.primeira());

        assertThat(lista).extracting(Mediacao::pedidoId).containsExactly(pedidoDoArtesao);
        verify(commerce, never()).pedidosVendidosPeloArtesao(any());
    }

    @Test
    void mediacaoAntigaSemArtesaoEhVinculadaPelasVendasDoArtesao() {
        UUID artesao = UUID.randomUUID();
        UUID pedidoAntigo = UUID.randomUUID();
        UUID pedidoAntigoDeOutro = UUID.randomUUID();
        mediacoes.abrir(abertura(pedidoAntigo, UUID.randomUUID()));
        mediacoes.abrir(abertura(pedidoAntigoDeOutro, UUID.randomUUID()));
        when(commerce.pedidosVendidosPeloArtesao("token-artesao")).thenReturn(List.of(pedidoAntigo));

        var lista = mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.primeira());

        assertThat(lista).extracting(Mediacao::pedidoId).containsExactly(pedidoAntigo);
        assertThat(repositorio.buscar(lista.getFirst().id()).orElseThrow().artesaoId()).isEqualTo(artesao);
    }

    @Test
    void somenteArtesaoUsaAListagemDoArtesao() {
        var comprador = new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, TOKEN);

        assertThatThrownBy(() -> mediacoes.listarDoArtesao(comprador, Pagina.primeira()))
                .isInstanceOf(AcessoNegadoException.class);
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
        doThrow(FalhaNaEntregaException.temporaria("commerce indisponível", new ResourceAccessException("timeout"))).doNothing()
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
            throw FalhaNaEntregaException.temporaria("commerce indisponível", null);
        }).when(commerce).aplicarDecisao(any(), any(), any());

        var atual = entregas.entregar(mediacao.id());

        assertThat(atual.estado()).isEqualTo(EstadoMediacao.DECIDIDA);
        assertThat(atual.tentativasEntrega()).isZero();
    }

    @Test
    void esgotarTentativasLevaAoEstadoTerminalForaDaFilaDoWorker() {
        var mediacao = mediacaoDecidida(UUID.randomUUID());
        doThrow(FalhaNaEntregaException.temporaria("commerce respondeu HTTP 503", null))
                .when(commerce).aplicarDecisao(any(), any(), any());

        for (int tentativa = 1; tentativa < Mediacao.MAXIMO_TENTATIVAS_ENTREGA; tentativa++) {
            assertThat(entregas.entregar(mediacao.id()).estado()).isEqualTo(EstadoMediacao.DECISAO_PENDENTE);
        }
        var esgotada = entregas.entregar(mediacao.id());

        assertThat(esgotada.estado()).isEqualTo(EstadoMediacao.FALHA_ENTREGA);
        assertThat(esgotada.tentativasEntrega()).isEqualTo(Mediacao.MAXIMO_TENTATIVAS_ENTREGA);
        assertThat(esgotada.ultimoErroEntrega()).contains("503");
        assertThat(entregas.aguardandoEntrega(10)).isEmpty();
        entregas.entregar(mediacao.id());
        verify(commerce, times(Mediacao.MAXIMO_TENTATIVAS_ENTREGA)).aplicarDecisao(any(), any(), any());
    }

    @Test
    void recusaDefinitivaInterrompeAsTentativasNaPrimeiraFalha() {
        var mediacao = mediacaoDecidida(UUID.randomUUID());
        doThrow(FalhaNaEntregaException.definitiva("commerce recusou a decisão com HTTP 409", null))
                .when(commerce).aplicarDecisao(any(), any(), any());

        var atual = entregas.entregar(mediacao.id());

        assertThat(atual.estado()).isEqualTo(EstadoMediacao.FALHA_ENTREGA);
        assertThat(atual.tentativasEntrega()).isEqualTo(1);
        assertThat(entregas.aguardandoEntrega(10)).isEmpty();
    }

    @Test
    void reprocessamentoReenviaAMesmaDecisaoComOMesmoIdentificador() {
        UUID pedido = UUID.randomUUID();
        var mediacao = mediacaoDecidida(pedido);
        doThrow(FalhaNaEntregaException.definitiva("commerce recusou a decisão com HTTP 403", null)).doNothing()
                .when(commerce).aplicarDecisao(any(), any(), any());
        entregas.entregar(mediacao.id());

        var reprocessada = entregas.reprocessar(mediacao.id());

        assertThat(reprocessada.estado()).isEqualTo(EstadoMediacao.DECIDIDA);
        var identificadores = ArgumentCaptor.forClass(UUID.class);
        verify(commerce, times(2)).aplicarDecisao(eq(pedido), identificadores.capture(),
                eq(DecisaoMediacao.CANCELAR_REEMBOLSAR));
        assertThat(identificadores.getAllValues()).containsOnly(reprocessada.decisaoId());
    }

    @Test
    void reprocessamentoComNovaFalhaTemporariaVoltaParaAFilaComContagemZerada() {
        var mediacao = mediacaoDecidida(UUID.randomUUID());
        doThrow(FalhaNaEntregaException.definitiva("commerce recusou a decisão com HTTP 401", null))
                .doThrow(FalhaNaEntregaException.temporaria("commerce respondeu HTTP 503", null))
                .when(commerce).aplicarDecisao(any(), any(), any());
        entregas.entregar(mediacao.id());

        var reprocessada = entregas.reprocessar(mediacao.id());

        assertThat(reprocessada.estado()).isEqualTo(EstadoMediacao.DECISAO_PENDENTE);
        assertThat(reprocessada.tentativasEntrega()).isEqualTo(1);
        assertThat(entregas.aguardandoEntrega(10)).containsExactly(mediacao.id());
    }

    @Test
    void reprocessarMediacaoQueNaoFalhouGeraConflito() {
        var mediacao = mediacaoDecidida(UUID.randomUUID());

        assertThatThrownBy(() -> entregas.reprocessar(mediacao.id())).isInstanceOf(ConflitoException.class);
        verify(commerce, never()).aplicarDecisao(any(), any(), any());
    }

    @Test
    void compradorDesisteDaPropriaMediacaoAbertaSemConsultarCommerce() {
        UUID comprador = UUID.randomUUID();
        var mediacao = mediacoes.abrir(abertura(UUID.randomUUID(), comprador));

        var cancelada = mediacoes.cancelar(mediacao.id(), compradorAutenticado(comprador));

        assertThat(cancelada.estado()).isEqualTo(EstadoMediacao.CANCELADA);
        assertThat(cancelada.canceladaPor()).isEqualTo(comprador);
        assertThat(cancelada.canceladaEm()).isEqualTo(OffsetDateTime.now(RELOGIO));
        assertThat(repositorio.buscar(mediacao.id()).orElseThrow().estaCancelada()).isTrue();
        verify(commerce, times(1)).confirmarCompradorDoPedido(any(), any());
        verify(commerce, never()).aplicarDecisao(any(), any(), any());
    }

    @Test
    void administradorEncerraSemDecisaoUmaMediacaoEmAnalise() {
        UUID admin = UUID.randomUUID();
        var mediacao = mediacoes.abrir(abertura(UUID.randomUUID(), UUID.randomUUID()));
        mediacoes.iniciarAnalise(mediacao.id());

        var cancelada = mediacoes.cancelar(mediacao.id(), new UsuarioAutenticado(admin, Papel.ADMIN, "token-admin"));

        assertThat(cancelada.estado()).isEqualTo(EstadoMediacao.CANCELADA);
        assertThat(cancelada.canceladaPor()).isEqualTo(admin);
        assertThat(cancelada.decisao()).isNull();
    }

    @Test
    void outroCompradorNaoEnxergaAMediacaoParaCancelar() {
        var mediacao = mediacoes.abrir(abertura(UUID.randomUUID(), UUID.randomUUID()));

        assertThatThrownBy(() -> mediacoes.cancelar(mediacao.id(), compradorAutenticado(UUID.randomUUID())))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(repositorio.buscar(mediacao.id()).orElseThrow().estado()).isEqualTo(EstadoMediacao.ABERTA);
    }

    @Test
    void artesaoVendedorNaoCancelaMediacaoAbertaPeloComprador() {
        UUID artesao = UUID.randomUUID();
        UUID pedido = UUID.randomUUID();
        when(commerce.confirmarCompradorDoPedido(pedido, TOKEN)).thenReturn(artesao);
        var mediacao = mediacoes.abrir(abertura(pedido, UUID.randomUUID()));

        assertThatThrownBy(() -> mediacoes.cancelar(mediacao.id(), artesaoAutenticado(artesao)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(repositorio.buscar(mediacao.id()).orElseThrow().estado()).isEqualTo(EstadoMediacao.ABERTA);
    }

    @Test
    void organizadorNaoCancelaMediacao() {
        var mediacao = mediacoes.abrir(abertura(UUID.randomUUID(), UUID.randomUUID()));
        var organizador = new UsuarioAutenticado(UUID.randomUUID(), Papel.ORGANIZADOR, "token-organizador");

        assertThatThrownBy(() -> mediacoes.cancelar(mediacao.id(), organizador))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void artesaoQueComprouAbreECancelaAPropriaMediacao() {
        UUID artesaoComprador = UUID.randomUUID();
        UUID compra = UUID.randomUUID();
        when(commerce.confirmarCompradorDoPedido(compra, TOKEN)).thenReturn(UUID.randomUUID());
        var mediacao = mediacoes.abrir(abertura(compra, artesaoComprador));

        var cancelada = mediacoes.cancelar(mediacao.id(), artesaoAutenticado(artesaoComprador));

        assertThat(cancelada.estado()).isEqualTo(EstadoMediacao.CANCELADA);
        assertThat(cancelada.canceladaPor()).isEqualTo(artesaoComprador);
    }

    @Test
    void artesaoListaNumaSoPaginaAsMediacoesDasComprasEDasVendasSemDuplicar() {
        UUID artesao = UUID.randomUUID();
        UUID venda = UUID.randomUUID();
        UUID compra = UUID.randomUUID();
        UUID pedidoDeTerceiros = UUID.randomUUID();
        when(commerce.confirmarCompradorDoPedido(venda, TOKEN)).thenReturn(artesao);
        when(commerce.confirmarCompradorDoPedido(compra, TOKEN)).thenReturn(UUID.randomUUID());
        when(commerce.confirmarCompradorDoPedido(pedidoDeTerceiros, TOKEN)).thenReturn(UUID.randomUUID());
        mediacoes.abrir(abertura(venda, UUID.randomUUID()));
        mediacoes.abrir(abertura(compra, artesao));
        mediacoes.abrir(abertura(pedidoDeTerceiros, UUID.randomUUID()));

        var lista = mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.primeira());

        assertThat(lista).extracting(Mediacao::pedidoId).containsExactlyInAnyOrder(venda, compra);
        assertThat(lista).extracting(Mediacao::id).doesNotHaveDuplicates();
        assertThat(mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.de(0, 1))).hasSize(1);
        assertThat(mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.de(1, 1))).hasSize(1);
        assertThat(mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.de(2, 1))).isEmpty();
    }

    @Test
    void cancelarMediacaoJaDecididaGeraConflito() {
        var mediacao = mediacaoDecidida(UUID.randomUUID());
        var admin = new UsuarioAutenticado(UUID.randomUUID(), Papel.ADMIN, "token-admin");

        assertThatThrownBy(() -> mediacoes.cancelar(mediacao.id(), admin)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> mediacoes.cancelar(mediacao.id(), compradorAutenticado(mediacao.compradorId())))
                .isInstanceOf(ConflitoException.class);
        assertThat(repositorio.buscar(mediacao.id()).orElseThrow().estado())
                .isEqualTo(EstadoMediacao.DECISAO_PENDENTE);
    }

    @Test
    void cancelarMediacaoInexistenteRetornaNaoEncontrada() {
        var admin = new UsuarioAutenticado(UUID.randomUUID(), Papel.ADMIN, "token-admin");

        assertThatThrownBy(() -> mediacoes.cancelar(UUID.randomUUID(), admin))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void cancelarDeNovoDevolveAMesmaMediacaoCancelada() {
        UUID comprador = UUID.randomUUID();
        var mediacao = mediacoes.abrir(abertura(UUID.randomUUID(), comprador));
        var primeira = mediacoes.cancelar(mediacao.id(), compradorAutenticado(comprador));

        var repetida = mediacoes.cancelar(mediacao.id(),
                new UsuarioAutenticado(UUID.randomUUID(), Papel.ADMIN, "token-admin"));

        assertThat(repetida.estado()).isEqualTo(EstadoMediacao.CANCELADA);
        assertThat(repetida.canceladaPor()).isEqualTo(comprador);
        assertThat(repetida.canceladaEm()).isEqualTo(primeira.canceladaEm());
    }

    @Test
    void canceladaSomeDasListagensEPedidoAceitaNovaMediacao() {
        UUID comprador = UUID.randomUUID();
        UUID artesao = UUID.randomUUID();
        UUID pedido = UUID.randomUUID();
        when(commerce.confirmarCompradorDoPedido(pedido, TOKEN)).thenReturn(artesao);
        var primeira = mediacoes.abrir(abertura(pedido, comprador));
        mediacoes.cancelar(primeira.id(), compradorAutenticado(comprador));

        assertThat(mediacoes.listarDoComprador(comprador, Pagina.primeira())).isEmpty();
        assertThat(mediacoes.listarDoArtesao(artesaoAutenticado(artesao), Pagina.primeira())).isEmpty();
        assertThat(mediacoes.listarTodas(Pagina.primeira())).isEmpty();

        var nova = mediacoes.abrir(abertura(pedido, comprador));

        assertThat(nova.id()).isNotEqualTo(primeira.id());
        assertThat(nova.estado()).isEqualTo(EstadoMediacao.ABERTA);
        assertThat(mediacoes.listarDoComprador(comprador, Pagina.primeira())).extracting(Mediacao::id)
                .containsExactly(nova.id());
        assertThatThrownBy(() -> mediacoes.abrir(abertura(pedido, comprador))).isInstanceOf(ConflitoException.class);
    }

    private static UsuarioAutenticado compradorAutenticado(UUID comprador) {
        return new UsuarioAutenticado(comprador, Papel.COMPRADOR, TOKEN);
    }

    private Mediacao mediacaoDecidida(UUID pedido) {
        var mediacao = mediacoes.abrir(abertura(pedido, UUID.randomUUID()));
        mediacoes.iniciarAnalise(mediacao.id());
        mediacoes.registrarDecisao(mediacao.id(), DecisaoMediacao.CANCELAR_REEMBOLSAR);
        return mediacao;
    }

    private static UsuarioAutenticado artesaoAutenticado(UUID artesao) {
        return new UsuarioAutenticado(artesao, Papel.ARTESAO, "token-artesao");
    }

    private static AberturaMediacao abertura(UUID pedido, UUID comprador) {
        return new AberturaMediacao(pedido, comprador, "Peça quebrada", "Chegou trincada.", TOKEN);
    }
}
