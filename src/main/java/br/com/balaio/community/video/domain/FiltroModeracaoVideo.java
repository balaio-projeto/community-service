package br.com.balaio.community.video.domain;

import java.util.Arrays;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public enum FiltroModeracaoVideo {
    TODOS("todos"),
    PUBLICADOS("publicada"),
    SUSPENSOS("suspensa");

    private final String codigo;

    FiltroModeracaoVideo(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public boolean aceita(Video video) {
        return switch (this) {
            case TODOS -> video.situacao() == SituacaoVideo.PUBLICADA || video.suspenso();
            case PUBLICADOS -> video.visivelAoPublico();
            case SUSPENSOS -> video.suspenso();
        };
    }

    public static FiltroModeracaoVideo deCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return TODOS;
        }
        return Arrays.stream(values()).filter(filtro -> filtro.codigo.equals(codigo.strip())).findFirst()
                .orElseThrow(() -> new RequisicaoInvalidaException(
                        "Estado inválido; use todos, publicada ou suspensa."));
    }
}
