package br.com.balaio.community.mediacao.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.balaio.community.mediacao.application.port.out.MediacoesPort;
import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort;
import br.com.balaio.community.mediacao.domain.DecisaoMediacao;
import br.com.balaio.community.mediacao.domain.Mediacao;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@Service
public class MediacaoService {
    private final MediacoesPort mediacoes;
    private final PedidosCommercePort pedidos;
    private final Clock relogio;

    public MediacaoService(MediacoesPort mediacoes, PedidosCommercePort pedidos, Clock relogio) {
        this.mediacoes = mediacoes;
        this.pedidos = pedidos;
        this.relogio = relogio;
    }

    public Mediacao abrir(AberturaMediacao abertura) {
        var mediacao = Mediacao.abrir(abertura.pedidoId(), abertura.compradorId(), abertura.assunto(),
                abertura.relato(), agora());
        if (mediacoes.existeParaPedido(abertura.pedidoId())) {
            throw mediacaoExistente();
        }
        var artesao = pedidos.confirmarCompradorDoPedido(abertura.pedidoId(), abertura.tokenComprador());
        return mediacoes.registrar(mediacao.doArtesao(artesao));
    }

    @Transactional(readOnly = true)
    public List<Mediacao> listarTodas(Pagina pagina) {
        return mediacoes.listarTodas(pagina);
    }

    @Transactional(readOnly = true)
    public List<Mediacao> listarDoComprador(UUID compradorId, Pagina pagina) {
        return mediacoes.listarDoComprador(compradorId, pagina);
    }

    public List<Mediacao> listarDoArtesao(UsuarioAutenticado artesao, Pagina pagina) {
        artesao.exigirPapel(Papel.ARTESAO);
        if (mediacoes.existemSemArtesao()) {
            mediacoes.vincularArtesao(artesao.id(), pedidos.pedidosVendidosPeloArtesao(artesao.token()));
        }
        return mediacoes.listarDoArtesao(artesao.id(), pagina);
    }

    @Transactional(readOnly = true)
    public Mediacao consultar(UUID id) {
        return mediacoes.buscar(id).orElseThrow(MediacaoService::naoEncontrada);
    }

    @Transactional
    public Mediacao iniciarAnalise(UUID id) {
        var mediacao = bloquear(id);
        mediacao.iniciarAnalise(agora());
        return mediacoes.atualizar(mediacao);
    }

    @Transactional
    public Mediacao registrarDecisao(UUID id, DecisaoMediacao decisao) {
        var mediacao = bloquear(id);
        mediacao.registrarDecisao(decisao, agora());
        return mediacoes.atualizar(mediacao);
    }

    static ConflitoException mediacaoExistente() {
        return new ConflitoException("MEDIACAO_EXISTENTE", "Já existe uma mediação para este pedido.");
    }

    static RecursoNaoEncontradoException naoEncontrada() {
        return new RecursoNaoEncontradoException("MEDIACAO_NAO_ENCONTRADA", "Mediação não encontrada.");
    }

    private Mediacao bloquear(UUID id) {
        return mediacoes.buscarParaAtualizar(id).orElseThrow(MediacaoService::naoEncontrada);
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(relogio);
    }
}
