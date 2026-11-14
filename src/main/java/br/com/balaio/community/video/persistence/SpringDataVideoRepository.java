package br.com.balaio.community.video.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataVideoRepository extends JpaRepository<VideoJpaEntity, UUID> {
    List<VideoJpaEntity> findBySituacaoOrderByPublicadoEmDesc(String situacao);

    List<VideoJpaEntity> findByArtesaoIdOrderByCriadoEmDesc(UUID artesaoId);
}
