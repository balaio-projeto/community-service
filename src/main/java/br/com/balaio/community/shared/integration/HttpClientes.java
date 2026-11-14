package br.com.balaio.community.shared.integration;

import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

public final class HttpClientes {
    private static final Duration TEMPO_CONEXAO = Duration.ofSeconds(2);
    private static final Duration TEMPO_LEITURA = Duration.ofSeconds(3);

    private HttpClientes() {
    }

    public static RestClient criar(String urlBase) {
        var requisicoes = new SimpleClientHttpRequestFactory();
        requisicoes.setConnectTimeout(TEMPO_CONEXAO);
        requisicoes.setReadTimeout(TEMPO_LEITURA);
        return RestClient.builder().baseUrl(urlBase).requestFactory(requisicoes).build();
    }

    public static JsonNode dados(JsonNode envelope) {
        if (envelope == null || envelope.path("dados").isNull() || envelope.path("dados").isMissingNode()) {
            throw new IllegalStateException("Resposta sem dados.");
        }
        return envelope.path("dados");
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
