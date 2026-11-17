package br.com.balaio.community.mediacao.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import br.com.balaio.community.mediacao.application.port.out.MediacoesPort;
import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort;
import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort.EntregaRecusadaException;
import br.com.balaio.community.mediacao.domain.Mediacao;

@Service
public class EntregaDecisaoService {
    public static final int MAXIMO_TENTATIVAS = 10;
    private static final Logger LOG = LoggerFactory.getLogger(EntregaDecisaoService.class);

    private final MediacoesPort mediacoes;
    private final PedidosCommercePort pedidos;
    private final TransactionOperations transacao;
    private final Clock relogio;

    public EntregaDecisaoService(MediacoesPort mediacoes, PedidosCommercePort pedidos, TransactionOperations transacao,
            Clock relogio) {
        this.mediacoes = mediacoes;
        this.pedidos = pedidos;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<UUID> aguardandoEntrega(int limite) {
        if (!pedidos.entregaDeDecisoesConfigurada()) {
            return List.of();
        }
        return mediacoes.idsAguardandoEntrega(MAXIMO_TENTATIVAS, limite);
    }

    public Mediacao entregar(UUID mediacaoId) {
        var pendente = mediacoes.buscar(mediacaoId).orElseThrow(MediacaoService::naoEncontrada);
        if (!pendente.aguardaEntregaDaDecisao() || !pedidos.entregaDeDecisoesConfigurada()) {
            return pendente;
        }
        var resultado = enviarAoCommerce(pendente);
        return transacao.execute(status -> registrar(mediacaoId, resultado));
    }

    private ResultadoEntrega enviarAoCommerce(Mediacao mediacao) {
        try {
            pedidos.aplicarDecisao(mediacao.pedidoId(), mediacao.decisaoId(), mediacao.decisao());
            return ResultadoEntrega.CONFIRMADA;
        } catch (EntregaRecusadaException falha) {
            return new ResultadoEntrega(falha.getMessage());
        }
    }

    private Mediacao registrar(UUID mediacaoId, ResultadoEntrega resultado) {
        var mediacao = mediacoes.buscarParaAtualizar(mediacaoId).orElseThrow(MediacaoService::naoEncontrada);
        if (!mediacao.aguardaEntregaDaDecisao()) {
            return mediacao;
        }
        if (resultado.confirmada()) {
            mediacao.confirmarEntregaDaDecisao(agora());
        } else {
            mediacao.registrarFalhaNaEntrega(resultado.motivoDaFalha(), agora());
            avisarSeEsgotou(mediacao);
        }
        return mediacoes.atualizar(mediacao);
    }

    private static void avisarSeEsgotou(Mediacao mediacao) {
        if (mediacao.tentativasEntrega() >= MAXIMO_TENTATIVAS) {
            LOG.error("Mediação {} esgotou {} tentativas de entregar a decisão ao commerce; último erro: {}",
                    mediacao.id(), MAXIMO_TENTATIVAS, mediacao.ultimoErroEntrega());
        } else {
            LOG.warn("Mediação {}: tentativa {} de entregar a decisão falhou ({})",
                    mediacao.id(), mediacao.tentativasEntrega(), mediacao.ultimoErroEntrega());
        }
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(relogio);
    }

    private record ResultadoEntrega(String motivoDaFalha) {
        static final ResultadoEntrega CONFIRMADA = new ResultadoEntrega(null);

        boolean confirmada() {
            return motivoDaFalha == null;
        }
    }
}
