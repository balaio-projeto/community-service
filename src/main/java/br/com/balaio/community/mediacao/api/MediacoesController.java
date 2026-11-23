package br.com.balaio.community.mediacao.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.balaio.community.mediacao.application.AberturaMediacao;
import br.com.balaio.community.mediacao.application.EntregaDecisaoService;
import br.com.balaio.community.mediacao.application.MediacaoService;
import br.com.balaio.community.mediacao.domain.DecisaoMediacao;
import br.com.balaio.community.mediacao.domain.EstadoMediacao;
import br.com.balaio.community.mediacao.domain.Mediacao;
import br.com.balaio.community.shared.api.ApiResponse;
import br.com.balaio.community.shared.config.OpenApiConfig;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/v1/admin/mediacoes")
@Tag(name = "Mediações", description = "Disputas de pedidos abertas por compradores e decididas por administradores")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class MediacoesController {
    private final MediacaoService mediacoes;
    private final EntregaDecisaoService entregas;

    public MediacoesController(MediacaoService mediacoes, EntregaDecisaoService entregas) {
        this.mediacoes = mediacoes;
        this.entregas = entregas;
    }

    @PostMapping
    @Operation(summary = "Comprador abre uma mediação para um pedido próprio")
    public ResponseEntity<ApiResponse<MediacaoResponse>> abrir(@RequestBody MediacaoRequests.Abertura corpo,
            UsuarioAutenticado usuario) {
        usuario.exigirPapel(Papel.COMPRADOR);
        var mediacao = mediacoes.abrir(new AberturaMediacao(corpo.pedido(), usuario.id(), corpo.assunto(),
                corpo.relato(), usuario.token()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.sucesso(MediacaoResponse.paraComprador(mediacao)));
    }

    @GetMapping
    @Operation(summary = "Comprador vê as próprias mediações; artesão vê, sem poder decidir, as dos pedidos dele; "
            + "administrador vê todas (paginado)")
    public ApiResponse<List<MediacaoResponse>> listar(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + Pagina.TAMANHO_PADRAO) int tamanho, UsuarioAutenticado usuario) {
        var trecho = Pagina.de(pagina, tamanho);
        if (usuario.possuiPapel(Papel.COMPRADOR)) {
            return ApiResponse.sucesso(mediacoes.listarDoComprador(usuario.id(), trecho).stream()
                    .map(MediacaoResponse::paraComprador).toList());
        }
        if (usuario.possuiPapel(Papel.ARTESAO)) {
            return ApiResponse.sucesso(mediacoes.listarDoArtesao(usuario, trecho).stream()
                    .map(MediacaoResponse::paraArtesao).toList());
        }
        usuario.exigirPapel(Papel.ADMIN);
        return ApiResponse.sucesso(mediacoes.listarTodas(trecho).stream()
                .map(MediacaoResponse::paraAdministrador).toList());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Administrador coloca a mediação em análise")
    public ApiResponse<MediacaoResponse> iniciarAnalise(@PathVariable UUID id,
            @RequestBody MediacaoRequests.AlteracaoEstado corpo, UsuarioAutenticado usuario) {
        usuario.exigirPapel(Papel.ADMIN);
        if (corpo == null || !EstadoMediacao.EM_ANALISE.codigo().equals(corpo.estado())) {
            throw new RequisicaoInvalidaException("Estado inválido.");
        }
        return ApiResponse.sucesso(MediacaoResponse.paraAdministrador(mediacoes.iniciarAnalise(id)));
    }

    @PostMapping("/{id}/decisao")
    @Operation(summary = "Administrador decide a mediação; 202 enquanto o commerce não confirma")
    public ResponseEntity<ApiResponse<MediacaoResponse>> decidir(@PathVariable UUID id,
            @RequestBody MediacaoRequests.Decisao corpo, UsuarioAutenticado usuario) {
        usuario.exigirPapel(Papel.ADMIN);
        mediacoes.registrarDecisao(id, DecisaoMediacao.deCodigo(corpo.decisao()));
        return respostaDeEntrega(entregas.entregar(id));
    }

    @PostMapping("/{id}/reprocessar-entrega")
    @Operation(summary = "Administrador reenvia ao commerce uma decisão cuja entrega falhou (mesmo decisaoId)")
    public ResponseEntity<ApiResponse<MediacaoResponse>> reprocessarEntrega(@PathVariable UUID id,
            UsuarioAutenticado usuario) {
        usuario.exigirPapel(Papel.ADMIN);
        return respostaDeEntrega(entregas.reprocessar(id));
    }

    private static ResponseEntity<ApiResponse<MediacaoResponse>> respostaDeEntrega(Mediacao atual) {
        var status = atual.aguardaEntregaDaDecisao() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.sucesso(MediacaoResponse.paraAdministrador(atual)));
    }
}
