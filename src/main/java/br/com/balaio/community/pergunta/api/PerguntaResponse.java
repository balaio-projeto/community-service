package br.com.balaio.community.pergunta.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.balaio.community.pergunta.domain.Pergunta;

public record PerguntaResponse(UUID id, String pecaSlug, String pergunta, String autor, String autorImagem, UUID usuarioId,
        OffsetDateTime criadaEm, String resposta, OffsetDateTime respondidaEm) {

    static PerguntaResponse de(Pergunta pergunta, java.util.Map<UUID, String> imagens) {
        return new PerguntaResponse(pergunta.id(), pergunta.pecaSlug(), pergunta.texto(), pergunta.autorNome(),
                imagens.get(pergunta.autorId()), pergunta.autorId(), pergunta.criadaEm(), pergunta.resposta(), pergunta.respondidaEm());
    }
}
