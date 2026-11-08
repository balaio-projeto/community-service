package br.com.balaio.community.mediation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class MediacaoErrorHandler {
    @ExceptionHandler(MediacaoException.class)
    public ResponseEntity<Map<String, Object>> regra(MediacaoException erro) {
        return resposta(erro.status(), erro.codigo(), erro.getMessage());
    }

    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    public ResponseEntity<Map<String, Object>> pedidoNaoEncontrado() {
        return resposta(404, "PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado.");
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> commerceIndisponivel() {
        return resposta(503, "COMMERCE_INDISPONIVEL", "Não foi possível consultar o pedido.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> corpoInvalido() {
        return resposta(400, "REQUISICAO_INVALIDA", "Dados inválidos.");
    }

    private ResponseEntity<Map<String, Object>> resposta(int status, String codigo, String mensagem) {
        var corpo = new LinkedHashMap<String, Object>();
        corpo.put("dados", null);
        corpo.put("erro", Map.of("codigo", codigo, "mensagem", mensagem));
        return ResponseEntity.status(status).body(corpo);
    }
}
