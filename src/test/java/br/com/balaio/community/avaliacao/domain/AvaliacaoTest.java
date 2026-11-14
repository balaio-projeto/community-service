package br.com.balaio.community.avaliacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

class AvaliacaoTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 14, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void pedidoEntregueGeraAvaliacaoComDadosNormalizados() {
        var avaliacao = Avaliacao.registrar(pedido("entregue"), 5, " Linda! ",
                List.of("Embalagem", " Embalagem ", "Prazo"), AGORA);

        assertThat(avaliacao.comentario()).isEqualTo("Linda!");
        assertThat(avaliacao.aspectos()).containsExactly("Embalagem", "Prazo");
    }

    @Test
    void pedidoNaoEntregueNaoPodeSerAvaliado() {
        assertThatThrownBy(() -> Avaliacao.registrar(pedido("enviado"), 5, null, null, AGORA))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void notaPrecisaEstarEntreUmECinco() {
        assertThatThrownBy(() -> Avaliacao.registrar(pedido("entregue"), 0, null, null, AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class).hasMessage("Escolha uma nota entre 1 e 5.");
        assertThatThrownBy(() -> Avaliacao.registrar(pedido("entregue"), null, null, null, AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void reputacaoCalculaMediaComUmaCasaEDistribuicao() {
        var reputacao = Reputacao.das(List.of(5, 4, 4));

        assertThat(reputacao.media()).isEqualByComparingTo(new BigDecimal("4.3"));
        assertThat(reputacao.total()).isEqualTo(3);
        assertThat(reputacao.distribuicao()).containsEntry(4, 2L).containsEntry(1, 0L);
    }

    @Test
    void artesaoSemAvaliacoesNaoTemMedia() {
        assertThat(Reputacao.das(List.of()).media()).isNull();
    }

    private static PedidoAvaliavel pedido(String estado) {
        return new PedidoAvaliavel(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "vaso", estado);
    }
}
