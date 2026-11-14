package br.com.balaio.community.mediacao.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.balaio.community.mediacao.application.port.out.MediacoesPort;
import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort;
import br.com.balaio.community.mediacao.domain.Mediacao;

@Service
public class EntregaDecisaoService {
    public static final int MAXIMO_TENTATIVAS = 10;

    private final MediacoesPort mediacoes;
    private final PedidosCommercePort pedidos;
    private final Clock relogio;

    public EntregaDecisaoService(MediacoesPort mediacoes, PedidosCommercePort pedidos, Clock relogio) {
        this.mediacoes = mediacoes;
        this.pedidos = pedidos;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<UUID> aguardandoEntrega(int limite) {
        return mediacoes.idsAguardandoEntrega(MAXIMO_TENTATIVAS, limite);
    }

    @Transactional
    public Mediacao entregar(UUID mediacaoId) {
        var mediacao = mediacoes.buscarParaAtualizar(mediacaoId).orElseThrow(MediacaoService::naoEncontrada);
        if (!mediacao.aguardaEntregaDaDecisao()) {
            return mediacao;
        }
        try {
            pedidos.aplicarDecisao(mediacao.pedidoId(), mediacao.decisaoId(), mediacao.decisao());
            mediacao.confirmarEntregaDaDecisao(agora());
        } catch (RuntimeException falha) {
            mediacao.registrarFalhaNaEntrega(falha.getClass().getSimpleName(), agora());
        }
        return mediacoes.atualizar(mediacao);
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(relogio);
    }
}
