package br.com.balaio.community.avaliacao.application.port.out;

import java.util.UUID;

import br.com.balaio.community.avaliacao.domain.PedidoAvaliavel;

public interface PedidosAvaliaveisPort {
    PedidoAvaliavel buscarDoComprador(UUID pedidoId, String tokenComprador);
}
