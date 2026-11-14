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

        mediacao.registrarFalhaNaEntrega("ResourceAccessException", AGORA);
        assertThat(mediacao.tentativasEntrega()).isEqualTo(1);
        assertThat(mediacao.aguardaEntregaDaDecisao()).isTrue();

        mediacao.confirmarEntregaDaDecisao(AGORA);
        assertThat(mediacao.estado()).isEqualTo(EstadoMediacao.DECIDIDA);
        assertThat(mediacao.tentativasEntrega()).isZero();
        assertThat(mediacao.ultimoErroEntrega()).isNull();
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
