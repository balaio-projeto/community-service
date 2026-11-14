package br.com.balaio.community.video.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public record ComentarioVideo(UUID id, UUID videoId, UUID autorId, String autorNome, boolean autorEhArtesao,
        String texto, OffsetDateTime criadoEm) {

    public static final int TAMANHO_MAXIMO_TEXTO = 1000;
    public static final int TAMANHO_MAXIMO_NOME = 120;

    public static ComentarioVideo comentar(Video video, UUID autorId, String autorNome, String texto,
            OffsetDateTime agora) {
        if (texto == null || texto.isBlank()) {
            throw new RequisicaoInvalidaException("Informe o texto.");
        }
        String normalizado = texto.strip();
        if (normalizado.length() > TAMANHO_MAXIMO_TEXTO) {
            throw new RequisicaoInvalidaException("O comentário deve ter até " + TAMANHO_MAXIMO_TEXTO + " caracteres.");
        }
        return new ComentarioVideo(UUID.randomUUID(), video.id(), autorId, nomeExibivel(autorNome),
                video.pertenceA(autorId), normalizado, agora);
    }

    private static String nomeExibivel(String nome) {
        String normalizado = nome == null || nome.isBlank() ? "Visitante" : nome.strip();
        return normalizado.length() > TAMANHO_MAXIMO_NOME ? normalizado.substring(0, TAMANHO_MAXIMO_NOME) : normalizado;
    }
}
