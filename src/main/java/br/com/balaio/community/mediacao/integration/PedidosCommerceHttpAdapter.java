package br.com.balaio.community.mediacao.integration;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

@Component
class PedidosCommerceHttpAdapter implements PedidosCommercePort {
    private static final Duration TEMPO_CONEXAO = Duration.ofSeconds(2);
    private static final Duration TEMPO_LEITURA = Duration.ofSeconds(3);
    private static final int TAMANHO_MINIMO_TOKEN_INTERNO = 32;
    private static final String CABECALHO_TOKEN_INTERNO = "X-Balaio-Service-Token";

    private final RestClient http;
    private final String tokenInterno;

    PedidosCommerceHttpAdapter(@Value("${balaio.commerce-url}") String urlCommerce,
            @Value("${balaio.internal-token:}") String tokenInterno) {
        var requisicoes = new SimpleClientHttpRequestFactory();
        requisicoes.setConnectTimeout(TEMPO_CONEXAO);
        requisicoes.setReadTimeout(TEMPO_LEITURA);
        this.http = RestClient.builder().baseUrl(urlCommerce).requestFactory(requisicoes).build();
        this.tokenInterno = tokenInterno;
    }

    @Override
    public void confirmarCompradorDoPedido(UUID pedidoId, String tokenComprador) {
        JsonNode resposta;
        try {
            resposta = http.get().uri("/api/v1/pedidos/{id}", pedidoId)
                    .header("Authorization", "Bearer " + tokenComprador)
                    .retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound naoEncontrado) {
            throw new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado.");
        } catch (HttpClientErrorException.Forbidden semPermissao) {
            throw new AcessoNegadoException("Este pedido não pertence a você.");
        } catch (RestClientException indisponivel) {
            throw commerceIndisponivel();
        }
        if (!possuiDados(resposta)) {
            throw commerceIndisponivel();
        }
    }

    @Override
    public void aplicarDecisao(UUID pedidoId, UUID decisaoId, DecisaoMediacao decisao) {
        if (tokenInterno.length() < TAMANHO_MINIMO_TOKEN_INTERNO) {
            throw new IllegalStateException("Token interno não configurado.");
        }
        JsonNode resposta = http.post().uri("/internal/v1/pedidos/{id}/decisao-mediacao", pedidoId)
                .header(CABECALHO_TOKEN_INTERNO, tokenInterno)
                .body(Map.of("decisaoId", decisaoId, "decisao", decisao.codigo()))
                .retrieve().body(JsonNode.class);
        if (!possuiDados(resposta)) {
            throw new IllegalStateException("Resposta inválida do commerce.");
        }
    }

    private static boolean possuiDados(JsonNode resposta) {
        return resposta != null && !resposta.path("dados").isNull() && !resposta.path("dados").isMissingNode();
    }

    private static ServicoIndisponivelException commerceIndisponivel() {
        return new ServicoIndisponivelException("COMMERCE_INDISPONIVEL", "Não foi possível consultar o pedido.");
    }
}
