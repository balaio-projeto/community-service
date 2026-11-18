package br.com.balaio.community.mediacao.application.port.out;

import java.util.UUID;

import br.com.balaio.community.mediacao.domain.DecisaoMediacao;

public interface PedidosCommercePort {
    void confirmarCompradorDoPedido(UUID pedidoId, String tokenComprador);

    boolean entregaDeDecisoesConfigurada();

    void aplicarDecisao(UUID pedidoId, UUID decisaoId, DecisaoMediacao decisao);

    class FalhaNaEntregaException extends RuntimeException {
        private final boolean admiteNovaTentativa;

        private FalhaNaEntregaException(String motivo, boolean admiteNovaTentativa, Throwable causa) {
            super(motivo, causa);
            this.admiteNovaTentativa = admiteNovaTentativa;
        }

        public static FalhaNaEntregaException temporaria(String motivo, Throwable causa) {
            return new FalhaNaEntregaException(motivo, true, causa);
        }

        public static FalhaNaEntregaException definitiva(String motivo, Throwable causa) {
            return new FalhaNaEntregaException(motivo, false, causa);
        }

        public boolean admiteNovaTentativa() {
            return admiteNovaTentativa;
        }
    }
}
