package br.com.balaio.community.mediacao.application.port.out;

import java.util.UUID;

import br.com.balaio.community.mediacao.domain.DecisaoMediacao;

public interface PedidosCommercePort {
    void confirmarCompradorDoPedido(UUID pedidoId, String tokenComprador);

    void aplicarDecisao(UUID pedidoId, UUID decisaoId, DecisaoMediacao decisao);
}
