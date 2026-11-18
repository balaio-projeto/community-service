package br.com.balaio.community.shared.servicos;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

import br.com.balaio.community.shared.exception.ServicoIndisponivelException;
import br.com.balaio.community.shared.integration.HttpClientes;
import br.com.balaio.community.shared.integration.RespostaIncompativelException;

@Component
class CatalogoHttpAdapter implements CatalogoPort {
    private final RestClient http;

    CatalogoHttpAdapter(@Value("${balaio.catalog-url}") String urlCatalogo) {
        this.http = HttpClientes.criar(urlCatalogo);
    }

    @Override
    public Optional<PecaCatalogo> buscar(String slug, Optional<String> tokenVisitante) {
        try {
            var requisicao = http.get().uri("/api/v1/pecas/{slug}", slug);
            tokenVisitante.ifPresent(token -> requisicao.header(HttpHeaders.AUTHORIZATION, HttpClientes.bearer(token)));
            JsonNode peca = HttpClientes.dados(requisicao.retrieve().body(JsonNode.class));
            return Optional.of(new PecaCatalogo(peca.path("slug").asString(), peca.path("artesao").asString()));
        } catch (HttpClientErrorException.NotFound naoEncontrada) {
            return Optional.empty();
        } catch (RestClientException | RespostaIncompativelException indisponivel) {
            throw new ServicoIndisponivelException("CATALOGO_INDISPONIVEL", "Não foi possível consultar a peça.");
        }
    }
}
