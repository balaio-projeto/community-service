package br.com.balaio.community.video.persistence;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.balaio.community.video.application.port.out.ComentariosVideoPort;
import br.com.balaio.community.video.domain.ComentarioVideo;

@Component
class ComentariosVideoJdbcAdapter implements ComentariosVideoPort {
    private final JdbcClient jdbc;

    ComentariosVideoJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ComentarioVideo salvar(ComentarioVideo comentario) {
        jdbc.sql("""
                INSERT INTO comentarios_video (id, video_id, autor_id, autor_nome, autor_artesao, texto, criado_em)
                VALUES (:id, :video, :autor, :nome, :artesao, :texto, :em)
                """)
                .param("id", comentario.id()).param("video", comentario.videoId())
                .param("autor", comentario.autorId()).param("nome", comentario.autorNome())
                .param("artesao", comentario.autorEhArtesao()).param("texto", comentario.texto())
                .param("em", comentario.criadoEm())
                .update();
        return comentario;
    }

    @Override
    public List<ComentarioVideo> listarDoVideo(UUID videoId) {
        return jdbc.sql("""
                SELECT id, video_id, autor_id, autor_nome, autor_artesao, texto, criado_em
                FROM comentarios_video WHERE video_id = :video ORDER BY criado_em
                """)
                .param("video", videoId)
                .query((rs, linha) -> new ComentarioVideo(rs.getObject("id", UUID.class),
                        rs.getObject("video_id", UUID.class), rs.getObject("autor_id", UUID.class),
                        rs.getString("autor_nome"), rs.getBoolean("autor_artesao"), rs.getString("texto"),
                        rs.getObject("criado_em", OffsetDateTime.class)))
                .list();
    }

    @Override
    public Map<UUID, Long> contarPorVideo(Collection<UUID> videoIds) {
        if (videoIds.isEmpty()) {
            return Map.of();
        }
        var contagem = new HashMap<UUID, Long>();
        jdbc.sql("SELECT video_id, COUNT(*) AS total FROM comentarios_video WHERE video_id IN (:ids) GROUP BY video_id")
                .param("ids", List.copyOf(videoIds))
                .query((rs, linha) -> contagem.put(rs.getObject("video_id", UUID.class), rs.getLong("total")))
                .list();
        return contagem;
    }

    @Override
    public void removerDoVideo(UUID videoId) {
        jdbc.sql("DELETE FROM comentarios_video WHERE video_id = :video").param("video", videoId).update();
    }
}
