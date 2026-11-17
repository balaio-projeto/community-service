package br.com.balaio.community.shared.integration;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

@Component
public class ConsultasCommerceClient {
    private final RestClient http;

    public ConsultasCommerceClient(@Value("${balaio.commerce-url}") String urlCommerce) {
        this.http = HttpClientes.criar(urlCommerce);
    }

    public JsonNode consultarPedido(UUID pedidoId, String token) {
        return HttpClientes.dados(http.get().uri("/api/v1/pedidos/{id}", pedidoId)
                .header(HttpHeaders.AUTHORIZATION, HttpClientes.bearer(token))
                .retrieve().body(JsonNode.class));
    }
}
