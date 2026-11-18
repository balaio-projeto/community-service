package br.com.balaio.community.mediacao.api;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

import br.com.balaio.community.mediacao.domain.EstadoMediacao;
import br.com.balaio.community.mediacao.domain.Mediacao;

public record MediacaoResponse(UUID id, UUID pedido, UUID compradorId, String assunto, String relato,
        String estado, boolean emAnalise, String partes, String aberta, String decisao, int tentativas,
        String ultimoErro) {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String PARTES_ENVOLVIDAS = "Comprador e ateliê";

    static MediacaoResponse paraComprador(Mediacao mediacao) {
        return de(mediacao, null);
    }

    static MediacaoResponse paraAdministrador(Mediacao mediacao) {
        return de(mediacao, mediacao.ultimoErroEntrega());
    }

    private static MediacaoResponse de(Mediacao mediacao, String ultimoErroVisivel) {
        return new MediacaoResponse(mediacao.id(), mediacao.pedidoId(), mediacao.compradorId(),
                mediacao.assunto(), mediacao.relato(), mediacao.estado().codigo(),
                mediacao.estado() != EstadoMediacao.ABERTA, PARTES_ENVOLVIDAS,
                mediacao.criadaEm().format(FORMATO_DATA),
                mediacao.decisao() == null ? null : mediacao.decisao().codigo(),
                mediacao.tentativasEntrega(), ultimoErroVisivel);
    }
}
