package br.com.balaio.community.mediacao.domain;

import java.util.Arrays;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public enum DecisaoMediacao {
    CANCELAR_REEMBOLSAR("cancelar_reembolsar"),
    NEGAR("negar"),
    MANTER_CONVERSA("manter_conversa");

    private final String codigo;

    DecisaoMediacao(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static DecisaoMediacao deCodigo(String codigo) {
        return Arrays.stream(values()).filter(decisao -> decisao.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new RequisicaoInvalidaException("Decisão inválida."));
    }
}
