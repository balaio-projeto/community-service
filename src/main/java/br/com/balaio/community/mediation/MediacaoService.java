package br.com.balaio.community.mediation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MediacaoService {
    private final JdbcTemplate jdbc;
    private final CommercePedidoHttpAdapter commerce;
    private final TransactionTemplate transacoes;
    private final boolean workerAtivo;

    public MediacaoService(JdbcTemplate jdbc, CommercePedidoHttpAdapter commerce,
            TransactionTemplate transacoes, @Value("${balaio.worker.enabled:true}") boolean workerAtivo) {
        this.jdbc = jdbc;
        this.commerce = commerce;
        this.transacoes = transacoes;
        this.workerAtivo = workerAtivo;
    }

    @Transactional
    public Map<String, Object> criar(UUID pedido, UUID comprador, String assunto, String relato, String bearer) {
        if (pedido == null || comprador == null || vazio(assunto) || assunto.length() > 160 || vazio(relato))
            throw erro(400, "REQUISICAO_INVALIDA", "Informe pedido, assunto e relato.");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "mediacao-pedido:" + pedido);
        if (!jdbc.query("SELECT id FROM mediacoes WHERE pedido_id = ?", (rs, row) -> (UUID) rs.getObject(1), pedido).isEmpty())
            throw erro(409, "MEDIACAO_EXISTENTE", "Já existe uma mediação para este pedido.");
        commerce.verificarComprador(pedido, bearer);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mediacoes(id, pedido_id, comprador_id, assunto, relato, estado) VALUES (?, ?, ?, ?, ?, 'aberta')",
                id, pedido, comprador, assunto.trim(), relato.trim());
        return resposta(obter(id, false));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return jdbc.query("SELECT id FROM mediacoes ORDER BY criada_em DESC",
                (rs, row) -> (UUID) rs.getObject(1)).stream().map(id -> resposta(obter(id, false))).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarPorComprador(UUID comprador) {
        return jdbc.query("SELECT id FROM mediacoes WHERE comprador_id = ? ORDER BY criada_em DESC",
                (rs, row) -> (UUID) rs.getObject(1), comprador).stream()
                .map(id -> resposta(obter(id, false))).toList();
    }

    @Transactional
    public Map<String, Object> abrir(UUID id) {
        var mediacao = obter(id, true);
        if (!"aberta".equals(mediacao.estado()) && !"emAnalise".equals(mediacao.estado()))
            throw erro(409, "MEDIACAO_CONFLITANTE", "Mediação já decidida.");
        jdbc.update("UPDATE mediacoes SET estado = 'emAnalise', atualizada_em = now() WHERE id = ?", id);
        return resposta(obter(id, false));
    }

    @Transactional
    public Map<String, Object> decidir(UUID id, String decisao) {
        if (!List.of("cancelar_reembolsar", "negar", "manter_conversa").contains(decisao))
            throw erro(400, "REQUISICAO_INVALIDA", "Decisão inválida.");
        var mediacao = obter(id, true);
        if (mediacao.decisao() != null) {
            if (mediacao.decisao().equals(decisao)) return resposta(mediacao);
            throw erro(409, "MEDIACAO_CONFLITANTE", "Outra decisão já foi registrada.");
        }
        jdbc.update("UPDATE mediacoes SET estado = 'decisao_pendente', decisao = ?, decisao_id = ?, atualizada_em = now() WHERE id = ?",
                decisao, UUID.randomUUID(), id);
        return resposta(obter(id, false));
    }

    @Scheduled(fixedDelayString = "${balaio.worker.intervalo-ms:5000}")
    public void entregarPendentes() {
        if (!workerAtivo) return;
        var ids = jdbc.query("SELECT id FROM mediacoes WHERE estado = 'decisao_pendente' AND tentativas < 10 AND atualizada_em < now() - make_interval(secs => least(60, power(2, least(tentativas, 6)))::int) ORDER BY atualizada_em LIMIT 50",
                (rs, row) -> (UUID) rs.getObject(1));
        ids.forEach(this::entregar);
    }

    public void entregar(UUID id) {
        transacoes.executeWithoutResult(status -> {
            var mediacao = obter(id, true);
            if (!"decisao_pendente".equals(mediacao.estado())) return;
            try {
                commerce.aplicarDecisao(mediacao.pedido(), mediacao.decisaoId(), mediacao.decisao());
                jdbc.update("UPDATE mediacoes SET estado = 'decidida', ultimo_erro = NULL, tentativas = 0, atualizada_em = now() WHERE id = ?", id);
            } catch (RuntimeException falha) {
                jdbc.update("UPDATE mediacoes SET tentativas = tentativas + 1, ultimo_erro = ?, atualizada_em = now() WHERE id = ?",
                        falha.getClass().getSimpleName(), id);
            }
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> consultar(UUID id) { return resposta(obter(id, false)); }

    private Linha obter(UUID id, boolean bloquear) {
        String sql = "SELECT id, pedido_id, comprador_id, assunto, relato, estado, decisao, decisao_id, tentativas, criada_em FROM mediacoes WHERE id = ?"
                + (bloquear ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, row) -> new Linha((UUID) rs.getObject("id"),
                (UUID) rs.getObject("pedido_id"), (UUID) rs.getObject("comprador_id"),
                rs.getString("assunto"), rs.getString("relato"), rs.getString("estado"),
                rs.getString("decisao"), (UUID) rs.getObject("decisao_id"), rs.getInt("tentativas"),
                rs.getObject("criada_em", OffsetDateTime.class)), id)
                .stream().findFirst().orElseThrow(() -> erro(404, "MEDIACAO_NAO_ENCONTRADA", "Mediação não encontrada."));
    }

    private Map<String, Object> resposta(Linha linha) {
        var dados = new LinkedHashMap<String, Object>();
        dados.put("id", linha.id());
        dados.put("pedido", linha.pedido());
        dados.put("compradorId", linha.comprador());
        dados.put("assunto", linha.assunto());
        dados.put("relato", linha.relato());
        dados.put("estado", linha.estado());
        dados.put("emAnalise", !"aberta".equals(linha.estado()));
        dados.put("partes", "Comprador e ateliê");
        dados.put("aberta", linha.criadaEm().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        dados.put("decisao", linha.decisao());
        dados.put("tentativas", linha.tentativas());
        return dados;
    }

    private boolean vazio(String texto) { return texto == null || texto.isBlank(); }
    private MediacaoException erro(int status, String codigo, String mensagem) {
        return new MediacaoException(status, codigo, mensagem);
    }
    private record Linha(UUID id, UUID pedido, UUID comprador, String assunto, String relato,
                         String estado, String decisao, UUID decisaoId, int tentativas,
                         OffsetDateTime criadaEm) { }
}
