package br.com.balaio.community.shared.servicos;

import java.util.UUID;

public interface NotificacoesPort {
    void notificar(Notificacao notificacao);

    record Notificacao(UUID conta, String tipo, String titulo, String mensagem, String link, String chave) {
    }
}
