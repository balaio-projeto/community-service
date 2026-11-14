package br.com.balaio.community.avaliacao.domain;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public record Avaliacao(UUID id, UUID pedidoId, UUID compradorId, UUID artesaoId, String pecaSlug, int nota,
        String comentario, List<String> aspectos, OffsetDateTime criadaEm) {

    public static final int NOTA_MINIMA = 1;
    public static final int NOTA_MAXIMA = 5;
    public static final int TAMANHO_MAXIMO_COMENTARIO = 1000;
    public static final int QUANTIDADE_MAXIMA_ASPECTOS = 10;
    public static final int TAMANHO_MAXIMO_ASPECTO = 60;

    public Avaliacao {
        aspectos = List.copyOf(aspectos);
    }

    public static Avaliacao registrar(PedidoAvaliavel pedido, Integer nota, String comentario,
            List<String> aspectos, OffsetDateTime agora) {
        if (!pedido.foiEntregue()) {
            throw pedidoNaoAvaliavel();
        }
        if (nota == null || nota < NOTA_MINIMA || nota > NOTA_MAXIMA) {
            throw new RequisicaoInvalidaException("Escolha uma nota entre 1 e 5.");
        }
        return new Avaliacao(UUID.randomUUID(), pedido.id(), pedido.compradorId(), pedido.artesaoId(),
                pedido.pecaSlug(), nota, comentarioValido(comentario), aspectosValidos(aspectos), agora);
    }

    public static ConflitoException pedidoNaoAvaliavel() {
        return new ConflitoException("PEDIDO_NAO_AVALIAVEL",
                "Este pedido ainda não pode ser avaliado ou já foi avaliado.");
    }

    private static String comentarioValido(String comentario) {
        if (comentario == null || comentario.isBlank()) {
            return null;
        }
        String normalizado = comentario.strip();
        if (normalizado.length() > TAMANHO_MAXIMO_COMENTARIO) {
            throw new RequisicaoInvalidaException("O comentário deve ter até " + TAMANHO_MAXIMO_COMENTARIO
                    + " caracteres.");
        }
        return normalizado;
    }

    private static List<String> aspectosValidos(List<String> aspectos) {
        if (aspectos == null) {
            return List.of();
        }
        var semRepeticao = new LinkedHashSet<String>();
        aspectos.stream().filter(Objects::nonNull).map(String::strip).filter(aspecto -> !aspecto.isEmpty())
                .forEach(semRepeticao::add);
        if (semRepeticao.size() > QUANTIDADE_MAXIMA_ASPECTOS
                || semRepeticao.stream().anyMatch(aspecto -> aspecto.length() > TAMANHO_MAXIMO_ASPECTO)) {
            throw new RequisicaoInvalidaException("Aspectos inválidos.");
        }
        return List.copyOf(semRepeticao);
    }
}
