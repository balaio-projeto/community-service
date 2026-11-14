package br.com.balaio.community.avaliacao.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public record Reputacao(BigDecimal media, int total, Map<Integer, Long> distribuicao) {

    public static Reputacao das(List<Integer> notas) {
        var distribuicao = new LinkedHashMap<Integer, Long>();
        IntStream.rangeClosed(Avaliacao.NOTA_MINIMA, Avaliacao.NOTA_MAXIMA)
                .forEach(nota -> distribuicao.put(nota, notas.stream().filter(n -> n == nota).count()));
        return new Reputacao(media(notas), notas.size(), Collections.unmodifiableMap(distribuicao));
    }

    private static BigDecimal media(List<Integer> notas) {
        if (notas.isEmpty()) {
            return null;
        }
        int soma = notas.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(soma).divide(BigDecimal.valueOf(notas.size()), 1, RoundingMode.HALF_UP);
    }
}
