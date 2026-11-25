package br.com.balaio.community.shared.servicos;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
    private static final Logger log = LoggerFactory.getLogger(PerfilHttpAdapter.class);
    private static final int LIMITE_POR_CONSULTA = 100;

    private final RestClient http;
    private final String tokenInterno;

    PerfilHttpAdapter(@Value("${balaio.profile-url}") String urlPerfil,
            @Value("${balaio.internal-token:}") String tokenInterno) {
        this.http = HttpClientes.criar(urlPerfil);
        this.tokenInterno = tokenInterno;
    }

    @Override
    public Map<UUID, String> imagensDasContas(Collection<UUID> contas) {
        var unicas = contas.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (unicas.isEmpty() || tokenInterno.length() < 32) return Map.of();
        var imagens = new HashMap<UUID, String>();
        for (int inicio = 0; inicio < unicas.size(); inicio += LIMITE_POR_CONSULTA) {
            List<UUID> lote = unicas.subList(inicio, Math.min(unicas.size(), inicio + LIMITE_POR_CONSULTA));
            try {
                JsonNode dados = HttpClientes.dados(http.post().uri("/internal/v1/contas/imagens")
                        .header("X-Balaio-Service-Token", tokenInterno)
                        .contentType(MediaType.APPLICATION_JSON).body(Map.of("contas", lote))
                        .retrieve().body(JsonNode.class));
                for (UUID conta : lote) {
                    String imagem = dados.path(conta.toString()).asString("");
                    if (!imagem.isBlank()) imagens.put(conta, imagem);
                }
            } catch (RestClientException | RespostaIncompativelException indisponivel) {
                log.warn("Fotos dos autores indisponíveis no perfil: {}", indisponivel.getClass().getSimpleName());
            }
        }
        return imagens;
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
