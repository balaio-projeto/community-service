package br.com.balaio.community.avaliacao.integration;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

import br.com.balaio.community.avaliacao.application.port.out.PedidosAvaliaveisPort;
import br.com.balaio.community.avaliacao.domain.PedidoAvaliavel;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.exception.ServicoIndisponivelException;
import br.com.balaio.community.shared.integration.HttpClientes;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@Component
class PedidosAvaliaveisHttpAdapter implements PedidosAvaliaveisPort {
    private final RestClient commerce;
    private final RestClient catalogo;

    PedidosAvaliaveisHttpAdapter(@Value("${balaio.commerce-url}") String urlCommerce,
            @Value("${balaio.catalog-url}") String urlCatalogo) {
        this.commerce = HttpClientes.criar(urlCommerce);
        this.catalogo = HttpClientes.criar(urlCatalogo);
    }

    @Override
    public PedidoAvaliavel buscarDoComprador(UUID pedidoId, String tokenComprador) {
        try {
            JsonNode pedido = HttpClientes.dados(commerce.get().uri("/api/v1/pedidos/{id}", pedidoId)
                    .header(HttpHeaders.AUTHORIZATION, HttpClientes.bearer(tokenComprador))
                    .retrieve().body(JsonNode.class));
            String pecaSlug = pedido.path("pecaSlug").asString();
            return new PedidoAvaliavel(pedidoId, UUID.fromString(pedido.path("compradorId").asString()),
                    artesaoDoPedido(pedido, pecaSlug, tokenComprador), pecaSlug, pedido.path("estado").asString());
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden naoEhDoComprador) {
            throw new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado.");
        } catch (RestClientException | IllegalStateException | IllegalArgumentException indisponivel) {
            throw new ServicoIndisponivelException("COMMERCE_INDISPONIVEL", "Não foi possível consultar o pedido.");
        }
    }

    private UUID artesaoDoPedido(JsonNode pedido, String pecaSlug, String tokenComprador) {
        String informadoPeloCommerce = pedido.path("artesaoId").asString();
        if (informadoPeloCommerce != null && !informadoPeloCommerce.isBlank()) {
            return UUID.fromString(informadoPeloCommerce);
        }
        return artesaoDaPeca(pecaSlug, tokenComprador);
    }

    private UUID artesaoDaPeca(String pecaSlug, String tokenComprador) {
        try {
            JsonNode peca = HttpClientes.dados(catalogo.get().uri("/api/v1/pecas/{slug}", pecaSlug)
                    .header(HttpHeaders.AUTHORIZATION, HttpClientes.bearer(tokenComprador))
                    .retrieve().body(JsonNode.class));
            String artesaoSlug = peca.path("artesao").asString();
            if (artesaoSlug == null || !artesaoSlug.startsWith(UsuarioAutenticado.PREFIXO_SLUG_ARTESAO)) {
                throw artesaoNaoIdentificado();
            }
            return UUID.fromString(artesaoSlug.substring(UsuarioAutenticado.PREFIXO_SLUG_ARTESAO.length()));
        } catch (RestClientException | IllegalArgumentException semPeca) {
            throw artesaoNaoIdentificado();
        }
    }

    private static ServicoIndisponivelException artesaoNaoIdentificado() {
        return new ServicoIndisponivelException("ARTESAO_NAO_IDENTIFICADO",
                "Não foi possível identificar o artesão deste pedido.");
    }
}
