package br.com.balaio.community.pergunta.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

class PerguntaTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 14, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void criaPerguntaSemRespostaComTextoNormalizado() {
        var pergunta = Pergunta.fazer("vaso-de-barro", UUID.randomUUID(), " Ana ", "  Serve para planta? ", AGORA);

        assertThat(pergunta.texto()).isEqualTo("Serve para planta?");
        assertThat(pergunta.autorNome()).isEqualTo("Ana");
        assertThat(pergunta.resposta()).isNull();
        assertThat(pergunta.pertenceAPeca("vaso-de-barro")).isTrue();
    }

    @Test
    void recusaPerguntaVaziaOuLongaDemais() {
        assertThatThrownBy(() -> Pergunta.fazer("vaso", UUID.randomUUID(), "Ana", "   ", AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class).hasMessage("Escreva sua pergunta.");
        String longa = "x".repeat(Pergunta.TAMANHO_MAXIMO_PERGUNTA + 1);
        assertThatThrownBy(() -> Pergunta.fazer("vaso", UUID.randomUUID(), "Ana", longa, AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void autorSemNomeApareceComoComprador() {
        assertThat(Pergunta.fazer("vaso", UUID.randomUUID(), "", "Pergunta?", AGORA).autorNome())
                .isEqualTo("Comprador");
    }

    @Test
    void respostaRegistraDataERecusaTextoVazio() {
        var pergunta = Pergunta.fazer("vaso", UUID.randomUUID(), "Ana", "Pergunta?", AGORA);

        assertThatThrownBy(() -> pergunta.responder(" ", AGORA)).hasMessage("Escreva a resposta.");
        pergunta.responder(" Sim, serve. ", AGORA.plusHours(1));

        assertThat(pergunta.resposta()).isEqualTo("Sim, serve.");
        assertThat(pergunta.respondidaEm()).isEqualTo(AGORA.plusHours(1));
    }
}
