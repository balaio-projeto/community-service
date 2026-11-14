package br.com.balaio.community.pergunta.application.port.out;

import java.util.Optional;

public interface PecasCatalogoPort {
    Optional<PecaCatalogo> buscar(String slug, Optional<String> tokenVisitante);

    record PecaCatalogo(String slug, String artesaoSlug) {
    }
}
