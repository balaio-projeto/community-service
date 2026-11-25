package br.com.balaio.community.video.api;

import java.util.List;
import java.util.UUID;

import br.com.balaio.community.shared.security.UsuarioAutenticado;
import br.com.balaio.community.video.domain.Video;

public record VideoModeracaoResponse(UUID id, String artesao, String peca, String legenda, List<String> etiquetas,
        String duracao, long visualizacoes, String criadoEm, String publicadoEm, String capa, String url,
        String situacao, String estado, SuspensaoResponse suspensao, UUID suspensoPor) {

    static final String ESTADO_SUSPENSO = "suspensa";

    static VideoModeracaoResponse de(Video video) {
        String base = "/api/v1/videos/" + video.id();
        var suspensao = video.suspensao();
        return new VideoModeracaoResponse(video.id(), UsuarioAutenticado.PREFIXO_SLUG_ARTESAO + video.artesaoId(),
                video.pecaSlug(), video.legenda(), video.etiquetas(), video.duracao(), video.visualizacoes(),
                video.criadoEm().toString(), video.publicadoEm() == null ? null : video.publicadoEm().toString(),
                video.capa() == null ? null : base + "/capa",
                video.arquivo() == null ? null : base + "/arquivo",
                video.situacao().codigo(),
                video.suspenso() ? ESTADO_SUSPENSO : video.situacao().codigo(),
                SuspensaoResponse.de(suspensao),
                suspensao == null ? null : suspensao.suspensoPor());
    }
}
