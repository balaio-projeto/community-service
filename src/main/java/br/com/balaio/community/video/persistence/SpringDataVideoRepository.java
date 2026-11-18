package br.com.balaio.community.video.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataVideoRepository extends JpaRepository<VideoJpaEntity, UUID> {
    List<VideoJpaEntity> findBySituacaoOrderByPublicadoEmDesc(String situacao, Pageable pagina);

    List<VideoJpaEntity> findByArtesaoIdOrderByCriadoEmDesc(UUID artesaoId, Pageable pagina);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE videos SET visualizacoes = visualizacoes + 1 WHERE id = :id")
    int somarVisualizacao(@Param("id") UUID id);
}
