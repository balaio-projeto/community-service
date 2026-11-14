package br.com.balaio.community.mediacao.domain;

import java.util.Arrays;

public enum EstadoMediacao {
    ABERTA("aberta"),
    EM_ANALISE("emAnalise"),
    DECISAO_PENDENTE("decisao_pendente"),
    DECIDIDA("decidida");

    private final String codigo;

    EstadoMediacao(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public boolean aceitaAnalise() {
        return this == ABERTA || this == EM_ANALISE;
    }

    public static EstadoMediacao deCodigo(String codigo) {
        return Arrays.stream(values()).filter(estado -> estado.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de mediação desconhecido: " + codigo));
    }
}
