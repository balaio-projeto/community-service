package br.com.balaio.community.mediacao.integration;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

import br.com.balaio.community.mediacao.application.port.out.PedidosCommercePort;
import br.com.balaio.community.mediacao.domain.DecisaoMediacao;
import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.exception.ServicoIndisponivelException;
import br.com.balaio.community.shared.integration.HttpClientes;

@Component
class PedidosCommerceHttpAdapter implements PedidosCommercePort {
    private static final Logger LOG = LoggerFactory.getLogger(PedidosCommerceHttpAdapter.class);
    private static final int TAMANHO_MINIMO_TOKEN_INTERNO = 32;
    private static final String CABECALHO_TOKEN_INTERNO = "X-Balaio-Service-Token";

    private final RestClient http;
    private final String tokenInterno;

    PedidosCommerceHttpAdapter(@Value("${balaio.commerce-url}") String urlCommerce,
            @Value("${balaio.internal-token:}") String tokenInterno) {
        this.http = HttpClientes.criar(urlCommerce);
        this.tokenInterno = validarTokenInterno(tokenInterno);
    }

    private static String validarTokenInterno(String token) {
        if (token.isBlank()) {
            LOG.warn("balaio.internal-token não definido: decisões de mediação ficam pendentes até a configuração.");
            return "";
        }
        if (token.length() < TAMANHO_MINIMO_TOKEN_INTERNO) {
            throw new IllegalStateException("balaio.internal-token deve ter ao menos "
                    + TAMANHO_MINIMO_TOKEN_INTERNO + " caracteres.");
        }
        return token;
    }

    @Override
    public boolean entregaDeDecisoesConfigurada() {
        return !tokenInterno.isEmpty();
    }

    @Override
    public void confirmarCompradorDoPedido(UUID pedidoId, String tokenComprador) {
        try {
            HttpClientes.dados(http.get().uri("/api/v1/pedidos/{id}", pedidoId)
                    .header(HttpHeaders.AUTHORIZATION, HttpClientes.bearer(tokenComprador))
                    .retrieve().body(JsonNode.class));
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
        if (!entregaDeDecisoesConfigurada()) {
            throw new IllegalStateException("Entrega de decisões sem token interno configurado.");
        }
        try {
            HttpClientes.dados(http.post().uri("/internal/v1/pedidos/{id}/decisao-mediacao", pedidoId)
                    .header(CABECALHO_TOKEN_INTERNO, tokenInterno)
                    .body(Map.of("decisaoId", decisaoId, "decisao", decisao.codigo()))
                    .retrieve().body(JsonNode.class));
        } catch (HttpStatusCodeException recusada) {
            throw new EntregaRecusadaException("commerce respondeu HTTP " + recusada.getStatusCode().value(), recusada);
        } catch (RestClientException | IllegalStateException semResposta) {
            throw new EntregaRecusadaException("commerce indisponível: " + semResposta.getClass().getSimpleName(),
                    semResposta);
        }
    }
}
