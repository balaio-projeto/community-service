package br.com.balaio.community.conversa.domain;

import java.util.UUID;

public record PedidoDaConversa(UUID id, String compradorNome, String data) {
}
