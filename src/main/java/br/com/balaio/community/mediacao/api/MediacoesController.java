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
        return ResponseEntity.status(HttpStatus.CREATED).body(responder(mediacao));
    }

    @GetMapping
    @Operation(summary = "Comprador vê as próprias mediações; administrador vê todas")
    public ApiResponse<List<MediacaoResponse>> listar(UsuarioAutenticado usuario) {
        return ApiResponse.sucesso(mediacoesVisiveisPara(usuario).stream().map(MediacaoResponse::de).toList());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Administrador coloca a mediação em análise")
    public ApiResponse<MediacaoResponse> iniciarAnalise(@PathVariable UUID id,
            @RequestBody MediacaoRequests.AlteracaoEstado corpo, UsuarioAutenticado usuario) {
        usuario.exigirPapel(Papel.ADMIN);
        if (corpo == null || !EstadoMediacao.EM_ANALISE.codigo().equals(corpo.estado())) {
            throw new RequisicaoInvalidaException("Estado inválido.");
        }
        return responder(mediacoes.iniciarAnalise(id));
    }

    @PostMapping("/{id}/decisao")
    @Operation(summary = "Administrador decide a mediação; 202 enquanto o commerce não confirma")
    public ResponseEntity<ApiResponse<MediacaoResponse>> decidir(@PathVariable UUID id,
            @RequestBody MediacaoRequests.Decisao corpo, UsuarioAutenticado usuario) {
        usuario.exigirPapel(Papel.ADMIN);
        mediacoes.registrarDecisao(id, DecisaoMediacao.deCodigo(corpo.decisao()));
        var atual = entregas.entregar(id);
        var status = atual.aguardaEntregaDaDecisao() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(responder(atual));
    }

    private List<Mediacao> mediacoesVisiveisPara(UsuarioAutenticado usuario) {
        if (usuario.possuiPapel(Papel.COMPRADOR)) {
            return mediacoes.listarDoComprador(usuario.id());
        }
        usuario.exigirPapel(Papel.ADMIN);
        return mediacoes.listarTodas();
    }

    private static ApiResponse<MediacaoResponse> responder(Mediacao mediacao) {
        return ApiResponse.sucesso(MediacaoResponse.de(mediacao));
    }
}
