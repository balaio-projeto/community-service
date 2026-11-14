package br.com.balaio.community.colecao.domain;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

public enum TipoColecao {
    FAVORITOS("favoritos", 200, 160, Politica.RECUSAR_EXCESSO, Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*")),
    HISTORICO_BUSCA("historicoBusca", 20, 120, Politica.MANTER_MAIS_RECENTES, Pattern.compile("\\P{Cntrl}+")),
    VIDEOS_CURTIDOS("videosCurtidos", 500, 64, Politica.RECUSAR_EXCESSO, Pattern.compile("[A-Za-z0-9_-]+")),
    VIDEOS_SALVOS("videosSalvos", 500, 64, Politica.RECUSAR_EXCESSO, Pattern.compile("[A-Za-z0-9_-]+"));

    private final String codigo;
    private final int quantidadeMaxima;
    private final int tamanhoMaximoItem;
    private final Politica politicaDeExcesso;
    private final Pattern formatoItem;

    TipoColecao(String codigo, int quantidadeMaxima, int tamanhoMaximoItem, Politica politicaDeExcesso,
            Pattern formatoItem) {
        this.codigo = codigo;
        this.quantidadeMaxima = quantidadeMaxima;
        this.tamanhoMaximoItem = tamanhoMaximoItem;
        this.politicaDeExcesso = politicaDeExcesso;
        this.formatoItem = formatoItem;
    }

    public String codigo() { return codigo; }
    public int quantidadeMaxima() { return quantidadeMaxima; }
    public int tamanhoMaximoItem() { return tamanhoMaximoItem; }

    boolean descartaExcesso() {
        return politicaDeExcesso == Politica.MANTER_MAIS_RECENTES;
    }

    boolean aceitaFormato(String item) {
        return item.length() <= tamanhoMaximoItem && formatoItem.matcher(item).matches();
    }

    public static Optional<TipoColecao> deCodigo(String codigo) {
        return Arrays.stream(values()).filter(tipo -> tipo.codigo.equals(codigo)).findFirst();
    }

    private enum Politica {
        RECUSAR_EXCESSO,
        MANTER_MAIS_RECENTES
    }
}
