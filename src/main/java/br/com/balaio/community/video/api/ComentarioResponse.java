package br.com.balaio.community.video.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import br.com.balaio.community.video.domain.ComentarioVideo;

public record ComentarioResponse(UUID id, UUID videoId, String autor, String texto, String quando, long curtidas,
        boolean artesao, OffsetDateTime criadoEm) {

    private static final ZoneId FUSO_EXIBICAO = ZoneId.of("America/Recife");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static ComentarioResponse de(ComentarioVideo comentario) {
        return new ComentarioResponse(comentario.id(), comentario.videoId(), comentario.autorNome(),
                comentario.texto(), comentario.criadoEm().atZoneSameInstant(FUSO_EXIBICAO).format(FORMATO), 0,
                comentario.autorEhArtesao(), comentario.criadoEm());
    }
}
