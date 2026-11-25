package br.com.balaio.community.shared.security;

import java.util.UUID;

import br.com.balaio.community.shared.exception.AcessoNegadoException;

public record UsuarioAutenticado(UUID id, Papel papel, String token) {
    public static final String PREFIXO_SLUG_ARTESAO = "artesao-";

    public boolean possuiPapel(Papel esperado) {
        return papel == esperado;
    }

    public UsuarioAutenticado exigirPapel(Papel esperado) {
        if (!possuiPapel(esperado)) {
            throw new AcessoNegadoException();
        }
        return this;
    }

    public boolean podeComprar() {
        return papel != null && papel.podeComprar();
    }

    public UsuarioAutenticado exigirQuemCompra() {
        if (!podeComprar()) {
            throw new AcessoNegadoException();
        }
        return this;
    }

    public String slugArtesao() {
        return PREFIXO_SLUG_ARTESAO + id;
    }

    @Override
    public String toString() {
        return "UsuarioAutenticado[id=" + id + ", papel=" + papel + "]";
    }
}
