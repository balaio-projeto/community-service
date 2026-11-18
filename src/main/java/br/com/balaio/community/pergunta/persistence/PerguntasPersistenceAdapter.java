package br.com.balaio.community.pergunta.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import br.com.balaio.community.pergunta.application.port.out.PerguntasPort;
import br.com.balaio.community.pergunta.domain.Pergunta;
import br.com.balaio.community.shared.paginacao.Pagina;

@Component
class PerguntasPersistenceAdapter implements PerguntasPort {
    private final SpringDataPerguntaRepository repositorio;

    PerguntasPersistenceAdapter(SpringDataPerguntaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Pergunta salvar(Pergunta pergunta) {
        return repositorio.save(PerguntaJpaEntity.de(pergunta)).paraDominio();
    }

    @Override
    public Optional<Pergunta> buscar(UUID id) {
        return repositorio.findById(id).map(PerguntaJpaEntity::paraDominio);
    }

    @Override
    public List<Pergunta> listarDaPeca(String pecaSlug, Pagina pagina) {
        return repositorio.findByPecaSlugOrderByCriadaEmAsc(pecaSlug,
                        PageRequest.of(pagina.numero(), pagina.tamanho())).stream()
                .map(PerguntaJpaEntity::paraDominio).toList();
    }
}
