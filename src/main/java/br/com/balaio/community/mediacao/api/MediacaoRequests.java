package br.com.balaio.community.mediacao.api;

import java.util.UUID;

final class MediacaoRequests {
    private MediacaoRequests() {
    }

    record Abertura(UUID pedido, String assunto, String relato) {
    }

    record AlteracaoEstado(String estado) {
    }

    record Decisao(String decisao) {
    }
}
