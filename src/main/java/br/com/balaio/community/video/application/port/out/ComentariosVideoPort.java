package br.com.balaio.community.video.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.balaio.community.video.domain.ComentarioVideo;

public interface ComentariosVideoPort {
    ComentarioVideo salvar(ComentarioVideo comentario);

    List<ComentarioVideo> listarDoVideo(UUID videoId);

    Map<UUID, Long> contarPorVideo(Collection<UUID> videoIds);

    void removerDoVideo(UUID videoId);
}
