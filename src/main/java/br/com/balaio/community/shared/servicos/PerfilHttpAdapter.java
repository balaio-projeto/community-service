package br.com.balaio.community.shared.servicos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

import br.com.balaio.community.shared.exception.NaoAutenticadoException;
import br.com.balaio.community.shared.exception.ServicoIndisponivelException;
import br.com.balaio.community.shared.integration.HttpClientes;
import br.com.balaio.community.shared.integration.RespostaIncompativelException;

@Component
class PerfilHttpAdapter implements PerfilPort {
    private final RestClient http;

    PerfilHttpAdapter(@Value("${balaio.profile-url}") String urlPerfil) {
        this.http = HttpClientes.criar(urlPerfil);
    }

    @Override
    public String nomeDoUsuario(String token) {
        try {
            JsonNode usuario = HttpClientes.dados(http.get().uri("/api/v1/usuario")
                    .header(HttpHeaders.AUTHORIZATION, HttpClientes.bearer(token))
                    .retrieve().body(JsonNode.class));
            return usuario.path("nome").asString();
        } catch (HttpClientErrorException.Unauthorized sessaoInvalida) {
            throw new NaoAutenticadoException();
        } catch (RestClientException | RespostaIncompativelException indisponivel) {
            throw new ServicoIndisponivelException("PERFIL_INDISPONIVEL", "Não foi possível consultar o perfil.");
        }
    }
}
