package br.com.balaio.community.pergunta.api;

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

import br.com.balaio.community.pergunta.application.PerguntaService;
import br.com.balaio.community.shared.api.ApiResponse;
import br.com.balaio.community.shared.config.OpenApiConfig;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/v1/pecas/{slug}/perguntas")
@Tag(name = "Perguntas", description = "Perguntas públicas sobre peças, respondidas pelo artesão dono")
public class PerguntasController {
    private final PerguntaService perguntas;

    public PerguntasController(PerguntaService perguntas) {
        this.perguntas = perguntas;
    }

    @GetMapping
    @Operation(summary = "Lista as perguntas públicas de uma peça (paginado, mais antigas primeiro)")
    public ApiResponse<List<PerguntaResponse>> listar(@PathVariable String slug,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + Pagina.TAMANHO_PADRAO) int tamanho) {
        return ApiResponse.sucesso(perguntas.listarDaPeca(slug, Pagina.de(pagina, tamanho)).stream()
                .map(PerguntaResponse::de).toList());
    }

    @PostMapping
    @Operation(summary = "Usuário autenticado pergunta sobre a peça")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
    public ResponseEntity<ApiResponse<PerguntaResponse>> perguntar(@PathVariable String slug,
            @RequestBody NovaPerguntaRequest corpo, UsuarioAutenticado usuario) {
        var pergunta = perguntas.perguntar(slug, corpo.pergunta(), usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.sucesso(PerguntaResponse.de(pergunta)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Artesão dono da peça responde a pergunta")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
    public ApiResponse<PerguntaResponse> responder(@PathVariable String slug, @PathVariable UUID id,
            @RequestBody RespostaRequest corpo, UsuarioAutenticado usuario) {
        return ApiResponse.sucesso(PerguntaResponse.de(perguntas.responder(slug, id, corpo.resposta(), usuario)));
    }

    record NovaPerguntaRequest(String pergunta) {
    }

    record RespostaRequest(String resposta) {
    }
}
