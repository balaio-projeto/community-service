package br.com.balaio.community.shared.paginacao;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public record Pagina(int numero, int tamanho) {
    public static final int TAMANHO_PADRAO = 50;
    public static final int TAMANHO_MAXIMO = 100;

    public Pagina {
        if (numero < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new RequisicaoInvalidaException(
                    "Paginação inválida: use pagina >= 0 e tamanho entre 1 e " + TAMANHO_MAXIMO + ".");
        }
    }

    public static Pagina de(int numero, int tamanho) {
        return new Pagina(numero, tamanho);
    }

    public static Pagina primeira() {
        return new Pagina(0, TAMANHO_PADRAO);
    }

    public long deslocamento() {
        return (long) numero * tamanho;
    }
}
