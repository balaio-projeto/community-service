package br.com.balaio.community.conversa.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.balaio.community.conversa.application.port.out.MensagensPort;
import br.com.balaio.community.conversa.application.port.out.PedidosDaConversaPort;
import br.com.balaio.community.conversa.domain.Mensagem;
import br.com.balaio.community.conversa.domain.ParteDaConversa;
import br.com.balaio.community.conversa.domain.ResumoConversa;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@Service
public class ConversaService {
    private final MensagensPort mensagens;
    private final PedidosDaConversaPort pedidos;
    private final Clock relogio;

    public ConversaService(MensagensPort mensagens, PedidosDaConversaPort pedidos, Clock relogio) {
        this.mensagens = mensagens;
        this.pedidos = pedidos;
        this.relogio = relogio;
    }

    @Transactional
    public List<Mensagem> lerConversa(UUID pedidoId, UsuarioAutenticado leitor) {
        exigirParte(pedidoId, leitor);
        mensagens.registrarLeitura(pedidoId, leitor.id(), agora());
        return mensagens.listarDoPedido(pedidoId);
    }

    @Transactional
    public Mensagem enviar(UUID pedidoId, String texto, UsuarioAutenticado autor) {
        var parte = exigirParte(pedidoId, autor);
        var mensagem = mensagens.salvar(Mensagem.enviar(pedidoId, autor.id(), parte, texto, agora()));
        mensagens.registrarLeitura(pedidoId, autor.id(), mensagem.enviadaEm());
        return mensagem;
    }

    @Transactional(readOnly = true)
    public List<ResumoConversa> conversasDoArtesao(UsuarioAutenticado artesao) {
        artesao.exigirPapel(Papel.ARTESAO);
        return pedidos.vendasDoArtesao(artesao).stream()
                .map(pedido -> ResumoConversa.para(artesao.id(), pedido, mensagens.ultimaDoPedido(pedido.id()),
                        mensagens.ultimaLeitura(pedido.id(), artesao.id())))
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
