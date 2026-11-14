package br.com.balaio.community.avaliacao.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAvaliacaoRepository extends JpaRepository<AvaliacaoJpaEntity, UUID> {
    boolean existsByPedidoId(UUID pedidoId);

    Optional<AvaliacaoJpaEntity> findByPedidoId(UUID pedidoId);

    @Query("select cast(a.nota as Integer) from AvaliacaoJpaEntity a where a.artesaoId = :artesaoId")
    List<Integer> notasDoArtesao(@Param("artesaoId") UUID artesaoId);
}
