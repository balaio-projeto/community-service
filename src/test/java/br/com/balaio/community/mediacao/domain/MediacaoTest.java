package br.com.balaio.community.mediacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

class MediacaoTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 14, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void abreMediacaoComTextosNormalizados() {
        var mediacao = novaMediacao();

        assertThat(mediacao.estado()).isEqualTo(EstadoMediacao.ABERTA);
        assertThat(mediacao.assunto()).isEqualTo("Peça quebrada");
        assertThat(mediacao.relato()).isEqualTo("Chegou trincada.");
        assertThat(mediacao.decisao()).isNull();
    }

    @Test
    void recusaAberturaSemDadosObrigatorios() {
        assertThatThrownBy(() -> Mediacao.abrir(null, UUID.randomUUID(), "Assunto", "Relato", AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> Mediacao.abrir(UUID.randomUUID(), UUID.randomUUID(), " ", "Relato", AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> Mediacao.abrir(UUID.randomUUID(), UUID.randomUUID(), "Assunto", null, AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void recusaAssuntoAcimaDoLimite() {
        String assuntoLongo = "a".repeat(Mediacao.TAMANHO_MAXIMO_ASSUNTO + 1);

        assertThatThrownBy(() -> Mediacao.abrir(UUID.randomUUID(), UUID.randomUUID(), assuntoLongo, "Relato", AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void decisaoRepetidaEhIdempotenteEMantemIdentificador() {
        var mediacao = novaMediacao();
        mediacao.registrarDecisao(DecisaoMediacao.NEGAR, AGORA);
        UUID primeiroId = mediacao.decisaoId();

        mediacao.registrarDecisao(DecisaoMediacao.NEGAR, AGORA.plusMinutes(1));

        assertThat(mediacao.decisaoId()).isEqualTo(primeiroId);
        assertThat(mediacao.aguardaEntregaDaDecisao()).isTrue();
    }

    @Test
    void decisaoDiferenteDaRegistradaGeraConflito() {
        var mediacao = novaMediacao();
        mediacao.registrarDecisao(DecisaoMediacao.NEGAR, AGORA);

        assertThatThrownBy(() -> mediacao.registrarDecisao(DecisaoMediacao.CANCELAR_REEMBOLSAR, AGORA))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void naoVoltaParaAnaliseDepoisDeDecidida() {
        var mediacao = novaMediacao();
        mediacao.registrarDecisao(DecisaoMediacao.NEGAR, AGORA);

        assertThatThrownBy(() -> mediacao.iniciarAnalise(AGORA)).isInstanceOf(ConflitoException.class);
    }

    @Test
    void falhaNaEntregaContaTentativaEConfirmacaoZeraContador() {
        var mediacao = novaMediacao();
        mediacao.registrarDecisao(DecisaoMediacao.MANTER_CONVERSA, AGORA);

        mediacao.registrarFalhaTemporariaNaEntrega("ResourceAccessException", AGORA);
        assertThat(mediacao.tentativasEntrega()).isEqualTo(1);
        assertThat(mediacao.aguardaEntregaDaDecisao()).isTrue();

        mediacao.confirmarEntregaDaDecisao(AGORA);
        assertThat(mediacao.estado()).isEqualTo(EstadoMediacao.DECIDIDA);
        assertThat(mediacao.tentativasEntrega()).isZero();
        assertThat(mediacao.ultimoErroEntrega()).isNull();
    }

    @Test
    void decimaFalhaTemporariaEncerraAsTentativasAutomaticas() {
        var mediacao = novaMediacao();
        mediacao.registrarDecisao(DecisaoMediacao.NEGAR, AGORA);

        for (int falha = 1; falha < Mediacao.MAXIMO_TENTATIVAS_ENTREGA; falha++) {
            mediacao.registrarFalhaTemporariaNaEntrega("HTTP 503", AGORA);
        }
        assertThat(mediacao.aguardaEntregaDaDecisao()).isTrue();

        mediacao.registrarFalhaTemporariaNaEntrega("HTTP 503", AGORA);
        assertThat(mediacao.estado()).isEqualTo(EstadoMediacao.FALHA_ENTREGA);
        assertThat(mediacao.aguardaEntregaDaDecisao()).isFalse();
    }

    @Test
    void reaberturaDaEntregaPreservaDecisaoEIdentificador() {
        var mediacao = novaMediacao();
        mediacao.registrarDecisao(DecisaoMediacao.CANCELAR_REEMBOLSAR, AGORA);
        var decisaoId = mediacao.decisaoId();
        mediacao.registrarFalhaDefinitivaNaEntrega("HTTP 409", AGORA);

        mediacao.reabrirEntregaDaDecisao(AGORA);

        assertThat(mediacao.estado()).isEqualTo(EstadoMediacao.DECISAO_PENDENTE);
        assertThat(mediacao.tentativasEntrega()).isZero();
        assertThat(mediacao.decisaoId()).isEqualTo(decisaoId);
        assertThat(mediacao.decisao()).isEqualTo(DecisaoMediacao.CANCELAR_REEMBOLSAR);
    }

    @Test
    void mediacaoEmFalhaNaoVoltaParaAnalise() {
        var mediacao = novaMediacao();
        mediacao.registrarDecisao(DecisaoMediacao.NEGAR, AGORA);
        mediacao.registrarFalhaDefinitivaNaEntrega("HTTP 409", AGORA);

        assertThatThrownBy(() -> mediacao.iniciarAnalise(AGORA)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> mediacao.registrarDecisao(DecisaoMediacao.CANCELAR_REEMBOLSAR, AGORA))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void cancelaMediacaoAbertaRegistrandoAutorEMomento() {
        var mediacao = novaMediacao();
        UUID autor = UUID.randomUUID();

        mediacao.cancelar(autor, AGORA.plusHours(1));

        assertThat(mediacao.estado()).isEqualTo(EstadoMediacao.CANCELADA);
        assertThat(mediacao.estaCancelada()).isTrue();
        assertThat(mediacao.canceladaPor()).isEqualTo(autor);
        assertThat(mediacao.canceladaEm()).isEqualTo(AGORA.plusHours(1));
        assertThat(mediacao.atualizadaEm()).isEqualTo(AGORA.plusHours(1));
    }

    @Test
    void cancelaMediacaoEmAnalise() {
        var mediacao = novaMediacao();
        mediacao.iniciarAnalise(AGORA);

        mediacao.cancelar(UUID.randomUUID(), AGORA);

        assertThat(mediacao.estado()).isEqualTo(EstadoMediacao.CANCELADA);
    }

    @Test
    void cancelarDeNovoNaoAlteraAutorNemMomento() {
        var mediacao = novaMediacao();
        UUID primeiroAutor = UUID.randomUUID();
        mediacao.cancelar(primeiroAutor, AGORA);

        mediacao.cancelar(UUID.randomUUID(), AGORA.plusDays(1));

        assertThat(mediacao.canceladaPor()).isEqualTo(primeiroAutor);
        assertThat(mediacao.canceladaEm()).isEqualTo(AGORA);
    }

    @Test
    void naoCancelaDepoisDaDecisao() {
        var pendente = novaMediacao();
        pendente.registrarDecisao(DecisaoMediacao.NEGAR, AGORA);
        var decidida = novaMediacao();
        decidida.registrarDecisao(DecisaoMediacao.NEGAR, AGORA);
        decidida.confirmarEntregaDaDecisao(AGORA);
        var emFalha = novaMediacao();
        emFalha.registrarDecisao(DecisaoMediacao.NEGAR, AGORA);
        emFalha.registrarFalhaDefinitivaNaEntrega("HTTP 409", AGORA);

        for (var mediacao : new Mediacao[] { pendente, decidida, emFalha }) {
            var estadoAnterior = mediacao.estado();
            assertThatThrownBy(() -> mediacao.cancelar(UUID.randomUUID(), AGORA))
                    .isInstanceOf(ConflitoException.class);
            assertThat(mediacao.estado()).isEqualTo(estadoAnterior);
            assertThat(mediacao.canceladaPor()).isNull();
        }
    }

    @Test
    void mediacaoCanceladaNaoVaiParaAnaliseNemRecebeDecisao() {
        var mediacao = novaMediacao();
        mediacao.cancelar(UUID.randomUUID(), AGORA);

        assertThatThrownBy(() -> mediacao.iniciarAnalise(AGORA)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> mediacao.registrarDecisao(DecisaoMediacao.NEGAR, AGORA))
                .isInstanceOf(ConflitoException.class);
        assertThat(mediacao.estado()).isEqualTo(EstadoMediacao.CANCELADA);
        assertThat(mediacao.decisaoId()).isNull();
    }

    @Test
    void identificaQuemAbriuAMediacao() {
        UUID comprador = UUID.randomUUID();
        var mediacao = Mediacao.abrir(UUID.randomUUID(), comprador, "Assunto", "Relato", AGORA);

        assertThat(mediacao.abertaPor(comprador)).isTrue();
        assertThat(mediacao.abertaPor(UUID.randomUUID())).isFalse();
    }

    @Test
    void codigoDeDecisaoDesconhecidoEhRequisicaoInvalida() {
        assertThatThrownBy(() -> DecisaoMediacao.deCodigo("reembolsar_tudo"))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    private static Mediacao novaMediacao() {
        return Mediacao.abrir(UUID.randomUUID(), UUID.randomUUID(), "  Peça quebrada ", " Chegou trincada. ", AGORA);
    }
}
