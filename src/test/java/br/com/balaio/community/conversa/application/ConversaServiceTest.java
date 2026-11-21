package br.com.balaio.community.conversa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import br.com.balaio.community.conversa.application.port.out.MensagensPort;
import br.com.balaio.community.conversa.application.port.out.PedidosDaConversaPort;
import br.com.balaio.community.conversa.domain.Mensagem;
import br.com.balaio.community.conversa.domain.ParteDaConversa;
import br.com.balaio.community.conversa.domain.PedidoDaConversa;
import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.exception.ServicoIndisponivelException;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

class ConversaServiceTest {
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);

    private final UsuarioAutenticado comprador = new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "c");
    private final UsuarioAutenticado artesao = new UsuarioAutenticado(UUID.randomUUID(), Papel.ARTESAO, "a");
    private final PedidoDaConversa pedido = new PedidoDaConversa(UUID.randomUUID(), "Ana", "14/11/2026");

    private PedidosDaConversaPort pedidos;
    private ConversaService conversas;

    @BeforeEach
    void preparar() {
        pedidos = mock(PedidosDaConversaPort.class);
        conversas = new ConversaService(new MensagensEmMemoria(), pedidos, TransactionOperations.withoutTransaction(),
                RELOGIO);
        when(pedidos.parteNoPedido(any(), any())).thenReturn(Optional.empty());
        when(pedidos.parteNoPedido(pedido.id(), comprador)).thenReturn(Optional.of(ParteDaConversa.COMPRADOR));
        when(pedidos.parteNoPedido(pedido.id(), artesao)).thenReturn(Optional.of(ParteDaConversa.ARTESAO));
        when(pedidos.vendasDoArtesao(artesao)).thenReturn(List.of(pedido));
    }

    @Test
    void compradorEArtesaoConversamComAutoriaDefinidaPeloPedido() {
        conversas.enviar(pedido.id(), "Quando chega?", comprador);
        conversas.enviar(pedido.id(), "Amanhã!", artesao);

        assertThat(conversas.lerConversa(pedido.id(), comprador))
                .extracting(Mensagem::autor).containsExactly(ParteDaConversa.COMPRADOR, ParteDaConversa.ARTESAO);
    }

    @Test
    void retryRetornaMensagemOriginalEReutilizacaoDiferenteConflita() {
        var primeira = conversas.enviar(pedido.id(), "Olá", "tentativa-1", comprador);
        var repetida = conversas.enviar(pedido.id(), "Olá", "tentativa-1", comprador);
        assertThat(repetida.id()).isEqualTo(primeira.id());
        assertThat(conversas.lerConversa(pedido.id(), comprador)).hasSize(1);
        assertThatThrownBy(() -> conversas.enviar(pedido.id(), "Outro texto", "tentativa-1", comprador))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void terceiroNaoVeNemEscreveNaConversa() {
        var intruso = new UsuarioAutenticado(UUID.randomUUID(), Papel.ARTESAO, "x");

        assertThatThrownBy(() -> conversas.lerConversa(pedido.id(), intruso))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> conversas.enviar(pedido.id(), "Oi", intruso))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void falhaNoCommerceNaoLiberaAcesso() {
        when(pedidos.parteNoPedido(pedido.id(), comprador))
                .thenThrow(new ServicoIndisponivelException("COMMERCE_INDISPONIVEL", "x"));

        assertThatThrownBy(() -> conversas.lerConversa(pedido.id(), comprador))
                .isInstanceOf(ServicoIndisponivelException.class);
    }

    @Test
    void painelDoArtesaoMostraPreviaENaoLidaAteEleAbrir() {
        conversas.enviar(pedido.id(), "Quando chega?", comprador);

        var antes = conversas.conversasDoArtesao(artesao).getFirst();
        assertThat(antes.naoLida()).isTrue();
        assertThat(antes.ultimaMensagem()).map(Mensagem::texto).contains("Quando chega?");

        conversas.lerConversa(pedido.id(), artesao);
        assertThat(conversas.conversasDoArtesao(artesao).getFirst().naoLida()).isFalse();
    }

    @Test
    void confirmacaoExplicitaDeLeituraZeraPendenciaDoArtesao() {
        conversas.enviar(pedido.id(), "Chegou?", comprador);
        assertThat(conversas.conversasDoArtesao(artesao).getFirst().naoLida()).isTrue();

        conversas.confirmarLeitura(pedido.id(), artesao);

        assertThat(conversas.conversasDoArtesao(artesao).getFirst().naoLida()).isFalse();
    }

    @Test
    void terceiroNaoConfirmaLeitura() {
        var intruso = new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "x");

        assertThatThrownBy(() -> conversas.confirmarLeitura(pedido.id(), intruso))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void compradorNaoAcessaPainelDeConversas() {
        assertThatThrownBy(() -> conversas.conversasDoArtesao(comprador)).isInstanceOf(AcessoNegadoException.class);
    }

    private static final class MensagensEmMemoria implements MensagensPort {
        private final List<Mensagem> mensagens = new ArrayList<>();
        private final Map<String, Mensagem> chaves = new HashMap<>();
        private final Map<String, OffsetDateTime> leituras = new HashMap<>();

        @Override
        public Mensagem salvarIdempotente(Mensagem mensagem, String chave) {
            var anterior = chaves.get(mensagem.autorId() + ":" + chave);
            if (anterior != null) {
                if (!anterior.pedidoId().equals(mensagem.pedidoId()) || !anterior.texto().equals(mensagem.texto()))
                    throw new ConflitoException("CHAVE_IDEMPOTENCIA_REUTILIZADA", "Chave reutilizada.");
                return anterior;
            }
            mensagens.add(mensagem);
            chaves.put(mensagem.autorId() + ":" + chave, mensagem);
            return mensagem;
        }

        @Override
        public List<Mensagem> listarDoPedido(UUID pedidoId) {
            return mensagens.stream().filter(m -> m.pedidoId().equals(pedidoId)).toList();
        }

        @Override
        public Map<UUID, Mensagem> ultimasDosPedidos(Collection<UUID> pedidoIds) {
            var ultimas = new HashMap<UUID, Mensagem>();
            mensagens.stream().filter(m -> pedidoIds.contains(m.pedidoId())).forEach(m -> ultimas.put(m.pedidoId(), m));
            return ultimas;
        }

        @Override
        public void registrarLeitura(UUID pedidoId, UUID leitorId, OffsetDateTime lidaEm) {
            leituras.merge(pedidoId + ":" + leitorId, lidaEm, (a, b) -> a.isAfter(b) ? a : b);
        }

        @Override
        public Map<UUID, OffsetDateTime> leiturasDoLeitor(Collection<UUID> pedidoIds, UUID leitorId) {
            var doLeitor = new HashMap<UUID, OffsetDateTime>();
            pedidoIds.forEach(id -> Optional.ofNullable(leituras.get(id + ":" + leitorId))
                    .ifPresent(lida -> doLeitor.put(id, lida)));
            return doLeitor;
        }
    }
}
