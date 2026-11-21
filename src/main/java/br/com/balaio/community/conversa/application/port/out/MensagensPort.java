package br.com.balaio.community.conversa.application.port.out;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.balaio.community.conversa.domain.Mensagem;

public interface MensagensPort {
    Mensagem salvarIdempotente(Mensagem mensagem, String chave);

    List<Mensagem> listarDoPedido(UUID pedidoId);

    Map<UUID, Mensagem> ultimasDosPedidos(Collection<UUID> pedidoIds);

    void registrarLeitura(UUID pedidoId, UUID leitorId, OffsetDateTime lidaEm);

    Map<UUID, OffsetDateTime> leiturasDoLeitor(Collection<UUID> pedidoIds, UUID leitorId);
}
