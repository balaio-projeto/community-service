package br.com.balaio.community.pergunta.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPerguntaRepository extends JpaRepository<PerguntaJpaEntity, UUID> {
    List<PerguntaJpaEntity> findByPecaSlugOrderByCriadaEmAsc(String pecaSlug);
}
