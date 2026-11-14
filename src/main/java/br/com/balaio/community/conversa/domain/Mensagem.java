package br.com.balaio.community.conversa.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public record Mensagem(UUID id, UUID pedidoId, UUID autorId, ParteDaConversa autor, String texto,
        OffsetDateTime enviadaEm) {

    public static final int TAMANHO_MAXIMO_TEXTO = 2000;

    public static Mensagem enviar(UUID pedidoId, UUID autorId, ParteDaConversa autor, String texto,
            OffsetDateTime agora) {
        if (texto == null || texto.isBlank()) {
            throw new RequisicaoInvalidaException("Informe o texto.");
        }
        String normalizado = texto.strip();
        if (normalizado.length() > TAMANHO_MAXIMO_TEXTO) {
            throw new RequisicaoInvalidaException("A mensagem deve ter até " + TAMANHO_MAXIMO_TEXTO + " caracteres.");
        }
        return new Mensagem(UUID.randomUUID(), pedidoId, autorId, autor, normalizado, agora);
    }

    public boolean foiEscritaPor(UUID usuarioId) {
        return autorId.equals(usuarioId);
    }
}
