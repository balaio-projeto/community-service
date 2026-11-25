package br.com.balaio.community.video.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public record SuspensaoVideo(String motivo, UUID suspensoPor, OffsetDateTime suspensoEm) {
    public static final int TAMANHO_MINIMO_MOTIVO = 10;
    public static final int TAMANHO_MAXIMO_MOTIVO = 1000;

    public SuspensaoVideo {
        Objects.requireNonNull(motivo, "motivo");
        Objects.requireNonNull(suspensoPor, "suspensoPor");
        Objects.requireNonNull(suspensoEm, "suspensoEm");
    }

    public static SuspensaoVideo nova(String motivo, UUID administrador, OffsetDateTime agora) {
        return new SuspensaoVideo(motivoValido(motivo), administrador, agora);
    }

    private static String motivoValido(String motivo) {
        String normalizado = motivo == null ? "" : motivo.strip();
        if (normalizado.length() < TAMANHO_MINIMO_MOTIVO || normalizado.length() > TAMANHO_MAXIMO_MOTIVO) {
            throw new RequisicaoInvalidaException("Explique o motivo da suspensão com " + TAMANHO_MINIMO_MOTIVO
                    + " a " + TAMANHO_MAXIMO_MOTIVO + " caracteres.");
        }
        return normalizado;
    }
}
