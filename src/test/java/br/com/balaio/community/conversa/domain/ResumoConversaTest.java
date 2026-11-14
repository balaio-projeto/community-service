package br.com.balaio.community.conversa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

class ResumoConversaTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 14, 12, 0, 0, 0, ZoneOffset.UTC);
    private final UUID comprador = UUID.randomUUID();
    private final UUID artesao = UUID.randomUUID();
    private final PedidoDaConversa pedido = new PedidoDaConversa(UUID.randomUUID(), "Ana", "14/11/2026");

    @Test
    void mensagemDoOutroLadoDepoisDaUltimaLeituraFicaNaoLida() {
        var mensagem = Mensagem.enviar(pedido.id(), comprador, ParteDaConversa.COMPRADOR, "Oi", AGORA);

        assertThat(ResumoConversa.para(artesao, pedido, Optional.of(mensagem), Optional.empty()).naoLida()).isTrue();
        assertThat(ResumoConversa.para(artesao, pedido, Optional.of(mensagem), Optional.of(AGORA.plusSeconds(1)))
                .naoLida()).isFalse();
    }

    @Test
    void propriaMensagemNuncaFicaNaoLida() {
        var mensagem = Mensagem.enviar(pedido.id(), artesao, ParteDaConversa.ARTESAO, "Enviado!", AGORA);

        assertThat(ResumoConversa.para(artesao, pedido, Optional.of(mensagem), Optional.empty()).naoLida()).isFalse();
    }

    @Test
    void mensagemVaziaEhInvalida() {
        assertThatThrownBy(() -> Mensagem.enviar(pedido.id(), comprador, ParteDaConversa.COMPRADOR, " ", AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class).hasMessage("Informe o texto.");
    }
}
