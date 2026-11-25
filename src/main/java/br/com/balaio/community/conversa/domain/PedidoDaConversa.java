package br.com.balaio.community.conversa.domain;

import java.util.Locale;
import java.util.UUID;

public record PedidoDaConversa(UUID id, String compradorNome, String data, String compradorImagem) {
    private static final int TAMANHO_CODIGO = 8;

    public PedidoDaConversa(UUID id, String compradorNome, String data) {
        this(id, compradorNome, data, null);
    }

    public String codigo() {
        return "#" + id.toString().substring(0, TAMANHO_CODIGO).toUpperCase(Locale.ROOT);
    }
}
