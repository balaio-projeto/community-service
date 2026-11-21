package br.com.balaio.community.conversa.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import br.com.balaio.community.conversa.application.port.out.MensagensPort;
import br.com.balaio.community.conversa.application.port.out.PedidosDaConversaPort;
import br.com.balaio.community.conversa.domain.Mensagem;
import br.com.balaio.community.conversa.domain.ParteDaConversa;
import br.com.balaio.community.conversa.domain.PedidoDaConversa;
import br.com.balaio.community.conversa.domain.ResumoConversa;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@Service
public class ConversaService {
    private final MensagensPort mensagens;
    private final PedidosDaConversaPort pedidos;
    private final TransactionOperations transacao;
    private final Clock relogio;

    public ConversaService(MensagensPort mensagens, PedidosDaConversaPort pedidos, TransactionOperations transacao,
            Clock relogio) {
        this.mensagens = mensagens;
        this.pedidos = pedidos;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    public List<Mensagem> lerConversa(UUID pedidoId, UsuarioAutenticado leitor) {
        exigirParte(pedidoId, leitor);
        return transacao.execute(status -> {
            mensagens.registrarLeitura(pedidoId, leitor.id(), agora());
            return mensagens.listarDoPedido(pedidoId);
        });
    }

    public void confirmarLeitura(UUID pedidoId, UsuarioAutenticado leitor) {
        exigirParte(pedidoId, leitor);
        mensagens.registrarLeitura(pedidoId, leitor.id(), agora());
    }

    public Mensagem enviar(UUID pedidoId, String texto, UsuarioAutenticado autor) {
        return enviar(pedidoId, texto, UUID.randomUUID().toString(), autor);
    }

    public Mensagem enviar(UUID pedidoId, String texto, String chave, UsuarioAutenticado autor) {
        var parte = exigirParte(pedidoId, autor);
        if (chave == null || chave.isBlank() || chave.length() > 160) {
            throw new RequisicaoInvalidaException("Informe uma chave de idempotência válida.");
        }
        var nova = Mensagem.enviar(pedidoId, autor.id(), parte, texto, agora());
        return transacao.execute(status -> {
            var mensagem = mensagens.salvarIdempotente(nova, chave);
            mensagens.registrarLeitura(pedidoId, autor.id(), mensagem.enviadaEm());
            return mensagem;
        });
    }

    public List<ResumoConversa> conversasDoArtesao(UsuarioAutenticado artesao) {
        artesao.exigirPapel(Papel.ARTESAO);
        var vendas = pedidos.vendasDoArtesao(artesao);
        var ids = vendas.stream().map(PedidoDaConversa::id).toList();
        var ultimas = mensagens.ultimasDosPedidos(ids);
        var leituras = mensagens.leiturasDoLeitor(ids, artesao.id());
        return vendas.stream()
                .map(pedido -> ResumoConversa.para(artesao.id(), pedido,
                        Optional.ofNullable(ultimas.get(pedido.id())),
                        Optional.ofNullable(leituras.get(pedido.id()))))
                .toList();
    }

    private ParteDaConversa exigirParte(UUID pedidoId, UsuarioAutenticado usuario) {
        return pedidos.parteNoPedido(pedidoId, usuario)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CONVERSA_NAO_ENCONTRADA",
                        "Conversa não encontrada."));
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(relogio);
    }
}
