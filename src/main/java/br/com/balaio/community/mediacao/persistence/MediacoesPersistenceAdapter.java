package br.com.balaio.community.mediacao.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import br.com.balaio.community.mediacao.application.port.out.MediacoesPort;
import br.com.balaio.community.mediacao.domain.EstadoMediacao;
import br.com.balaio.community.mediacao.domain.Mediacao;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;

@Component
class MediacoesPersistenceAdapter implements MediacoesPort {
    private static final String CANCELADA = EstadoMediacao.CANCELADA.codigo();

    private final SpringDataMediacaoRepository repositorio;

    MediacoesPersistenceAdapter(SpringDataMediacaoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public boolean existeParaPedido(UUID pedidoId) {
        return repositorio.existsByPedidoIdAndEstadoNot(pedidoId, CANCELADA);
    }

    @Override
    public Mediacao registrar(Mediacao mediacao) {
        try {
            return repositorio.saveAndFlush(MediacaoJpaEntity.de(mediacao)).paraDominio();
        } catch (DataIntegrityViolationException duplicada) {
            throw new ConflitoException("MEDIACAO_EXISTENTE", "Já existe uma mediação para este pedido.");
        }
    }

    @Override
    public Mediacao atualizar(Mediacao mediacao) {
        var entidade = repositorio.findById(mediacao.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("MEDIACAO_NAO_ENCONTRADA",
                        "Mediação não encontrada."));
        entidade.copiarEstadoDe(mediacao);
        return repositorio.save(entidade).paraDominio();
    }

    @Override
    public Optional<Mediacao> buscar(UUID id) {
        return repositorio.findById(id).map(MediacaoJpaEntity::paraDominio);
    }

    @Override
    public Optional<Mediacao> buscarParaAtualizar(UUID id) {
        return repositorio.buscarComBloqueio(id).map(MediacaoJpaEntity::paraDominio);
    }

    @Override
    public List<Mediacao> listarTodas(Pagina pagina) {
        return repositorio.findByEstadoNotOrderByCriadaEmDesc(CANCELADA, paginacao(pagina)).stream()
                .map(MediacaoJpaEntity::paraDominio).toList();
    }

    @Override
    public List<Mediacao> listarDoComprador(UUID compradorId, Pagina pagina) {
        return repositorio.findByCompradorIdAndEstadoNotOrderByCriadaEmDesc(compradorId, CANCELADA,
                paginacao(pagina)).stream().map(MediacaoJpaEntity::paraDominio).toList();
    }

    @Override
    public List<Mediacao> listarDoArtesao(UUID artesaoId, Pagina pagina) {
        return repositorio.findByArtesaoIdAndEstadoNotOrderByCriadaEmDesc(artesaoId, CANCELADA,
                paginacao(pagina)).stream().map(MediacaoJpaEntity::paraDominio).toList();
    }

    @Override
    public List<Mediacao> listarComoCompradorOuArtesao(UUID contaId, Pagina pagina) {
        return repositorio.listarComoCompradorOuArtesao(contaId, CANCELADA, paginacao(pagina)).stream()
                .map(MediacaoJpaEntity::paraDominio).toList();
    }

    @Override
    public boolean existemSemArtesao() {
        return repositorio.existsByArtesaoIdIsNullAndEstadoNot(CANCELADA);
    }

    @Override
    public void vincularArtesao(UUID artesaoId, Collection<UUID> pedidosDoArtesao) {
        if (!pedidosDoArtesao.isEmpty()) {
            repositorio.vincularArtesao(artesaoId, pedidosDoArtesao);
        }
    }

    private static PageRequest paginacao(Pagina pagina) {
        return PageRequest.of(pagina.numero(), pagina.tamanho());
    }

    @Override
    public List<UUID> idsAguardandoEntrega(int limite) {
        return repositorio.idsAguardandoEntrega(limite);
    }
}
