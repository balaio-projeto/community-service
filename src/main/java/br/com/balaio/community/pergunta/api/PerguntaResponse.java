package br.com.balaio.community.pergunta.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.balaio.community.pergunta.domain.Pergunta;

public record PerguntaResponse(UUID id, String pecaSlug, String pergunta, String autor, UUID usuarioId,
        OffsetDateTime criadaEm, String resposta, OffsetDateTime respondidaEm) {

    static PerguntaResponse de(Pergunta pergunta) {
        return new PerguntaResponse(pergunta.id(), pergunta.pecaSlug(), pergunta.texto(), pergunta.autorNome(),
                pergunta.autorId(), pergunta.criadaEm(), pergunta.resposta(), pergunta.respondidaEm());
    }
}
