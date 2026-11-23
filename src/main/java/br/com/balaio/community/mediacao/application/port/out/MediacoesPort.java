package br.com.balaio.community.mediacao.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.balaio.community.mediacao.domain.Mediacao;
import br.com.balaio.community.shared.paginacao.Pagina;

public interface MediacoesPort {
    boolean existeParaPedido(UUID pedidoId);

    Mediacao registrar(Mediacao mediacao);

    Mediacao atualizar(Mediacao mediacao);

    Optional<Mediacao> buscar(UUID id);

    Optional<Mediacao> buscarParaAtualizar(UUID id);

    List<Mediacao> listarTodas(Pagina pagina);

    List<Mediacao> listarDoComprador(UUID compradorId, Pagina pagina);

    List<Mediacao> listarDoArtesao(UUID artesaoId, Pagina pagina);

    boolean existemSemArtesao();

    void vincularArtesao(UUID artesaoId, Collection<UUID> pedidosDoArtesao);

    List<UUID> idsAguardandoEntrega(int limite);
}
