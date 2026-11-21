package br.com.balaio.community.conversa.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.balaio.community.conversa.application.port.out.MensagensPort;
import br.com.balaio.community.conversa.domain.Mensagem;
import br.com.balaio.community.conversa.domain.ParteDaConversa;
import br.com.balaio.community.shared.exception.ConflitoException;

@Component
class MensagensJdbcAdapter implements MensagensPort {
    private static final String COLUNAS = "id, pedido_id, autor_id, autor, texto, enviada_em";

    private final JdbcClient jdbc;

    MensagensJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Mensagem salvarIdempotente(Mensagem mensagem, String chave) {
        int inseridas = jdbc.sql("INSERT INTO mensagens_pedido (" + COLUNAS + ", chave_idempotencia) "
                        + "VALUES (:id, :pedido, :autorId, :autor, :texto, :em, :chave) "
                        + "ON CONFLICT (autor_id, chave_idempotencia) WHERE chave_idempotencia IS NOT NULL DO NOTHING")
                .param("id", mensagem.id()).param("pedido", mensagem.pedidoId())
                .param("autorId", mensagem.autorId()).param("autor", mensagem.autor().codigo())
                .param("texto", mensagem.texto()).param("em", mensagem.enviadaEm()).param("chave", chave)
                .update();
        if (inseridas == 1) return mensagem;
        var existente = jdbc.sql("SELECT " + COLUNAS + " FROM mensagens_pedido "
                        + "WHERE autor_id = :autorId AND chave_idempotencia = :chave")
                .param("autorId", mensagem.autorId()).param("chave", chave)
                .query(MensagensJdbcAdapter::mensagem).single();
        if (!existente.pedidoId().equals(mensagem.pedidoId()) || !existente.texto().equals(mensagem.texto())) {
            throw new ConflitoException("CHAVE_IDEMPOTENCIA_REUTILIZADA",
                    "A chave de idempotência já foi usada para outra mensagem.");
        }
        return existente;
    }

    @Override
    public List<Mensagem> listarDoPedido(UUID pedidoId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM mensagens_pedido WHERE pedido_id = :pedido ORDER BY enviada_em")
                .param("pedido", pedidoId).query(MensagensJdbcAdapter::mensagem).list();
    }

    @Override
    public Map<UUID, Mensagem> ultimasDosPedidos(Collection<UUID> pedidoIds) {
        if (pedidoIds.isEmpty()) {
            return Map.of();
        }
        var ultimas = new HashMap<UUID, Mensagem>();
        jdbc.sql("SELECT DISTINCT ON (pedido_id) " + COLUNAS + " FROM mensagens_pedido "
                        + "WHERE pedido_id IN (:pedidos) ORDER BY pedido_id, enviada_em DESC")
                .param("pedidos", List.copyOf(pedidoIds)).query(MensagensJdbcAdapter::mensagem).list()
                .forEach(mensagem -> ultimas.put(mensagem.pedidoId(), mensagem));
        return ultimas;
    }

    @Override
    public void registrarLeitura(UUID pedidoId, UUID leitorId, OffsetDateTime lidaEm) {
        jdbc.sql("""
                INSERT INTO leituras_conversa (pedido_id, leitor_id, lida_em) VALUES (:pedido, :leitor, :lida)
                ON CONFLICT (pedido_id, leitor_id) DO UPDATE
                SET lida_em = GREATEST(leituras_conversa.lida_em, EXCLUDED.lida_em)
                """)
                .param("pedido", pedidoId).param("leitor", leitorId).param("lida", lidaEm)
                .update();
    }

    @Override
    public Map<UUID, OffsetDateTime> leiturasDoLeitor(Collection<UUID> pedidoIds, UUID leitorId) {
        if (pedidoIds.isEmpty()) {
            return Map.of();
        }
        var leituras = new HashMap<UUID, OffsetDateTime>();
        jdbc.sql("SELECT pedido_id, lida_em FROM leituras_conversa WHERE leitor_id = :leitor AND pedido_id IN (:pedidos)")
                .param("leitor", leitorId).param("pedidos", List.copyOf(pedidoIds))
                .query((rs, linha) -> leituras.put(rs.getObject("pedido_id", UUID.class),
                        rs.getObject("lida_em", OffsetDateTime.class)))
                .list();
        return leituras;
    }

    private static Mensagem mensagem(ResultSet rs, int linha) throws SQLException {
        return new Mensagem(rs.getObject("id", UUID.class), rs.getObject("pedido_id", UUID.class),
                rs.getObject("autor_id", UUID.class), ParteDaConversa.deCodigo(rs.getString("autor")),
                rs.getString("texto"), rs.getObject("enviada_em", OffsetDateTime.class));
    }
}
