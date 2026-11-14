package br.com.balaio.community.video.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.balaio.community.video.application.port.out.VideosPort;
import br.com.balaio.community.video.domain.SituacaoVideo;
import br.com.balaio.community.video.domain.Video;

@Component
class VideosPersistenceAdapter implements VideosPort {
    private final SpringDataVideoRepository repositorio;

    VideosPersistenceAdapter(SpringDataVideoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Video salvar(Video video) {
        var entidade = repositorio.findById(video.id()).orElseGet(() -> VideoJpaEntity.de(video));
        entidade.copiarDe(video);
        return repositorio.saveAndFlush(entidade).paraDominio();
    }

    @Override
    public Optional<Video> buscar(UUID id) {
        return repositorio.findById(id).map(VideoJpaEntity::paraDominio);
    }

    @Override
    public List<Video> listarPublicados() {
        return repositorio.findBySituacaoOrderByPublicadoEmDesc(SituacaoVideo.PUBLICADA.codigo()).stream()
                .map(VideoJpaEntity::paraDominio).toList();
    }

    @Override
    public List<Video> listarDoArtesao(UUID artesaoId) {
        return repositorio.findByArtesaoIdOrderByCriadoEmDesc(artesaoId).stream()
                .map(VideoJpaEntity::paraDominio).toList();
    }

    @Override
    public void remover(UUID id) {
        repositorio.deleteById(id);
        repositorio.flush();
    }
}
