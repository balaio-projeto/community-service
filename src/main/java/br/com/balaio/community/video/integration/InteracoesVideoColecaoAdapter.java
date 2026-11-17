package br.com.balaio.community.video.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.balaio.community.colecao.application.ColecaoService;
import br.com.balaio.community.colecao.domain.TipoColecao;
import br.com.balaio.community.video.application.port.out.InteracoesVideoPort;

@Component
class InteracoesVideoColecaoAdapter implements InteracoesVideoPort {
    private final ColecaoService colecoes;

    InteracoesVideoColecaoAdapter(ColecaoService colecoes) {
        this.colecoes = colecoes;
    }

    @Override
    public Map<UUID, Long> curtidasPorVideo(List<UUID> videoIds) {
        var porItem = colecoes.quantosUsuariosTem(TipoColecao.VIDEOS_CURTIDOS,
                videoIds.stream().map(UUID::toString).toList());
        var porVideo = new HashMap<UUID, Long>();
        videoIds.forEach(id -> porVideo.put(id, porItem.getOrDefault(id.toString(), 0L)));
        return porVideo;
    }

    @Override
    public void esquecerVideo(UUID videoId) {
        colecoes.esquecerItem(videoId.toString(), TipoColecao.VIDEOS_CURTIDOS, TipoColecao.VIDEOS_SALVOS);
    }
}
