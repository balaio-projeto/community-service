package br.com.balaio.community.colecao.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public record ColecaoDoUsuario(UUID usuarioId, TipoColecao tipo, List<String> itens) {

    public ColecaoDoUsuario {
        itens = List.copyOf(itens);
    }

    public static ColecaoDoUsuario vazia(UUID usuarioId, TipoColecao tipo) {
        return new ColecaoDoUsuario(usuarioId, tipo, List.of());
    }

    public static ColecaoDoUsuario comItens(UUID usuarioId, TipoColecao tipo, List<String> recebidos) {
        if (recebidos == null || recebidos.stream().anyMatch(Objects::isNull)) {
            throw new RequisicaoInvalidaException("Estado inválido.");
        }
        var semRepeticao = new LinkedHashSet<String>();
        recebidos.stream().map(String::strip).filter(item -> !item.isEmpty()).forEach(semRepeticao::add);
        var itens = List.copyOf(semRepeticao);
        if (itens.stream().anyMatch(item -> !tipo.aceitaFormato(item))) {
            throw new RequisicaoInvalidaException("Item inválido em " + tipo.codigo() + ".");
        }
        return new ColecaoDoUsuario(usuarioId, tipo, limitar(tipo, itens));
    }

    public ColecaoDoUsuario alterar(List<String> adicionar, List<String> remover) {
        var aRemover = normalizados(remover);
        var aAdicionar = normalizados(adicionar);
        var resultado = new LinkedHashSet<String>();
        if (tipo.descartaExcesso()) {
            resultado.addAll(aAdicionar);
        }
        itens.stream().filter(item -> !aRemover.contains(item)).forEach(resultado::add);
        if (!tipo.descartaExcesso()) {
            resultado.addAll(aAdicionar);
        }
        return comItens(usuarioId, tipo, List.copyOf(resultado));
    }

    private static List<String> normalizados(List<String> recebidos) {
        if (recebidos == null) {
            return List.of();
        }
        if (recebidos.stream().anyMatch(Objects::isNull)) {
            throw new RequisicaoInvalidaException("Estado inválido.");
        }
        return recebidos.stream().map(String::strip).filter(item -> !item.isEmpty()).distinct().toList();
    }

    private static List<String> limitar(TipoColecao tipo, List<String> itens) {
        if (itens.size() <= tipo.quantidadeMaxima()) {
            return itens;
        }
        if (!tipo.descartaExcesso()) {
            throw new RequisicaoInvalidaException(
                    "Limite de " + tipo.quantidadeMaxima() + " itens em " + tipo.codigo() + ".");
        }
        return itens.subList(0, tipo.quantidadeMaxima());
    }
}
