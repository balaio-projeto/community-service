package br.com.balaio.community.conversa.domain;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public record ResumoConversa(PedidoDaConversa pedido, Optional<Mensagem> ultimaMensagem, boolean naoLida) {
    public static final String PREVIA_SEM_MENSAGENS = "Nova compra recebida";

    public static ResumoConversa para(UUID leitorId, PedidoDaConversa pedido, Optional<Mensagem> ultima,
            Optional<OffsetDateTime> lidaEm) {
        boolean naoLida = ultima.filter(mensagem -> !mensagem.foiEscritaPor(leitorId))
                .filter(mensagem -> lidaEm.map(lida -> mensagem.enviadaEm().isAfter(lida)).orElse(true))
                .isPresent();
        return new ResumoConversa(pedido, ultima, naoLida);
    }
}
