package br.com.balaio.community.colecao.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.balaio.community.colecao.application.ColecaoService;
import br.com.balaio.community.colecao.domain.TipoColecao;
import br.com.balaio.community.shared.api.ApiResponse;
import br.com.balaio.community.shared.config.OpenApiConfig;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/v1/estado/{colecao}")
@Tag(name = "Coleções do usuário", description = "Favoritos, histórico de busca e vídeos curtidos/salvos")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class ColecoesController {
    private final ColecaoService colecoes;

    public ColecoesController(ColecaoService colecoes) {
        this.colecoes = colecoes;
    }

    @GetMapping
    @Operation(summary = "Lê a coleção do usuário autenticado")
    public ApiResponse<List<String>> consultar(@PathVariable String colecao, UsuarioAutenticado usuario) {
        return ApiResponse.sucesso(colecoes.consultar(usuario.id(), tipo(colecao)));
    }

    @PutMapping
    @Operation(summary = "Substitui a coleção; itens repetidos são removidos mantendo a ordem")
    public ApiResponse<List<String>> substituir(@PathVariable String colecao,
            @RequestBody Map<String, Object> corpo, UsuarioAutenticado usuario) {
        var tipo = tipo(colecao);
        return ApiResponse.sucesso(colecoes.substituir(usuario.id(), tipo, itensDoCorpo(corpo, tipo)));
    }

    @PatchMapping
    @Operation(summary = "Adiciona e/ou remove itens sem reenviar a lista; seguro entre abas e dispositivos")
    public ApiResponse<List<String>> alterar(@PathVariable String colecao,
            @RequestBody AlteracaoColecaoRequest corpo, UsuarioAutenticado usuario) {
        if (corpo == null || (corpo.adicionar() == null && corpo.remover() == null)) {
            throw new RequisicaoInvalidaException("Informe itens para adicionar ou remover.");
        }
        return ApiResponse.sucesso(colecoes.alterar(usuario.id(), tipo(colecao), corpo.adicionar(), corpo.remover()));
    }

    record AlteracaoColecaoRequest(List<String> adicionar, List<String> remover) {
    }

    private static TipoColecao tipo(String codigo) {
        return TipoColecao.deCodigo(codigo).orElseThrow(() -> new RecursoNaoEncontradoException(
                "RECURSO_NAO_ENCONTRADO", "Recurso não encontrado."));
    }

    private static List<String> itensDoCorpo(Map<String, Object> corpo, TipoColecao tipo) {
        if (corpo == null || !(corpo.get(tipo.codigo()) instanceof List<?> lista)
                || lista.stream().anyMatch(item -> !(item instanceof String))) {
            throw new RequisicaoInvalidaException("Estado inválido.");
        }
        return lista.stream().map(String.class::cast).toList();
    }
}
