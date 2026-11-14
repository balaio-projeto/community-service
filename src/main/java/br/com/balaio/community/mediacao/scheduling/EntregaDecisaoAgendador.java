package br.com.balaio.community.mediacao.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.balaio.community.mediacao.application.EntregaDecisaoService;

@Component
@ConditionalOnProperty(name = "balaio.worker.enabled", havingValue = "true", matchIfMissing = true)
public class EntregaDecisaoAgendador {
    private static final int LOTE_POR_EXECUCAO = 50;

    private final EntregaDecisaoService entregas;

    public EntregaDecisaoAgendador(EntregaDecisaoService entregas) {
        this.entregas = entregas;
    }

    @Scheduled(fixedDelayString = "${balaio.worker.intervalo-ms:5000}")
    public void entregarPendentes() {
        entregas.aguardandoEntrega(LOTE_POR_EXECUCAO).forEach(entregas::entregar);
    }
}
