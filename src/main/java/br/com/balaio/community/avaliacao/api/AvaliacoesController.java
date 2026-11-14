package br.com.balaio.community.avaliacao.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.balaio.community.avaliacao.application.AvaliacaoService;
import br.com.balaio.community.avaliacao.application.AvaliacaoService.NovaAvaliacao;
import br.com.balaio.community.avaliacao.domain.Avaliacao;
import br.com.balaio.community.avaliacao.domain.Reputacao;
import br.com.balaio.community.shared.api.ApiResponse;
import br.com.balaio.community.shared.config.OpenApiConfig;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@RestController
@Tag(name = "Avaliações", description = "Avaliação de compras entregues e reputação dos artesãos")
public class AvaliacoesController {
    private final AvaliacaoService avaliacoes;

    public AvaliacoesController(AvaliacaoService avaliacoes) {
        this.avaliacoes = avaliacoes;
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/avaliacao")
    @Operation(summary = "Comprador avalia um pedido entregue (uma vez por pedido)")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
    public ResponseEntity<ApiResponse<AvaliacaoResponse>> avaliar(@PathVariable UUID pedidoId,
            @RequestBody NovaAvaliacao corpo, UsuarioAutenticado usuario) {
        var avaliacao = avaliacoes.avaliar(pedidoId, corpo, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.sucesso(AvaliacaoResponse.de(avaliacao)));
    }

    @GetMapping("/api/v1/pedidos/{pedidoId}/avaliacao")
    @Operation(summary = "Comprador consulta a avaliação que fez do pedido")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
    public ApiResponse<AvaliacaoResponse> consultar(@PathVariable UUID pedidoId, UsuarioAutenticado usuario) {
        return ApiResponse.sucesso(AvaliacaoResponse.de(avaliacoes.consultarDoPedido(pedidoId, usuario)));
    }

    @GetMapping("/api/v1/artesaos/{artesaoSlug}/reputacao")
    @Operation(summary = "Média e distribuição das notas recebidas pelo artesão")
    public ApiResponse<Reputacao> reputacao(@PathVariable String artesaoSlug) {
        return ApiResponse.sucesso(avaliacoes.reputacaoDoArtesao(artesaoSlug));
    }

    public record AvaliacaoResponse(UUID id, UUID pedidoId, int nota, String comentario, List<String> aspectos,
            OffsetDateTime criadaEm) {
        static AvaliacaoResponse de(Avaliacao avaliacao) {
            return new AvaliacaoResponse(avaliacao.id(), avaliacao.pedidoId(), avaliacao.nota(),
                    avaliacao.comentario(), avaliacao.aspectos(), avaliacao.criadaEm());
        }
    }
}
