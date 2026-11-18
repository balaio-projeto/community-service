package br.com.balaio.community.colecao.application;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
        colecoes.bloquear(usuarioId, tipo);
        colecoes.substituir(colecao);
        return colecao.itens();
    }

    @Transactional
    public List<String> alterar(UUID usuarioId, TipoColecao tipo, List<String> adicionar, List<String> remover) {
        colecoes.bloquear(usuarioId, tipo);
        var atual = colecoes.carregar(usuarioId, tipo);
        var alterada = atual.alterar(adicionar, remover);
        if (!alterada.itens().equals(atual.itens())) {
            colecoes.substituir(alterada);
        }
        return alterada.itens();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> quantosUsuariosTem(TipoColecao tipo, Collection<String> itens) {
        return colecoes.contarUsuariosPorItem(tipo, itens);
    }

    @Transactional
    public void esquecerItem(String item, TipoColecao... tipos) {
        Arrays.stream(tipos).forEach(tipo -> colecoes.removerItemDeTodos(tipo, item));
    }
}
