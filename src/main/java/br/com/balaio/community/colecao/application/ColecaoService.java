package br.com.balaio.community.colecao.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.balaio.community.colecao.application.port.out.ColecoesPort;
import br.com.balaio.community.colecao.domain.ColecaoDoUsuario;
import br.com.balaio.community.colecao.domain.TipoColecao;

@Service
public class ColecaoService {
    private final ColecoesPort colecoes;

    public ColecaoService(ColecoesPort colecoes) {
        this.colecoes = colecoes;
    }

    @Transactional(readOnly = true)
    public List<String> consultar(UUID usuarioId, TipoColecao tipo) {
        return colecoes.carregar(usuarioId, tipo).itens();
    }

    @Transactional
    public List<String> substituir(UUID usuarioId, TipoColecao tipo, List<String> itens) {
        var colecao = ColecaoDoUsuario.comItens(usuarioId, tipo, itens);
        colecoes.substituir(colecao);
        return colecao.itens();
    }
}
