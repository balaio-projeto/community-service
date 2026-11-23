package br.com.balaio.community.mediacao.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public final class Mediacao {
    public static final int TAMANHO_MAXIMO_ASSUNTO = 160;
    public static final int MAXIMO_TENTATIVAS_ENTREGA = 10;

    private final UUID id;
    private final UUID pedidoId;
    private final UUID compradorId;
    private final UUID artesaoId;
    private final String assunto;
    private final String relato;
    private final OffsetDateTime criadaEm;
    private EstadoMediacao estado;
    private DecisaoMediacao decisao;
    private UUID decisaoId;
    private int tentativasEntrega;
    private String ultimoErroEntrega;
    private OffsetDateTime atualizadaEm;

    private Mediacao(Dados dados) {
        this.id = dados.id();
        this.pedidoId = dados.pedidoId();
        this.compradorId = dados.compradorId();
        this.artesaoId = dados.artesaoId();
        this.assunto = dados.assunto();
        this.relato = dados.relato();
        this.criadaEm = dados.criadaEm();
        this.estado = dados.estado();
        this.decisao = dados.decisao();
        this.decisaoId = dados.decisaoId();
        this.tentativasEntrega = dados.tentativasEntrega();
        this.ultimoErroEntrega = dados.ultimoErroEntrega();
        this.atualizadaEm = dados.atualizadaEm();
    }

    public static Mediacao abrir(UUID pedidoId, UUID compradorId, String assunto, String relato,
            OffsetDateTime agora) {
        if (pedidoId == null || compradorId == null || estaVazio(assunto) || estaVazio(relato)
                || assunto.strip().length() > TAMANHO_MAXIMO_ASSUNTO) {
            throw new RequisicaoInvalidaException("Informe pedido, assunto e relato.");
        }
        return new Mediacao(new Dados(UUID.randomUUID(), pedidoId, compradorId, null, assunto.strip(),
                relato.strip(), EstadoMediacao.ABERTA, null, null, 0, null, agora, agora));
    }

    public Mediacao doArtesao(UUID artesaoDoPedido) {
        return new Mediacao(new Dados(id, pedidoId, compradorId, artesaoDoPedido, assunto, relato, estado, decisao,
                decisaoId, tentativasEntrega, ultimoErroEntrega, criadaEm, atualizadaEm));
    }

    public static Mediacao reconstituir(Dados dados) {
        return new Mediacao(dados);
    }

    public void iniciarAnalise(OffsetDateTime agora) {
        if (!estado.aceitaAnalise()) {
            throw new ConflitoException("MEDIACAO_CONFLITANTE", "Mediação já decidida.");
        }
        estado = EstadoMediacao.EM_ANALISE;
        atualizadaEm = agora;
    }

    public void registrarDecisao(DecisaoMediacao escolhida, OffsetDateTime agora) {
        if (decisao == escolhida) {
            return;
        }
        if (decisao != null) {
            throw new ConflitoException("MEDIACAO_CONFLITANTE", "Outra decisão já foi registrada.");
        }
        decisao = escolhida;
        decisaoId = UUID.randomUUID();
        estado = EstadoMediacao.DECISAO_PENDENTE;
        atualizadaEm = agora;
    }

    public boolean aguardaEntregaDaDecisao() {
        return estado == EstadoMediacao.DECISAO_PENDENTE;
    }

    public void confirmarEntregaDaDecisao(OffsetDateTime agora) {
        estado = EstadoMediacao.DECIDIDA;
        tentativasEntrega = 0;
        ultimoErroEntrega = null;
        atualizadaEm = agora;
    }

    public void registrarFalhaTemporariaNaEntrega(String motivo, OffsetDateTime agora) {
        contarFalha(motivo, agora);
        if (tentativasEntrega >= MAXIMO_TENTATIVAS_ENTREGA) {
            estado = EstadoMediacao.FALHA_ENTREGA;
        }
    }

    public void registrarFalhaDefinitivaNaEntrega(String motivo, OffsetDateTime agora) {
        contarFalha(motivo, agora);
        estado = EstadoMediacao.FALHA_ENTREGA;
    }

    public boolean entregaFalhou() {
        return estado == EstadoMediacao.FALHA_ENTREGA;
    }

    public void reabrirEntregaDaDecisao(OffsetDateTime agora) {
        if (!entregaFalhou()) {
            throw new ConflitoException("MEDIACAO_CONFLITANTE", "A entrega desta decisão não está em falha.");
        }
        estado = EstadoMediacao.DECISAO_PENDENTE;
        tentativasEntrega = 0;
        atualizadaEm = agora;
    }

    private void contarFalha(String motivo, OffsetDateTime agora) {
        tentativasEntrega++;
        ultimoErroEntrega = motivo;
        atualizadaEm = agora;
    }

    public UUID id() { return id; }
    public UUID pedidoId() { return pedidoId; }
    public UUID compradorId() { return compradorId; }
    public UUID artesaoId() { return artesaoId; }
    public String assunto() { return assunto; }
    public String relato() { return relato; }
    public OffsetDateTime criadaEm() { return criadaEm; }
    public EstadoMediacao estado() { return estado; }
    public DecisaoMediacao decisao() { return decisao; }
    public UUID decisaoId() { return decisaoId; }
    public int tentativasEntrega() { return tentativasEntrega; }
    public String ultimoErroEntrega() { return ultimoErroEntrega; }
    public OffsetDateTime atualizadaEm() { return atualizadaEm; }

    private static boolean estaVazio(String texto) {
        return texto == null || texto.isBlank();
    }

    public record Dados(UUID id, UUID pedidoId, UUID compradorId, UUID artesaoId, String assunto, String relato,
            EstadoMediacao estado, DecisaoMediacao decisao, UUID decisaoId, int tentativasEntrega,
            String ultimoErroEntrega, OffsetDateTime criadaEm, OffsetDateTime atualizadaEm) {
    }
}
