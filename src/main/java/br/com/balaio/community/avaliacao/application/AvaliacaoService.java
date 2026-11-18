package br.com.balaio.community.avaliacao.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.balaio.community.avaliacao.application.port.out.AvaliacoesPort;
import br.com.balaio.community.avaliacao.application.port.out.PedidosAvaliaveisPort;
import br.com.balaio.community.avaliacao.domain.Avaliacao;
import br.com.balaio.community.avaliacao.domain.Reputacao;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@Service
public class AvaliacaoService {
    private final AvaliacoesPort avaliacoes;
    private final PedidosAvaliaveisPort pedidos;
    private final Clock relogio;

    public AvaliacaoService(AvaliacoesPort avaliacoes, PedidosAvaliaveisPort pedidos, Clock relogio) {
        this.avaliacoes = avaliacoes;
        this.pedidos = pedidos;
        this.relogio = relogio;
    }

    public Avaliacao avaliar(UUID pedidoId, NovaAvaliacao nova, UsuarioAutenticado comprador) {
        comprador.exigirPapel(Papel.COMPRADOR);
        if (avaliacoes.existeParaPedido(pedidoId)) {
            throw Avaliacao.pedidoNaoAvaliavel();
        }
        var pedido = pedidos.buscarDoComprador(pedidoId, comprador.token());
        if (!pedido.compradorId().equals(comprador.id())) {
            throw avaliacaoNaoEncontrada();
        }
        return avaliacoes.registrar(Avaliacao.registrar(pedido, nova.nota(), nova.comentario(), nova.aspectos(),
                OffsetDateTime.now(relogio)));
    }

    @Transactional(readOnly = true)
    public Avaliacao consultarDoPedido(UUID pedidoId, UsuarioAutenticado comprador) {
        return avaliacoes.buscarDoPedido(pedidoId)
                .filter(avaliacao -> avaliacao.compradorId().equals(comprador.id()))
                .orElseThrow(AvaliacaoService::avaliacaoNaoEncontrada);
    }

    @Transactional(readOnly = true)
    public Reputacao reputacaoDoArtesao(String artesaoSlug) {
        return Reputacao.das(avaliacoes.notasDoArtesao(idDoSlug(artesaoSlug)));
    }

    private static UUID idDoSlug(String artesaoSlug) {
        if (artesaoSlug == null || !artesaoSlug.startsWith(UsuarioAutenticado.PREFIXO_SLUG_ARTESAO)) {
            throw artesaoNaoEncontrado();
        }
        try {
            return UUID.fromString(artesaoSlug.substring(UsuarioAutenticado.PREFIXO_SLUG_ARTESAO.length()));
        } catch (IllegalArgumentException invalido) {
            throw artesaoNaoEncontrado();
        }
    }

    private static RecursoNaoEncontradoException avaliacaoNaoEncontrada() {
        return new RecursoNaoEncontradoException("AVALIACAO_NAO_ENCONTRADA", "Avaliação não encontrada.");
    }

    private static RecursoNaoEncontradoException artesaoNaoEncontrado() {
        return new RecursoNaoEncontradoException("ARTESAO_NAO_ENCONTRADO", "Artesão não encontrado.");
    }

    public record NovaAvaliacao(Integer nota, String comentario, List<String> aspectos) {
    }
}
