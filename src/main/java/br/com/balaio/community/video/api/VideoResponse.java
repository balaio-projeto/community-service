package br.com.balaio.community.video.api;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import br.com.balaio.community.shared.security.UsuarioAutenticado;
import br.com.balaio.community.video.application.VideoService.VideoComNumeros;
import br.com.balaio.community.video.domain.SituacaoVideo;

public record VideoResponse(UUID id, String artesao, String peca, String legenda, List<String> etiquetas,
        String duracao, long visualizacoes, long curtidas, long comentarios, String publicadoEm, String capa,
        String url, String situacao) {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String ROTULO_RASCUNHO = "Rascunho";

    static VideoResponse de(VideoComNumeros detalhado) {
        var video = detalhado.video();
        String base = "/api/v1/videos/" + video.id();
        return new VideoResponse(video.id(), UsuarioAutenticado.PREFIXO_SLUG_ARTESAO + video.artesaoId(),
                video.pecaSlug(), video.legenda(), video.etiquetas(), video.duracao(), 0, detalhado.curtidas(),
                detalhado.comentarios(),
                video.situacao() == SituacaoVideo.RASCUNHO || video.publicadoEm() == null
                        ? ROTULO_RASCUNHO : video.publicadoEm().format(FORMATO_DATA),
                video.capa() == null ? null : base + "/capa",
                video.arquivo() == null ? null : base + "/arquivo",
                video.situacao().codigo());
    }
}
