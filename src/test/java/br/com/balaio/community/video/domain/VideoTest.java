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

    @Test
    void suspensaoTiraDoPublicoMasMantemVisivelAoDono() {
        var video = publicado();
        UUID administrador = UUID.randomUUID();

        var suspensao = video.suspender("  Conteúdo de terceiros sem autorização  ", administrador, AGORA);

        assertThat(suspensao).isEqualTo(new SuspensaoVideo("Conteúdo de terceiros sem autorização", administrador,
                AGORA));
        assertThat(video.suspenso()).isTrue();
        assertThat(video.situacao()).isEqualTo(SituacaoVideo.PUBLICADA);
        assertThat(video.visivelAoPublico()).isFalse();
        assertThat(video.visivelPara(null)).isFalse();
        assertThat(video.visivelPara(UUID.randomUUID())).isFalse();
        assertThat(video.visivelPara(dono)).isTrue();
        assertThat(video.contaVisualizacaoDe(UUID.randomUUID())).isFalse();
    }

    @Test
    void donoEditaVideoSuspensoSemTirarASuspensao() {
        var video = publicado();
        video.suspender("Conteúdo de terceiros", UUID.randomUUID(), AGORA);

        video.alterarDescricao("Nova legenda", List.of("barro"), null);
        video.mudarSituacao(SituacaoVideo.RASCUNHO, AGORA);
        video.mudarSituacao(SituacaoVideo.PUBLICADA, AGORA);

        assertThat(video.legenda()).isEqualTo("Nova legenda");
        assertThat(video.suspenso()).isTrue();
        assertThat(video.visivelAoPublico()).isFalse();
    }

    @Test
    void motivoDaSuspensaoTemDeDezAMilCaracteres() {
        var video = publicado();
        UUID administrador = UUID.randomUUID();

        assertThatThrownBy(() -> video.suspender(null, administrador, AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class).hasMessageContaining("10 a 1000");
        assertThatThrownBy(() -> video.suspender("   curto   ", administrador, AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> video.suspender("a".repeat(1001), administrador, AGORA))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThat(video.suspenso()).isFalse();
        assertThat(video.suspender("a".repeat(1000), administrador, AGORA).motivo()).hasSize(1000);
    }

    @Test
    void naoSuspendeDuasVezesNemReativaQuemNaoEstaSuspenso() {
        var video = publicado();

        assertThatThrownBy(video::reativar).isInstanceOf(ConflitoException.class).hasMessageContaining("não está");
        video.suspender("Conteúdo de terceiros", UUID.randomUUID(), AGORA);
        assertThatThrownBy(() -> video.suspender("Outro motivo qualquer", UUID.randomUUID(), AGORA))
                .isInstanceOf(ConflitoException.class).hasMessageContaining("já está suspenso");
    }

    @Test
    void reativacaoDevolveOVideoAoPublicoEInformaASuspensaoEncerrada() {
        var video = publicado();
        var suspensao = video.suspender("Conteúdo de terceiros", UUID.randomUUID(), AGORA);

        assertThat(video.reativar()).isEqualTo(suspensao);
        assertThat(video.suspenso()).isFalse();
        assertThat(video.suspensao()).isNull();
        assertThat(video.visivelPara(null)).isTrue();
    }

    @Test
    void filtroDeModeracaoSeparaPublicadosESuspensos() {
        var publicado = publicado();
        var suspenso = publicado();
        suspenso.suspender("Conteúdo de terceiros", UUID.randomUUID(), AGORA);
        var rascunho = novo();

        assertThat(FiltroModeracaoVideo.deCodigo(null)).isEqualTo(FiltroModeracaoVideo.TODOS);
        assertThat(FiltroModeracaoVideo.deCodigo("suspensa")).isEqualTo(FiltroModeracaoVideo.SUSPENSOS);
        assertThatThrownBy(() -> FiltroModeracaoVideo.deCodigo("rascunho"))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThat(List.of(publicado, suspenso, rascunho)).filteredOn(FiltroModeracaoVideo.TODOS::aceita)
                .containsExactly(publicado, suspenso);
        assertThat(List.of(publicado, suspenso, rascunho)).filteredOn(FiltroModeracaoVideo.PUBLICADOS::aceita)
                .containsExactly(publicado);
        assertThat(List.of(publicado, suspenso, rascunho)).filteredOn(FiltroModeracaoVideo.SUSPENSOS::aceita)
                .containsExactly(suspenso);
    }

    private Video publicado() {
        var video = novo();
        video.anexarArquivo(ArquivoMidia.novo(video.id(), TipoMidia.VIDEO, "video/mp4", 1024), "0:48");
        video.mudarSituacao(SituacaoVideo.PUBLICADA, AGORA);
        return video;
    }

    private Video novo() {
        return Video.rascunho(dono, "vaso", "Como faço a asa do vaso", List.of("ceramica"), null, AGORA);
    }
}
