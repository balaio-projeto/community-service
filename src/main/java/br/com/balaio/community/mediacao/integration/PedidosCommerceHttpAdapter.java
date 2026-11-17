package br.com.balaio.community.mediacao.integration;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort;
import br.com.balaio.community.mediacao.domain.DecisaoMediacao;
import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.exception.ServicoIndisponivelException;
import br.com.balaio.community.shared.integration.ConsultasCommerceClient;
import br.com.balaio.community.shared.integration.HttpClientes;

@Component
class PedidosCommerceHttpAdapter implements PedidosCommercePort {
    private static final int TAMANHO_MINIMO_TOKEN_INTERNO = 32;
    private static final String CABECALHO_TOKEN_INTERNO = "X-Balaio-Service-Token";

    private final RestClient http;
    private final ConsultasCommerceClient consultasCommerce;
    private final String tokenInterno;

    PedidosCommerceHttpAdapter(@Value("${balaio.commerce-url}") String urlCommerce,
            @Value("${balaio.internal-token:}") String tokenInterno,
            ConsultasCommerceClient consultasCommerce) {
        this.http = HttpClientes.criar(urlCommerce);
        this.tokenInterno = tokenInterno;
        this.consultasCommerce = consultasCommerce;
    }

    @Override
    public void confirmarCompradorDoPedido(UUID pedidoId, String tokenComprador) {
        try {
            consultasCommerce.consultarPedido(pedidoId, tokenComprador);
        } catch (HttpClientErrorException.NotFound naoEncontrado) {
            throw new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado.");
        } catch (HttpClientErrorException.Forbidden semPermissao) {
            throw new AcessoNegadoException("Este pedido não pertence a você.");
        } catch (RestClientException | IllegalStateException indisponivel) {
            throw new ServicoIndisponivelException("COMMERCE_INDISPONIVEL", "Não foi possível consultar o pedido.");
        }
    }

    @Override
    public void aplicarDecisao(UUID pedidoId, UUID decisaoId, DecisaoMediacao decisao) {
        if (tokenInterno.length() < TAMANHO_MINIMO_TOKEN_INTERNO) {
            throw new IllegalStateException("Token interno não configurado.");
        }
        HttpClientes.dados(http.post().uri("/internal/v1/pedidos/{id}/decisao-mediacao", pedidoId)
                .header(CABECALHO_TOKEN_INTERNO, tokenInterno)
                .body(Map.of("decisaoId", decisaoId, "decisao", decisao.codigo()))
                .retrieve().body(JsonNode.class));
    }
}
