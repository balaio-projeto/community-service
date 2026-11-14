package br.com.balaio.community.colecao.persistence;

import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.balaio.community.colecao.application.port.out.ColecoesPort;
import br.com.balaio.community.colecao.domain.ColecaoDoUsuario;
import br.com.balaio.community.colecao.domain.TipoColecao;

@Component
class ColecoesJdbcAdapter implements ColecoesPort {
    private final JdbcClient jdbc;

    ColecoesJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ColecaoDoUsuario carregar(UUID usuarioId, TipoColecao tipo) {
        var itens = jdbc.sql("""
                SELECT valor FROM itens_colecao
                WHERE usuario_id = :usuario AND colecao = :colecao
                ORDER BY posicao
                """)
                .param("usuario", usuarioId).param("colecao", tipo.codigo())
                .query(String.class).list();
        return new ColecaoDoUsuario(usuarioId, tipo, itens);
    }

    @Override
    public void substituir(ColecaoDoUsuario colecao) {
        jdbc.sql("DELETE FROM itens_colecao WHERE usuario_id = :usuario AND colecao = :colecao")
                .param("usuario", colecao.usuarioId()).param("colecao", colecao.tipo().codigo())
                .update();
        IntStream.range(0, colecao.itens().size()).forEach(posicao -> jdbc.sql("""
                INSERT INTO itens_colecao (usuario_id, colecao, valor, posicao)
                VALUES (:usuario, :colecao, :valor, :posicao)
                """)
                .param("usuario", colecao.usuarioId()).param("colecao", colecao.tipo().codigo())
                .param("valor", colecao.itens().get(posicao)).param("posicao", posicao)
                .update());
    }
}
