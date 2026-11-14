package br.com.balaio.community.conversa.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

import br.com.balaio.community.conversa.application.port.out.PedidosDaConversaPort;
import br.com.balaio.community.conversa.domain.ParteDaConversa;
import br.com.balaio.community.conversa.domain.PedidoDaConversa;
import br.com.balaio.community.shared.exception.ServicoIndisponivelException;
import br.com.balaio.community.shared.integration.HttpClientes;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@Component
class PedidosDaConversaHttpAdapter implements PedidosDaConversaPort {
    private final RestClient http;

    PedidosDaConversaHttpAdapter(@Value("${balaio.commerce-url}") String urlCommerce) {
        this.http = HttpClientes.criar(urlCommerce);
    }

    @Override
    public Optional<ParteDaConversa> parteNoPedido(UUID pedidoId, UsuarioAutenticado usuario) {
        if (usuario.possuiPapel(Papel.COMPRADOR)) {
            return compradorDoPedido(pedidoId, usuario) ? Optional.of(ParteDaConversa.COMPRADOR) : Optional.empty();
        }
        if (usuario.possuiPapel(Papel.ARTESAO)) {
            boolean vendeu = vendasDoArtesao(usuario).stream().anyMatch(pedido -> pedido.id().equals(pedidoId));
            return vendeu ? Optional.of(ParteDaConversa.ARTESAO) : Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public List<PedidoDaConversa> vendasDoArtesao(UsuarioAutenticado artesao) {
        try {
            JsonNode vendas = HttpClientes.dados(http.get().uri("/api/v1/artesao/vendas")
                    .header(HttpHeaders.AUTHORIZATION, HttpClientes.bearer(artesao.token()))
                    .retrieve().body(JsonNode.class));
            return StreamSupport.stream(vendas.spliterator(), false)
                    .map(venda -> new PedidoDaConversa(UUID.fromString(venda.path("id").asString()),
                            venda.path("compradorNome").asString(), venda.path("data").asString()))
                    .toList();
        } catch (RestClientException | IllegalStateException | IllegalArgumentException indisponivel) {
            throw commerceIndisponivel();
        }
    }

    private boolean compradorDoPedido(UUID pedidoId, UsuarioAutenticado comprador) {
        try {
            HttpClientes.dados(http.get().uri("/api/v1/pedidos/{id}", pedidoId)
                    .header(HttpHeaders.AUTHORIZATION, HttpClientes.bearer(comprador.token()))
                    .retrieve().body(JsonNode.class));
            return true;
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden naoEhDele) {
            return false;
        } catch (RestClientException | IllegalStateException indisponivel) {
            throw commerceIndisponivel();
        }
    }

    private static ServicoIndisponivelException commerceIndisponivel() {
        return new ServicoIndisponivelException("COMMERCE_INDISPONIVEL", "Não foi possível consultar o pedido.");
    }
}
