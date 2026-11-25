package br.com.balaio.community.video.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;
import br.com.balaio.community.shared.servicos.NotificacoesPort;
import br.com.balaio.community.video.application.port.out.VideosPort;
import br.com.balaio.community.video.domain.ArquivoMidia;
import br.com.balaio.community.video.domain.FiltroModeracaoVideo;
import br.com.balaio.community.video.domain.SituacaoVideo;
import br.com.balaio.community.video.domain.TipoMidia;
import br.com.balaio.community.video.domain.Video;

class ModeracaoVideoServiceTest {
    private static final Instant INSTANTE = Instant.parse("2026-11-25T15:30:00Z");
    private static final Clock RELOGIO = Clock.fixed(INSTANTE, ZoneOffset.UTC);
    private static final String MOTIVO = "Vídeo usa conteúdo de terceiros sem autorização.";

    private final UsuarioAutenticado administrador = new UsuarioAutenticado(UUID.randomUUID(), Papel.ADMIN, "adm");
    private final UsuarioAutenticado artesao = new UsuarioAutenticado(UUID.randomUUID(), Papel.ARTESAO, "a");

    private VideosEmMemoria videos;
    private NotificacoesGravadas notificacoes;
    private ModeracaoVideoService moderacao;

    @BeforeEach
    void preparar() {
        videos = new VideosEmMemoria();
        notificacoes = new NotificacoesGravadas();
        moderacao = new ModeracaoVideoService(videos, notificacoes, TransactionOperations.withoutTransaction(),
                RELOGIO);
    }

    @Test
    void suspensaoRegistraQuemQuandoEPorQueEAvisaODono() {
        var video = publicado("Como faço a asa do vaso");

        var suspenso = moderacao.suspender(video.id(), "  " + MOTIVO + "  ", administrador);

        assertThat(suspenso.suspensao().motivo()).isEqualTo(MOTIVO);
        assertThat(suspenso.suspensao().suspensoPor()).isEqualTo(administrador.id());
        assertThat(suspenso.suspensao().suspensoEm()).isEqualTo(OffsetDateTime.ofInstant(INSTANTE, ZoneOffset.UTC));
        assertThat(videos.buscar(video.id()).orElseThrow().suspenso()).isTrue();
        assertThat(notificacoes.enviadas).singleElement().satisfies(aviso -> {
            assertThat(aviso.conta()).isEqualTo(artesao.id());
            assertThat(aviso.tipo()).isEqualTo("video_suspenso").matches("[a-z_]{3,40}");
            assertThat(aviso.titulo()).isEqualTo("Vídeo suspenso");
            assertThat(aviso.mensagem()).contains("\"Como faço a asa do vaso\"").endsWith("Motivo: " + MOTIVO);
            assertThat(aviso.link()).isEqualTo("/dashboard/videos/" + video.id());
            assertThat(aviso.chave()).isEqualTo("video:" + video.id() + ":suspenso:" + INSTANTE.toEpochMilli());
        });
    }

    @Test
    void reativacaoDevolveAoPublicoEAvisaComChaveDaSuspensaoEncerrada() {
        var video = publicado("Como faço a asa do vaso");
        moderacao.suspender(video.id(), MOTIVO, administrador);

        var reativado = moderacao.reativar(video.id(), administrador);

        assertThat(reativado.suspenso()).isFalse();
        assertThat(reativado.visivelAoPublico()).isTrue();
        assertThat(notificacoes.enviadas).hasSize(2).last().satisfies(aviso -> {
            assertThat(aviso.tipo()).isEqualTo("video_reativado");
            assertThat(aviso.titulo()).isEqualTo("Vídeo reativado");
            assertThat(aviso.mensagem()).contains("reativado").contains("Ateliê ao vivo");
            assertThat(aviso.chave()).isEqualTo("video:" + video.id() + ":reativado:" + INSTANTE.toEpochMilli());
        });
    }

    @Test
    void falhaAoNotificarNaoDesfazASuspensao() {
        var video = publicado("Legenda");
        notificacoes.falhar = true;

        var suspenso = moderacao.suspender(video.id(), MOTIVO, administrador);

        assertThat(suspenso.suspenso()).isTrue();
        assertThat(videos.buscar(video.id()).orElseThrow().suspenso()).isTrue();
    }

    @Test
    void mensagemLongaEhAbreviadaNoLimiteDoPerfil() {
        var video = publicado("L".repeat(500));

        moderacao.suspender(video.id(), "m".repeat(1000), administrador);

        var mensagem = notificacoes.enviadas.getFirst().mensagem();
        assertThat(mensagem).hasSizeLessThanOrEqualTo(ModeracaoVideoService.TAMANHO_MAXIMO_MENSAGEM).endsWith("…");
        assertThat(mensagem).contains("L".repeat(79) + "…\"");
    }

    @Test
    void soAdministradorModera() {
        var video = publicado("Legenda");

        assertThatThrownBy(() -> moderacao.suspender(video.id(), MOTIVO, artesao))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> moderacao.reativar(video.id(), artesao))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> moderacao.listar(FiltroModeracaoVideo.TODOS, Pagina.primeira(), artesao))
                .isInstanceOf(AcessoNegadoException.class);
        assertThat(videos.buscar(video.id()).orElseThrow().suspenso()).isFalse();
        assertThat(notificacoes.enviadas).isEmpty();
    }

    @Test
    void motivoInvalidoOuEstadoErradoNaoNotificam() {
        var video = publicado("Legenda");

        assertThatThrownBy(() -> moderacao.suspender(video.id(), "curto", administrador))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> moderacao.reativar(video.id(), administrador))
                .isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> moderacao.suspender(UUID.randomUUID(), MOTIVO, administrador))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(notificacoes.enviadas).isEmpty();
    }

    @Test
    void listagemFiltraPorEstado() {
        var publicado = publicado("Publicado");
        var suspenso = publicado("Suspenso");
        moderacao.suspender(suspenso.id(), MOTIVO, administrador);
        videos.salvar(Video.rascunho(artesao.id(), null, "Rascunho", List.of(), null,
                OffsetDateTime.now(RELOGIO)));

        assertThat(moderacao.listar(FiltroModeracaoVideo.TODOS, Pagina.primeira(), administrador))
                .extracting(Video::id).containsExactly(publicado.id(), suspenso.id());
        assertThat(moderacao.listar(FiltroModeracaoVideo.SUSPENSOS, Pagina.primeira(), administrador))
                .extracting(Video::id).containsExactly(suspenso.id());
        assertThat(moderacao.listar(FiltroModeracaoVideo.PUBLICADOS, Pagina.primeira(), administrador))
                .extracting(Video::id).containsExactly(publicado.id());
    }

    private Video publicado(String legenda) {
        var agora = OffsetDateTime.now(RELOGIO);
        var video = Video.rascunho(artesao.id(), null, legenda, List.of(), null, agora);
        video.anexarArquivo(ArquivoMidia.novo(video.id(), TipoMidia.VIDEO, "video/mp4", 10), null);
        video.mudarSituacao(SituacaoVideo.PUBLICADA, agora);
        return videos.salvar(video);
    }

    private static final class NotificacoesGravadas implements NotificacoesPort {
        private final List<Notificacao> enviadas = new ArrayList<>();
        private boolean falhar;

        @Override
        public void notificar(Notificacao notificacao) {
            if (falhar) {
                throw new IllegalStateException("perfil fora do ar");
            }
            enviadas.add(notificacao);
        }
    }

    private static final class VideosEmMemoria implements VideosPort {
        private final Map<UUID, Video> armazenados = new LinkedHashMap<>();

        @Override
        public Video salvar(Video video) {
            armazenados.put(video.id(), video);
            return copia(video);
        }

        @Override
        public Optional<Video> buscar(UUID id) {
            return Optional.ofNullable(armazenados.get(id)).map(VideosEmMemoria::copia);
        }

        @Override
        public List<Video> listarPublicados(Pagina pagina) {
            return listarParaModeracao(FiltroModeracaoVideo.PUBLICADOS, pagina);
        }

        @Override
        public List<Video> listarDoArtesao(UUID artesaoId, Pagina pagina) {
            return armazenados.values().stream().filter(video -> video.pertenceA(artesaoId)).toList();
        }

        @Override
        public List<Video> listarParaModeracao(FiltroModeracaoVideo filtro, Pagina pagina) {
            return armazenados.values().stream().filter(filtro::aceita).map(VideosEmMemoria::copia).toList();
        }

        @Override
        public void remover(UUID id) {
            armazenados.remove(id);
        }

        @Override
        public void registrarVisualizacao(UUID id) {
        }

        private static Video copia(Video video) {
            return Video.reconstituir(new Video.Dados(video.id(), video.artesaoId(), video.criadoEm(),
                    video.pecaSlug(), video.legenda(), video.etiquetas(), video.duracao(), video.situacao(),
                    video.arquivo(), video.capa(), video.publicadoEm(), video.visualizacoes(), video.suspensao()));
        }
    }
}
