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
import br.com.balaio.community.shared.integration.RespostaIncompativelException;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@Component
class PedidosDaConversaHttpAdapter implements PedidosDaConversaPort {
    private final RestClient http;
    private final String tokenInterno;

    PedidosDaConversaHttpAdapter(@Value("${balaio.commerce-url}") String urlCommerce,
            @Value("${balaio.internal-token:}") String tokenInterno) {
        this.http = HttpClientes.criar(urlCommerce);
        this.tokenInterno = tokenInterno;
    }

    @Override
    public Optional<ParteDaConversa> parteNoPedido(UUID pedidoId, UsuarioAutenticado usuario) {
        if (!usuario.podeComprar() && !usuario.possuiPapel(Papel.ARTESAO))
            return Optional.empty();
        try {
            JsonNode partes = HttpClientes.dados(http.get()
                    .uri("/internal/v1/pedidos/{id}/participantes", pedidoId)
                    .header("X-Balaio-Service-Token", tokenInterno)
                    .retrieve().body(JsonNode.class));
            UUID compradorId = UUID.fromString(partes.path("compradorId").asString());
            UUID artesaoId = UUID.fromString(partes.path("artesaoId").asString());
            if (usuario.podeComprar() && usuario.id().equals(compradorId))
                return Optional.of(ParteDaConversa.COMPRADOR);
            if (usuario.possuiPapel(Papel.ARTESAO)
                    && usuario.id().equals(artesaoId))
                return Optional.of(ParteDaConversa.ARTESAO);
            return Optional.empty();
        } catch (HttpClientErrorException.NotFound inexistente) {
            return Optional.empty();
        } catch (RestClientException | RespostaIncompativelException | IllegalArgumentException indisponivel) {
            throw commerceIndisponivel();
        }
    }

    @Override
    public List<PedidoDaConversa> vendasDoArtesao(UsuarioAutenticado artesao) {
        try {
            JsonNode vendas = HttpClientes.dados(http.get().uri("/api/v1/artesao/vendas")
                    .header(HttpHeaders.AUTHORIZATION, HttpClientes.bearer(artesao.token()))
                    .retrieve().body(JsonNode.class));
            return StreamSupport.stream(vendas.spliterator(), false)
                    .map(PedidosDaConversaHttpAdapter::pedidoDaVenda)
                    .toList();
        } catch (RestClientException | RespostaIncompativelException | IllegalArgumentException indisponivel) {
            throw commerceIndisponivel();
        }
    }

    private static PedidoDaConversa pedidoDaVenda(JsonNode venda) {
        JsonNode comprador = venda.path("comprador");
        String nome = comprador.isObject() ? comprador.path("nome").asString("") : venda.path("compradorNome").asString("");
        String imagem = comprador.path("imagem").asString("");
        return new PedidoDaConversa(UUID.fromString(venda.path("id").asString()), nome.isBlank() ? null : nome,
                venda.path("data").asString(), imagem.isBlank() ? null : imagem);
    }

    private static ServicoIndisponivelException commerceIndisponivel() {
        return new ServicoIndisponivelException("COMMERCE_INDISPONIVEL", "Não foi possível consultar o pedido.");
    }
}
