package br.com.balaio.community.shared.security;

import java.util.Arrays;
import java.util.Optional;

public enum Papel {
    COMPRADOR("comprador"),
    ARTESAO("artesao"),
    ORGANIZADOR("organizador"),
    ADMIN("admin");

    private final String codigo;

    Papel(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static Optional<Papel> deCodigo(String codigo) {
        return Arrays.stream(values()).filter(papel -> papel.codigo.equals(codigo)).findFirst();
    }
}
