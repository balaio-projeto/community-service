package br.com.balaio.community.conversa.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.balaio.community.conversa.application.port.out.MensagensPort;
import br.com.balaio.community.conversa.domain.Mensagem;
import br.com.balaio.community.conversa.domain.ParteDaConversa;

@Component
class MensagensJdbcAdapter implements MensagensPort {
    private static final String COLUNAS = "id, pedido_id, autor_id, autor, texto, enviada_em";

    private final JdbcClient jdbc;

    MensagensJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Mensagem salvar(Mensagem mensagem) {
        jdbc.sql("INSERT INTO mensagens_pedido (" + COLUNAS + ") VALUES (:id, :pedido, :autorId, :autor, :texto, :em)")
                .param("id", mensagem.id()).param("pedido", mensagem.pedidoId())
                .param("autorId", mensagem.autorId()).param("autor", mensagem.autor().codigo())
                .param("texto", mensagem.texto()).param("em", mensagem.enviadaEm())
                .update();
        return mensagem;
    }

    @Override
    public List<Mensagem> listarDoPedido(UUID pedidoId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM mensagens_pedido WHERE pedido_id = :pedido ORDER BY enviada_em")
                .param("pedido", pedidoId).query(MensagensJdbcAdapter::mensagem).list();
    }

    @Override
    public Optional<Mensagem> ultimaDoPedido(UUID pedidoId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM mensagens_pedido WHERE pedido_id = :pedido "
                        + "ORDER BY enviada_em DESC LIMIT 1")
                .param("pedido", pedidoId).query(MensagensJdbcAdapter::mensagem).optional();
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
    public Optional<OffsetDateTime> ultimaLeitura(UUID pedidoId, UUID leitorId) {
        return jdbc.sql("SELECT lida_em FROM leituras_conversa WHERE pedido_id = :pedido AND leitor_id = :leitor")
                .param("pedido", pedidoId).param("leitor", leitorId)
                .query((rs, linha) -> rs.getObject("lida_em", OffsetDateTime.class)).optional();
    }

    private static Mensagem mensagem(ResultSet rs, int linha) throws SQLException {
        return new Mensagem(rs.getObject("id", UUID.class), rs.getObject("pedido_id", UUID.class),
                rs.getObject("autor_id", UUID.class), ParteDaConversa.deCodigo(rs.getString("autor")),
                rs.getString("texto"), rs.getObject("enviada_em", OffsetDateTime.class));
    }
}
