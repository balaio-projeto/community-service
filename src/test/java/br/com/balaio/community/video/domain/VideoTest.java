package br.com.balaio.community.video.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

class VideoTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 14, 12, 0, 0, 0, ZoneOffset.UTC);
    private final UUID dono = UUID.randomUUID();

    @Test
    void nasceRascunhoVisivelSoAoDono() {
        var video = novo();

        assertThat(video.situacao()).isEqualTo(SituacaoVideo.RASCUNHO);
        assertThat(video.visivelPara(dono)).isTrue();
        assertThat(video.visivelPara(UUID.randomUUID())).isFalse();
        assertThat(video.visivelPara(null)).isFalse();
    }

    @Test
    void naoPublicaSemArquivo() {
        var video = novo();

        assertThatThrownBy(() -> video.mudarSituacao(SituacaoVideo.PUBLICADA, AGORA))
                .isInstanceOf(ConflitoException.class).hasMessageContaining("arquivo");
    }

    @Test
    void publicaDepoisDoArquivoERegistraData() {
        var video = novo();
        video.anexarArquivo(ArquivoMidia.novo(video.id(), TipoMidia.VIDEO, "video/mp4", 1024), "0:48");

        video.mudarSituacao(SituacaoVideo.PUBLICADA, AGORA);

        assertThat(video.visivelPara(null)).isTrue();
        assertThat(video.publicadoEm()).isEqualTo(AGORA);
        assertThat(video.duracao()).isEqualTo("0:48");
    }

    @Test
    void normalizaEtiquetasERecusaInvalidas() {
        var video = Video.rascunho(dono, null, "Legenda", List.of("Ceramica", "ceramica", " passo-a-passo "), null,
                AGORA);

        assertThat(video.etiquetas()).containsExactly("ceramica", "passo-a-passo");
        assertThatThrownBy(() -> video.alterarDescricao(null, List.of("com espaço"), null))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void arquivoAceitaSoFormatosETamanhosPermitidos() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> ArquivoMidia.novo(id, TipoMidia.VIDEO, "video/avi", 10))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> ArquivoMidia.novo(id, TipoMidia.CAPA, "image/png", TipoMidia.CAPA.tamanhoMaximoBytes() + 1))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThat(ArquivoMidia.novo(id, TipoMidia.VIDEO, "video/webm; codecs=vp9", -1).chave()).endsWith(".webm");
    }

    private Video novo() {
        return Video.rascunho(dono, "vaso", "Como faço a asa do vaso", List.of("ceramica"), null, AGORA);
    }
}
