package br.com.balaio.community.mediation;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

@Component
public class CommercePedidoHttpAdapter {
    private final RestClient http;
    private final String tokenInterno;

    public CommercePedidoHttpAdapter(@Value("${balaio.commerce-url}") String url,
            @Value("${balaio.internal-token:}") String tokenInterno) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.tokenInterno = tokenInterno;
    }

    public void verificarComprador(UUID pedido, String bearer) {
        JsonNode resposta = http.get().uri("/api/v1/pedidos/{id}", pedido)
                .header("Authorization", bearer).retrieve().body(JsonNode.class);
        if (resposta == null || resposta.path("dados").isNull())
            throw new IllegalStateException("Resposta inválida do commerce.");
    }

    public void aplicarDecisao(UUID pedido, UUID decisaoId, String decisao) {
        if (tokenInterno.length() < 32) throw new IllegalStateException("Token interno não configurado.");
        JsonNode resposta = http.post().uri("/internal/v1/pedidos/{id}/decisao-mediacao", pedido)
                .header("X-Balaio-Service-Token", tokenInterno)
                .body(Map.of("decisaoId", decisaoId, "decisao", decisao))
                .retrieve().body(JsonNode.class);
        if (resposta == null || resposta.path("dados").isNull())
            throw new IllegalStateException("Resposta inválida do commerce.");
    }
}
