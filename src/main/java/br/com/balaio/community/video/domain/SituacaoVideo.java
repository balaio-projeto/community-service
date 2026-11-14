package br.com.balaio.community.video.domain;

import java.util.Arrays;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public enum SituacaoVideo {
    PUBLICADA("publicada"),
    RASCUNHO("rascunho");

    private final String codigo;

    SituacaoVideo(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static SituacaoVideo deCodigo(String codigo) {
        return Arrays.stream(values()).filter(situacao -> situacao.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new RequisicaoInvalidaException("Situação do vídeo inválida."));
    }
}
