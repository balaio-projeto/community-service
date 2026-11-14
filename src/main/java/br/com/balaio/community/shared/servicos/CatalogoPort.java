package br.com.balaio.community.shared.servicos;

import java.util.Optional;

public interface CatalogoPort {
    Optional<PecaCatalogo> buscar(String slug, Optional<String> tokenVisitante);

    record PecaCatalogo(String slug, String artesaoSlug) {
    }
}
