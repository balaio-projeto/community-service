package br.com.balaio.community.conversa.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.balaio.community.conversa.domain.ParteDaConversa;
import br.com.balaio.community.conversa.domain.PedidoDaConversa;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

public interface PedidosDaConversaPort {
    Optional<ParteDaConversa> parteNoPedido(UUID pedidoId, UsuarioAutenticado usuario);

    List<PedidoDaConversa> vendasDoArtesao(UsuarioAutenticado artesao);
}
