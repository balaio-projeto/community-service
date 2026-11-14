package br.com.balaio.community.avaliacao.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.balaio.community.avaliacao.domain.Avaliacao;

public interface AvaliacoesPort {
    boolean existeParaPedido(UUID pedidoId);

    Avaliacao registrar(Avaliacao avaliacao);

    Optional<Avaliacao> buscarDoPedido(UUID pedidoId);

    List<Integer> notasDoArtesao(UUID artesaoId);
}
