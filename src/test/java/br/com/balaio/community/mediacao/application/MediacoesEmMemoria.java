package br.com.balaio.community.mediacao.application;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import br.com.balaio.community.mediacao.application.port.out.MediacoesPort;
import br.com.balaio.community.mediacao.domain.Mediacao;
import br.com.balaio.community.shared.paginacao.Pagina;

class MediacoesEmMemoria implements MediacoesPort {
    private final Map<UUID, Mediacao.Dados> armazenadas = new LinkedHashMap<>();

    @Override
    public boolean existeParaPedido(UUID pedidoId) {
        return armazenadas.values().stream().anyMatch(dados -> dados.pedidoId().equals(pedidoId));
    }

    @Override
    public Mediacao registrar(Mediacao mediacao) {
        armazenadas.put(mediacao.id(), copiar(mediacao));
        return reconstituir(mediacao.id());
    }

    @Override
    public Mediacao atualizar(Mediacao mediacao) {
        return registrar(mediacao);
    }

    @Override
    public Optional<Mediacao> buscar(UUID id) {
        return Optional.ofNullable(armazenadas.get(id)).map(Mediacao::reconstituir);
    }

    @Override
    public Optional<Mediacao> buscarParaAtualizar(UUID id) {
        return buscar(id);
    }

    @Override
    public List<Mediacao> listarTodas(Pagina pagina) {
        return paginar(todas(), pagina);
    }

    @Override
    public List<Mediacao> listarDoComprador(UUID compradorId, Pagina pagina) {
        return paginar(todas().stream().filter(mediacao -> mediacao.compradorId().equals(compradorId)).toList(),
                pagina);
    }

    @Override
    public List<Mediacao> listarDoArtesao(UUID artesaoId, Pagina pagina) {
        return paginar(todas().stream().filter(mediacao -> artesaoId.equals(mediacao.artesaoId())).toList(), pagina);
    }

    @Override
    public boolean existemSemArtesao() {
        return armazenadas.values().stream().anyMatch(dados -> dados.artesaoId() == null);
    }

    @Override
    public void vincularArtesao(UUID artesaoId, Collection<UUID> pedidosDoArtesao) {
        todas().stream().filter(mediacao -> mediacao.artesaoId() == null)
                .filter(mediacao -> pedidosDoArtesao.contains(mediacao.pedidoId()))
                .forEach(mediacao -> registrar(mediacao.doArtesao(artesaoId)));
    }

    @Override
    public List<UUID> idsAguardandoEntrega(int limite) {
        return todas().stream().filter(Mediacao::aguardaEntregaDaDecisao).limit(limite).map(Mediacao::id).toList();
    }

    private List<Mediacao> todas() {
        return armazenadas.values().stream().map(Mediacao::reconstituir)
                .sorted(Comparator.comparing(Mediacao::criadaEm).reversed()).toList();
    }

    private static List<Mediacao> paginar(List<Mediacao> lista, Pagina pagina) {
        return lista.stream().skip(pagina.deslocamento()).limit(pagina.tamanho()).toList();
    }

    private Mediacao reconstituir(UUID id) {
        return Mediacao.reconstituir(armazenadas.get(id));
    }

    private static Mediacao.Dados copiar(Mediacao mediacao) {
        return new Mediacao.Dados(mediacao.id(), mediacao.pedidoId(), mediacao.compradorId(), mediacao.artesaoId(),
                mediacao.assunto(), mediacao.relato(), mediacao.estado(), mediacao.decisao(), mediacao.decisaoId(),
                mediacao.tentativasEntrega(), mediacao.ultimoErroEntrega(), mediacao.criadaEm(),
                mediacao.atualizadaEm());
    }
}
