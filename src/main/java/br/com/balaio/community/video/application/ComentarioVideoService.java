package br.com.balaio.community.video.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.balaio.community.shared.security.UsuarioAutenticado;
import br.com.balaio.community.shared.servicos.PerfilPort;
import br.com.balaio.community.video.application.port.out.ComentariosVideoPort;
import br.com.balaio.community.video.domain.ComentarioVideo;

@Service
public class ComentarioVideoService {
    private final VideoService videos;
    private final ComentariosVideoPort comentarios;
    private final PerfilPort perfis;
    private final Clock relogio;

    public ComentarioVideoService(VideoService videos, ComentariosVideoPort comentarios, PerfilPort perfis,
            Clock relogio) {
        this.videos = videos;
        this.comentarios = comentarios;
        this.perfis = perfis;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<ComentarioVideo> listar(UUID videoId, Optional<UsuarioAutenticado> leitor) {
        videos.visivel(videoId, leitor);
        return comentarios.listarDoVideo(videoId);
    }

    @Transactional
    public ComentarioVideo comentar(UUID videoId, String texto, UsuarioAutenticado autor) {
        var video = videos.visivel(videoId, Optional.of(autor));
        String nome = perfis.nomeDoUsuario(autor.token());
        return comentarios.salvar(ComentarioVideo.comentar(video, autor.id(), nome, texto,
                OffsetDateTime.now(relogio)));
    }
}
