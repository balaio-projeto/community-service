package br.com.balaio.community.mediacao.application;

import java.util.UUID;

public record AberturaMediacao(UUID pedidoId, UUID compradorId, String assunto, String relato,
        String tokenComprador) {

    @Override
    public String toString() {
        return "AberturaMediacao[pedidoId=" + pedidoId + ", compradorId=" + compradorId + "]";
    }
}
