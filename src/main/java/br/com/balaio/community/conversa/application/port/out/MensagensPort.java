package br.com.balaio.community.conversa.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.balaio.community.conversa.domain.Mensagem;

public interface MensagensPort {
    Mensagem salvar(Mensagem mensagem);

    List<Mensagem> listarDoPedido(UUID pedidoId);

    Optional<Mensagem> ultimaDoPedido(UUID pedidoId);

    void registrarLeitura(UUID pedidoId, UUID leitorId, OffsetDateTime lidaEm);

    Optional<OffsetDateTime> ultimaLeitura(UUID pedidoId, UUID leitorId);
}
