package br.com.balaio.community.avaliacao.domain;

import java.util.UUID;

public record PedidoAvaliavel(UUID id, UUID compradorId, UUID artesaoId, String pecaSlug, String estado) {
    private static final String ESTADO_ENTREGUE = "entregue";

    public boolean foiEntregue() {
        return ESTADO_ENTREGUE.equals(estado);
    }
}
