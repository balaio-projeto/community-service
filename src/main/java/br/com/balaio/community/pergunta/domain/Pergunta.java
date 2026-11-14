package br.com.balaio.community.pergunta.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public final class Pergunta {
    public static final int TAMANHO_MAXIMO_PERGUNTA = 1000;
    public static final int TAMANHO_MAXIMO_RESPOSTA = 2000;
    public static final int TAMANHO_MAXIMO_NOME_AUTOR = 120;

    private final UUID id;
    private final String pecaSlug;
    private final UUID autorId;
    private final String autorNome;
    private final String texto;
    private final OffsetDateTime criadaEm;
    private String resposta;
    private OffsetDateTime respondidaEm;

    private Pergunta(Dados dados) {
        this.id = dados.id();
        this.pecaSlug = dados.pecaSlug();
        this.autorId = dados.autorId();
        this.autorNome = dados.autorNome();
        this.texto = dados.texto();
        this.criadaEm = dados.criadaEm();
        this.resposta = dados.resposta();
        this.respondidaEm = dados.respondidaEm();
    }

    public static Pergunta fazer(String pecaSlug, UUID autorId, String autorNome, String texto,
            OffsetDateTime agora) {
        String pergunta = textoValido(texto, TAMANHO_MAXIMO_PERGUNTA, "Escreva sua pergunta.");
        return new Pergunta(new Dados(UUID.randomUUID(), pecaSlug, autorId, nomeExibivel(autorNome), pergunta,
                agora, null, null));
    }

    public static Pergunta reconstituir(Dados dados) {
        return new Pergunta(dados);
    }

    public void responder(String texto, OffsetDateTime agora) {
        resposta = textoValido(texto, TAMANHO_MAXIMO_RESPOSTA, "Escreva a resposta.");
        respondidaEm = agora;
    }

    public boolean pertenceAPeca(String slug) {
        return pecaSlug.equals(slug);
    }

    public UUID id() { return id; }
    public String pecaSlug() { return pecaSlug; }
    public UUID autorId() { return autorId; }
    public String autorNome() { return autorNome; }
    public String texto() { return texto; }
    public OffsetDateTime criadaEm() { return criadaEm; }
    public String resposta() { return resposta; }
    public OffsetDateTime respondidaEm() { return respondidaEm; }

    private static String textoValido(String texto, int tamanhoMaximo, String mensagemSeVazio) {
        if (texto == null || texto.isBlank()) {
            throw new RequisicaoInvalidaException(mensagemSeVazio);
        }
        String normalizado = texto.strip();
        if (normalizado.length() > tamanhoMaximo) {
            throw new RequisicaoInvalidaException("O texto deve ter até " + tamanhoMaximo + " caracteres.");
        }
        return normalizado;
    }

    private static String nomeExibivel(String nome) {
        String normalizado = nome == null || nome.isBlank() ? "Comprador" : nome.strip();
        return normalizado.length() > TAMANHO_MAXIMO_NOME_AUTOR
                ? normalizado.substring(0, TAMANHO_MAXIMO_NOME_AUTOR)
                : normalizado;
    }

    public record Dados(UUID id, String pecaSlug, UUID autorId, String autorNome, String texto,
            OffsetDateTime criadaEm, String resposta, OffsetDateTime respondidaEm) {
    }
}
