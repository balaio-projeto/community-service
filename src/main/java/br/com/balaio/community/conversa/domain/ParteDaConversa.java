package br.com.balaio.community.conversa.domain;

public enum ParteDaConversa {
    COMPRADOR("comprador"),
    ARTESAO("artesao");

    private final String codigo;

    ParteDaConversa(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static ParteDaConversa deCodigo(String codigo) {
        return COMPRADOR.codigo.equals(codigo) ? COMPRADOR : ARTESAO;
    }
}
