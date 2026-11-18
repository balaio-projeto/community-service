package br.com.balaio.community.mediacao.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface SpringDataMediacaoRepository extends JpaRepository<MediacaoJpaEntity, UUID> {
    boolean existsByPedidoId(UUID pedidoId);

    List<MediacaoJpaEntity> findAllByOrderByCriadaEmDesc(Pageable pagina);

    List<MediacaoJpaEntity> findByCompradorIdOrderByCriadaEmDesc(UUID compradorId, Pageable pagina);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediacaoJpaEntity m where m.id = :id")
    Optional<MediacaoJpaEntity> buscarComBloqueio(@Param("id") UUID id);

    @Query(nativeQuery = true, value = """
            SELECT id FROM mediacoes
            WHERE estado = 'decisao_pendente'
              AND atualizada_em < now() - make_interval(secs => LEAST(60, POWER(2, LEAST(tentativas, 6))))
            ORDER BY atualizada_em
            LIMIT :limite
            """)
    List<UUID> idsAguardandoEntrega(@Param("limite") int limite);
}
