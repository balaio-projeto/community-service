package br.com.balaio.community.video.domain;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public final class Video {
    public static final int TAMANHO_MAXIMO_LEGENDA = 500;
    public static final int QUANTIDADE_MAXIMA_ETIQUETAS = 10;
    private static final Pattern FORMATO_ETIQUETA = Pattern.compile("[\\p{L}0-9-]{1,30}");
    private static final Pattern FORMATO_DURACAO = Pattern.compile("\\d{1,3}:[0-5]\\d");

    private final UUID id;
    private final UUID artesaoId;
    private final OffsetDateTime criadoEm;
    private String pecaSlug;
    private String legenda;
    private List<String> etiquetas;
    private String duracao;
    private SituacaoVideo situacao;
    private ArquivoMidia arquivo;
    private ArquivoMidia capa;
    private OffsetDateTime publicadoEm;
    private final long visualizacoes;
    private SuspensaoVideo suspensao;

    private Video(Dados dados) {
        this.id = dados.id();
        this.artesaoId = dados.artesaoId();
        this.criadoEm = dados.criadoEm();
        this.pecaSlug = dados.pecaSlug();
        this.legenda = dados.legenda();
        this.etiquetas = List.copyOf(dados.etiquetas());
        this.duracao = dados.duracao();
        this.situacao = dados.situacao();
        this.arquivo = dados.arquivo();
        this.capa = dados.capa();
        this.publicadoEm = dados.publicadoEm();
        this.visualizacoes = dados.visualizacoes();
        this.suspensao = dados.suspensao();
    }

    public static Video rascunho(UUID artesaoId, String pecaSlug, String legenda, List<String> etiquetas,
            String duracao, OffsetDateTime agora) {
        return new Video(new Dados(UUID.randomUUID(), artesaoId, agora, pecaSlug, legendaValida(legenda),
                etiquetasValidas(etiquetas), duracaoValida(duracao), SituacaoVideo.RASCUNHO, null, null, null, 0, null));
    }

    public static Video reconstituir(Dados dados) {
        return new Video(dados);
    }

    public void alterarDescricao(String novaLegenda, List<String> novasEtiquetas, String novaPeca) {
        if (novaLegenda != null) {
            legenda = legendaValida(novaLegenda);
        }
        if (novasEtiquetas != null) {
            etiquetas = etiquetasValidas(novasEtiquetas);
        }
        if (novaPeca != null) {
            pecaSlug = novaPeca.isBlank() ? null : novaPeca;
        }
    }

    public void anexarArquivo(ArquivoMidia novoArquivo, String novaDuracao) {
        arquivo = novoArquivo;
        if (novaDuracao != null) {
            duracao = duracaoValida(novaDuracao);
        }
    }

    public void anexarCapa(ArquivoMidia novaCapa) {
        capa = novaCapa;
    }

    public void mudarSituacao(SituacaoVideo nova, OffsetDateTime agora) {
        if (nova == SituacaoVideo.PUBLICADA && arquivo == null) {
            throw new ConflitoException("VIDEO_SEM_ARQUIVO", "Envie o arquivo do vídeo antes de publicar.");
        }
        if (nova == SituacaoVideo.PUBLICADA && situacao != SituacaoVideo.PUBLICADA) {
            publicadoEm = agora;
        }
        situacao = nova;
    }

    public boolean pertenceA(UUID usuarioId) {
        return artesaoId.equals(usuarioId);
    }

    public boolean visivelPara(UUID usuarioId) {
        return visivelAoPublico() || pertenceA(usuarioId);
    }

    public boolean visivelAoPublico() {
        return situacao == SituacaoVideo.PUBLICADA && !suspenso();
    }

    public boolean suspenso() {
        return suspensao != null;
    }

    public SuspensaoVideo suspender(String motivo, UUID administrador, OffsetDateTime agora) {
        if (suspenso()) {
            throw new ConflitoException("VIDEO_JA_SUSPENSO", "Este vídeo já está suspenso.");
        }
        suspensao = SuspensaoVideo.nova(motivo, administrador, agora);
        return suspensao;
    }

    public SuspensaoVideo reativar() {
        if (!suspenso()) {
            throw new ConflitoException("VIDEO_NAO_SUSPENSO", "Este vídeo não está suspenso.");
        }
        var encerrada = suspensao;
        suspensao = null;
        return encerrada;
    }

    public UUID id() { return id; }
    public UUID artesaoId() { return artesaoId; }
    public OffsetDateTime criadoEm() { return criadoEm; }
    public String pecaSlug() { return pecaSlug; }
    public String legenda() { return legenda; }
    public List<String> etiquetas() { return etiquetas; }
    public String duracao() { return duracao; }
    public SituacaoVideo situacao() { return situacao; }
    public ArquivoMidia arquivo() { return arquivo; }
    public ArquivoMidia capa() { return capa; }
    public OffsetDateTime publicadoEm() { return publicadoEm; }
    public long visualizacoes() { return visualizacoes; }
    public SuspensaoVideo suspensao() { return suspensao; }

    public boolean contaVisualizacaoDe(UUID espectadorId) {
        return visivelAoPublico() && !pertenceA(espectadorId);
    }

    private static String legendaValida(String legenda) {
        if (legenda == null || legenda.isBlank()) {
            throw new RequisicaoInvalidaException("Informe a legenda do vídeo.");
        }
        String normalizada = legenda.strip();
        if (normalizada.length() > TAMANHO_MAXIMO_LEGENDA) {
            throw new RequisicaoInvalidaException("A legenda deve ter até " + TAMANHO_MAXIMO_LEGENDA + " caracteres.");
        }
        return normalizada;
    }

    private static List<String> etiquetasValidas(List<String> etiquetas) {
        if (etiquetas == null) {
            return List.of();
        }
        var semRepeticao = new LinkedHashSet<String>();
        etiquetas.stream().filter(Objects::nonNull).map(etiqueta -> etiqueta.strip().toLowerCase())
                .filter(etiqueta -> !etiqueta.isEmpty()).forEach(semRepeticao::add);
        if (semRepeticao.size() > QUANTIDADE_MAXIMA_ETIQUETAS
                || semRepeticao.stream().anyMatch(etiqueta -> !FORMATO_ETIQUETA.matcher(etiqueta).matches())) {
            throw new RequisicaoInvalidaException("Etiquetas inválidas.");
        }
        return List.copyOf(semRepeticao);
    }

    private static String duracaoValida(String duracao) {
        if (duracao == null || duracao.isBlank()) {
            return null;
        }
        if (!FORMATO_DURACAO.matcher(duracao.strip()).matches()) {
            throw new RequisicaoInvalidaException("Duração inválida; use m:ss.");
        }
        return duracao.strip();
    }

    public record Dados(UUID id, UUID artesaoId, OffsetDateTime criadoEm, String pecaSlug, String legenda,
            List<String> etiquetas, String duracao, SituacaoVideo situacao, ArquivoMidia arquivo, ArquivoMidia capa,
            OffsetDateTime publicadoEm, long visualizacoes, SuspensaoVideo suspensao) {
    }
}
