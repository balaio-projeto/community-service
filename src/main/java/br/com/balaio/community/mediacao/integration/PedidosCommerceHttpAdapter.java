package br.com.balaio.community.mediacao.integration;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
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
import br.com.balaio.community.shared.integration.RespostaIncompativelException;

@Component
class PedidosCommerceHttpAdapter implements PedidosCommercePort {
    private static final Logger LOG = LoggerFactory.getLogger(PedidosCommerceHttpAdapter.class);
    private static final int TAMANHO_MINIMO_TOKEN_INTERNO = 32;
    private static final String CABECALHO_TOKEN_INTERNO = "X-Balaio-Service-Token";
    private static final Set<Integer> STATUS_TRANSITORIOS = Set.of(408, 425, 429);

    private final RestClient http;
    private final ConsultasCommerceClient consultasCommerce;
    private final String tokenInterno;

    PedidosCommerceHttpAdapter(@Value("${balaio.commerce-url}") String urlCommerce,
            @Value("${balaio.internal-token:}") String tokenInterno,
            ConsultasCommerceClient consultasCommerce) {
        this.http = HttpClientes.criar(urlCommerce);
        this.tokenInterno = validarTokenInterno(tokenInterno);
        this.consultasCommerce = consultasCommerce;
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
    public UUID confirmarCompradorDoPedido(UUID pedidoId, String tokenComprador) {
        try {
            String artesao = consultasCommerce.consultarPedido(pedidoId, tokenComprador).path("artesaoId").asString();
            return artesao.isBlank() ? null : UUID.fromString(artesao);
        } catch (HttpClientErrorException.NotFound naoEncontrado) {
            throw new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado.");
        } catch (HttpClientErrorException.Forbidden semPermissao) {
            throw new AcessoNegadoException("Este pedido não pertence a você.");
        } catch (RestClientException | RespostaIncompativelException | IllegalArgumentException indisponivel) {
            throw commerceIndisponivel();
        }
    }

    @Override
    public List<UUID> pedidosVendidosPeloArtesao(String tokenArtesao) {
        try {
            JsonNode vendas = HttpClientes.dados(http.get().uri("/api/v1/artesao/vendas")
                    .header(HttpHeaders.AUTHORIZATION, HttpClientes.bearer(tokenArtesao))
                    .retrieve().body(JsonNode.class));
            return StreamSupport.stream(vendas.spliterator(), false)
                    .map(venda -> UUID.fromString(venda.path("id").asString())).toList();
        } catch (RestClientException | RespostaIncompativelException | IllegalArgumentException indisponivel) {
            throw commerceIndisponivel();
        }
    }

    private static ServicoIndisponivelException commerceIndisponivel() {
        return new ServicoIndisponivelException("COMMERCE_INDISPONIVEL", "Não foi possível consultar o pedido.");
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
        } catch (HttpStatusCodeException respostaDeErro) {
            throw classificarRespostaDeErro(respostaDeErro);
        } catch (RestClientException falha) {
            if (falha instanceof ResourceAccessException || ehFalhaDeTransporte(falha)) {
                throw FalhaNaEntregaException.temporaria(
                        "commerce inacessível: " + falha.getMostSpecificCause().getClass().getSimpleName(), falha);
            }
            throw violacaoDeContrato(falha);
        } catch (RespostaIncompativelException respostaSemDados) {
            throw violacaoDeContrato(respostaSemDados);
        }
    }

    private static boolean ehFalhaDeTransporte(Throwable falha) {
        for (Throwable causa = falha; causa != null; causa = causa.getCause()) {
            if (causa instanceof HttpMessageConversionException) {
                return false;
            }
            if (causa instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private static FalhaNaEntregaException violacaoDeContrato(RuntimeException falha) {
        return FalhaNaEntregaException.definitiva(
                "commerce aceitou a requisição, mas a resposta não segue o contrato: "
                        + falha.getClass().getSimpleName(), falha);
    }

    private static FalhaNaEntregaException classificarRespostaDeErro(HttpStatusCodeException erro) {
        int status = erro.getStatusCode().value();
        if (erro.getStatusCode().is5xxServerError() || STATUS_TRANSITORIOS.contains(status)) {
            return FalhaNaEntregaException.temporaria("commerce respondeu HTTP " + status, erro);
        }
        return FalhaNaEntregaException.definitiva("commerce recusou a decisão com HTTP " + status, erro);
    }
}
