package br.com.balaio.community.pergunta.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.balaio.community.pergunta.domain.Pergunta;
import br.com.balaio.community.shared.paginacao.Pagina;

public interface PerguntasPort {
    Pergunta salvar(Pergunta pergunta);

    Optional<Pergunta> buscar(UUID id);

    List<Pergunta> listarDaPeca(String pecaSlug, Pagina pagina);
}
