package br.com.balaio.community.video.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface InteracoesVideoPort {
    Map<UUID, Long> curtidasPorVideo(List<UUID> videoIds);

    void esquecerVideo(UUID videoId);
}
