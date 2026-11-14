package br.com.balaio.community.colecao.application.port.out;

import java.util.UUID;

import br.com.balaio.community.colecao.domain.ColecaoDoUsuario;
import br.com.balaio.community.colecao.domain.TipoColecao;

public interface ColecoesPort {
    ColecaoDoUsuario carregar(UUID usuarioId, TipoColecao tipo);

    void substituir(ColecaoDoUsuario colecao);
}
