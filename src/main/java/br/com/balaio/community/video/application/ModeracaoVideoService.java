package br.com.balaio.community.video.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;
import br.com.balaio.community.shared.servicos.NotificacoesPort;
import br.com.balaio.community.shared.servicos.NotificacoesPort.Notificacao;
import br.com.balaio.community.video.application.port.out.VideosPort;
import br.com.balaio.community.video.domain.FiltroModeracaoVideo;
import br.com.balaio.community.video.domain.SituacaoVideo;
import br.com.balaio.community.video.domain.SuspensaoVideo;
import br.com.balaio.community.video.domain.Video;

@Service
public class ModeracaoVideoService {
    static final String TIPO_SUSPENSO = "video_suspenso";
    static final String TIPO_REATIVADO = "video_reativado";
    static final int TAMANHO_MAXIMO_MENSAGEM = 500;
    private static final int TAMANHO_TRECHO_LEGENDA = 80;
    private static final String RETICENCIAS = "…";
    private static final Logger LOG = LoggerFactory.getLogger(ModeracaoVideoService.class);

    private final VideosPort videos;
    private final NotificacoesPort notificacoes;
    private final TransactionOperations transacao;
    private final Clock relogio;

    public ModeracaoVideoService(VideosPort videos, NotificacoesPort notificacoes, TransactionOperations transacao,
            Clock relogio) {
        this.videos = videos;
        this.notificacoes = notificacoes;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<Video> listar(FiltroModeracaoVideo filtro, Pagina pagina, UsuarioAutenticado administrador) {
        administrador.exigirPapel(Papel.ADMIN);
        return videos.listarParaModeracao(filtro, pagina);
    }

    public Video suspender(UUID id, String motivo, UsuarioAutenticado administrador) {
        administrador.exigirPapel(Papel.ADMIN);
        var suspenso = transacao.execute(status -> {
            var video = existente(id);
            video.suspender(motivo, administrador.id(), OffsetDateTime.now(relogio));
            return videos.salvar(video);
        });
        notificarSemInterromper(avisoDeSuspensao(suspenso));
        return suspenso;
    }

    public Video reativar(UUID id, UsuarioAutenticado administrador) {
        administrador.exigirPapel(Papel.ADMIN);
        var reativacao = transacao.execute(status -> {
            var video = existente(id);
            var encerrada = video.reativar();
            return new Reativacao(videos.salvar(video), encerrada);
        });
        notificarSemInterromper(avisoDeReativacao(reativacao.video(), reativacao.suspensaoEncerrada()));
        return reativacao.video();
    }

    private Video existente(UUID id) {
        return videos.buscar(id).orElseThrow(VideoService::naoEncontrado);
    }

    private void notificarSemInterromper(Notificacao notificacao) {
        try {
            notificacoes.notificar(notificacao);
        } catch (RuntimeException falha) {
            LOG.warn("Notificação {} não enviada; a moderação do vídeo foi mantida.", notificacao.chave(), falha);
        }
    }

    static Notificacao avisoDeSuspensao(Video video) {
        var suspensao = video.suspensao();
        String mensagem = "Seu vídeo \"" + trechoDaLegenda(video) + "\" foi retirado do ar pela administração. "
                + "Motivo: " + suspensao.motivo();
        return new Notificacao(video.artesaoId(), TIPO_SUSPENSO, "Vídeo suspenso", abreviar(mensagem,
                TAMANHO_MAXIMO_MENSAGEM), linkDoPainel(video), chave(video, "suspenso", suspensao));
    }

    static Notificacao avisoDeReativacao(Video video, SuspensaoVideo encerrada) {
        String complemento = video.situacao() == SituacaoVideo.PUBLICADA
                ? " Ele já aparece de novo no Ateliê ao vivo."
                : " Publique quando quiser que ele apareça no Ateliê ao vivo.";
        String mensagem = "Seu vídeo \"" + trechoDaLegenda(video) + "\" foi reativado pela administração."
                + complemento;
        return new Notificacao(video.artesaoId(), TIPO_REATIVADO, "Vídeo reativado", abreviar(mensagem,
                TAMANHO_MAXIMO_MENSAGEM), linkDoPainel(video), chave(video, "reativado", encerrada));
    }

    private static String linkDoPainel(Video video) {
        return "/dashboard/videos/" + video.id();
    }

    private static String chave(Video video, String evento, SuspensaoVideo suspensao) {
        return "video:" + video.id() + ":" + evento + ":" + suspensao.suspensoEm().toInstant().toEpochMilli();
    }

    private static String trechoDaLegenda(Video video) {
        return abreviar(video.legenda(), TAMANHO_TRECHO_LEGENDA);
    }

    static String abreviar(String texto, int limite) {
        if (texto.length() <= limite) {
            return texto;
        }
        return texto.substring(0, limite - RETICENCIAS.length()).stripTrailing() + RETICENCIAS;
    }

    private record Reativacao(Video video, SuspensaoVideo suspensaoEncerrada) {
    }
}
