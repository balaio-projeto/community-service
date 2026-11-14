package br.com.balaio.community.video.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.balaio.community.video.domain.Video;

public interface VideosPort {
    Video salvar(Video video);

    Optional<Video> buscar(UUID id);

    List<Video> listarPublicados();

    List<Video> listarDoArtesao(UUID artesaoId);

    void remover(UUID id);
}
