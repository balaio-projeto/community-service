package br.com.balaio.community.avaliacao.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import br.com.balaio.community.avaliacao.application.port.out.AvaliacoesPort;
import br.com.balaio.community.avaliacao.domain.Avaliacao;

@Component
class AvaliacoesPersistenceAdapter implements AvaliacoesPort {
    private final SpringDataAvaliacaoRepository repositorio;

    AvaliacoesPersistenceAdapter(SpringDataAvaliacaoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public boolean existeParaPedido(UUID pedidoId) {
        return repositorio.existsByPedidoId(pedidoId);
    }

    @Override
    public Avaliacao registrar(Avaliacao avaliacao) {
        try {
            return repositorio.saveAndFlush(AvaliacaoJpaEntity.de(avaliacao)).paraDominio();
        } catch (DataIntegrityViolationException avaliacaoSimultanea) {
            throw Avaliacao.pedidoNaoAvaliavel();
        }
    }

    @Override
    public Optional<Avaliacao> buscarDoPedido(UUID pedidoId) {
        return repositorio.findByPedidoId(pedidoId).map(AvaliacaoJpaEntity::paraDominio);
    }

    @Override
    public List<Integer> notasDoArtesao(UUID artesaoId) {
        return repositorio.notasDoArtesao(artesaoId);
    }
}
