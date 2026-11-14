package br.com.balaio.community.mediacao.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.balaio.community.mediacao.domain.Mediacao;

public interface MediacoesPort {
    boolean existeParaPedido(UUID pedidoId);

    Mediacao registrar(Mediacao mediacao);

    Mediacao atualizar(Mediacao mediacao);

    Optional<Mediacao> buscar(UUID id);

    Optional<Mediacao> buscarParaAtualizar(UUID id);

    List<Mediacao> listarTodas();

    List<Mediacao> listarDoComprador(UUID compradorId);

    List<UUID> idsAguardandoEntrega(int maximoTentativas, int limite);
}
