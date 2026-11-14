package br.com.balaio.community.video.api;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import br.com.balaio.community.video.application.VideoService.MidiaParaEntrega;

final class EntregaDeMidia {
    private static final String UNIDADE_BYTES = "bytes";

    private EntregaDeMidia() {
    }

    static ResponseEntity<StreamingResponseBody> responder(MidiaParaEntrega midia, String cabecalhoRange) {
        long tamanho = midia.conteudo().tamanho();
        List<HttpRange> trechos;
        try {
            trechos = cabecalhoRange == null ? List.of() : HttpRange.parseRanges(cabecalhoRange);
        } catch (IllegalArgumentException rangeInvalido) {
            return foraDoIntervalo(tamanho);
        }
        if (trechos.size() > 1) {
            return foraDoIntervalo(tamanho);
        }
        if (trechos.isEmpty()) {
            return corpo(HttpStatus.OK, midia, 0, tamanho - 1).build();
        }
        long inicio;
        long fim;
        try {
            inicio = trechos.getFirst().getRangeStart(tamanho);
            fim = trechos.getFirst().getRangeEnd(tamanho);
        } catch (IllegalArgumentException foraDoArquivo) {
            return foraDoIntervalo(tamanho);
        }
        if (inicio >= tamanho || fim < inicio) {
            return foraDoIntervalo(tamanho);
        }
        return corpo(HttpStatus.PARTIAL_CONTENT, midia, inicio, fim)
                .header(HttpHeaders.CONTENT_RANGE, UNIDADE_BYTES + " " + inicio + "-" + fim + "/" + tamanho)
                .build();
    }

    private static ConstrutorDeResposta corpo(HttpStatus status, MidiaParaEntrega midia, long inicio, long fim) {
        return new ConstrutorDeResposta(ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(midia.tipoConteudo()))
                .contentLength(fim - inicio + 1)
                .header(HttpHeaders.ACCEPT_RANGES, UNIDADE_BYTES)
                .cacheControl(CacheControl.noCache()), midia, inicio, fim);
    }

    private static ResponseEntity<StreamingResponseBody> foraDoIntervalo(long tamanho) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, UNIDADE_BYTES + " */" + tamanho).build();
    }

    private record ConstrutorDeResposta(ResponseEntity.BodyBuilder resposta, MidiaParaEntrega midia, long inicio,
            long fim) {
        ConstrutorDeResposta header(String nome, String valor) {
            resposta.header(nome, valor);
            return this;
        }

        ResponseEntity<StreamingResponseBody> build() {
            StreamingResponseBody envio = saida -> {
                try (var entrada = midia.conteudo().ler(inicio, fim)) {
                    entrada.transferTo(saida);
                }
            };
            return resposta.body(envio);
        }
    }
}
