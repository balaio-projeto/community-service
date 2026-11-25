package br.com.balaio.community.shared.servicos;

import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import br.com.balaio.community.shared.integration.HttpClientes;

@Component
class NotificacoesHttpAdapter implements NotificacoesPort {
    private static final Logger log = LoggerFactory.getLogger(NotificacoesHttpAdapter.class);
    private static final int TAMANHO_MINIMO_TOKEN = 32;

    private final RestClient http;
    private final String tokenInterno;

    NotificacoesHttpAdapter(@Value("${balaio.profile-url}") String urlPerfil,
            @Value("${balaio.internal-token:}") String tokenInterno) {
        this.http = HttpClientes.criar(urlPerfil);
        this.tokenInterno = tokenInterno;
    }

    @Override
    public void notificar(Notificacao notificacao) {
        if (tokenInterno.length() < TAMANHO_MINIMO_TOKEN) {
            log.warn("Notificação {} não enviada: token interno ausente.", notificacao.chave());
            return;
        }
        var corpo = new LinkedHashMap<String, Object>();
        corpo.put("conta", notificacao.conta().toString());
        corpo.put("tipo", notificacao.tipo());
        corpo.put("titulo", notificacao.titulo());
        corpo.put("mensagem", notificacao.mensagem());
        corpo.put("link", notificacao.link());
        corpo.put("chave", notificacao.chave());
        try {
            http.post().uri("/internal/v1/notificacoes")
                    .header("X-Balaio-Service-Token", tokenInterno)
                    .contentType(MediaType.APPLICATION_JSON).body(corpo)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException falha) {
            log.warn("Notificação {} não entregue ao perfil: {}", notificacao.chave(),
                    falha.getClass().getSimpleName());
        }
    }
}
