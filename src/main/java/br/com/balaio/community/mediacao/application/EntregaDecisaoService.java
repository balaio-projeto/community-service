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
import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort.FalhaNaEntregaException;
import br.com.balaio.community.mediacao.domain.Mediacao;

@Service
public class EntregaDecisaoService {
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
        return mediacoes.idsAguardandoEntrega(limite);
    }

    public Mediacao entregar(UUID mediacaoId) {
        var pendente = mediacoes.buscar(mediacaoId).orElseThrow(MediacaoService::naoEncontrada);
        if (!pendente.aguardaEntregaDaDecisao() || !pedidos.entregaDeDecisoesConfigurada()) {
            return pendente;
        }
        var resultado = enviarAoCommerce(pendente);
        return transacao.execute(status -> registrar(mediacaoId, resultado));
    }

    public Mediacao reprocessar(UUID mediacaoId) {
        transacao.executeWithoutResult(status -> {
            var mediacao = mediacoes.buscarParaAtualizar(mediacaoId).orElseThrow(MediacaoService::naoEncontrada);
            mediacao.reabrirEntregaDaDecisao(agora());
            mediacoes.atualizar(mediacao);
        });
        LOG.info("Mediação {}: entrega da decisão reaberta por um administrador.", mediacaoId);
        return entregar(mediacaoId);
    }

    private ResultadoEntrega enviarAoCommerce(Mediacao mediacao) {
        try {
            pedidos.aplicarDecisao(mediacao.pedidoId(), mediacao.decisaoId(), mediacao.decisao());
            return ResultadoEntrega.CONFIRMADA;
        } catch (FalhaNaEntregaException falha) {
            return new ResultadoEntrega(falha.getMessage(), falha.admiteNovaTentativa());
        }
    }

    private Mediacao registrar(UUID mediacaoId, ResultadoEntrega resultado) {
        var mediacao = mediacoes.buscarParaAtualizar(mediacaoId).orElseThrow(MediacaoService::naoEncontrada);
        if (!mediacao.aguardaEntregaDaDecisao()) {
            return mediacao;
        }
        if (resultado.confirmada()) {
            mediacao.confirmarEntregaDaDecisao(agora());
        } else if (resultado.admiteNovaTentativa()) {
            mediacao.registrarFalhaTemporariaNaEntrega(resultado.motivoDaFalha(), agora());
        } else {
            mediacao.registrarFalhaDefinitivaNaEntrega(resultado.motivoDaFalha(), agora());
        }
        avisarSeFalhou(mediacao);
        return mediacoes.atualizar(mediacao);
    }

    private static void avisarSeFalhou(Mediacao mediacao) {
        if (mediacao.entregaFalhou()) {
            LOG.error("Mediação {}: entrega da decisão ao commerce interrompida após {} tentativa(s); "
                    + "requer reprocessamento administrativo. Último erro: {}",
                    mediacao.id(), mediacao.tentativasEntrega(), mediacao.ultimoErroEntrega());
        } else if (mediacao.aguardaEntregaDaDecisao()) {
            LOG.warn("Mediação {}: tentativa {} de entregar a decisão falhou ({})",
                    mediacao.id(), mediacao.tentativasEntrega(), mediacao.ultimoErroEntrega());
        }
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(relogio);
    }

    private record ResultadoEntrega(String motivoDaFalha, boolean admiteNovaTentativa) {
        static final ResultadoEntrega CONFIRMADA = new ResultadoEntrega(null, false);

        boolean confirmada() {
            return motivoDaFalha == null;
        }
    }
}
