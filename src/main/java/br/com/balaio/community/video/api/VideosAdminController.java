package br.com.balaio.community.video.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.balaio.community.shared.api.ApiResponse;
import br.com.balaio.community.shared.config.OpenApiConfig;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.UsuarioAutenticado;
import br.com.balaio.community.video.application.ModeracaoVideoService;
import br.com.balaio.community.video.domain.FiltroModeracaoVideo;

@RestController
@RequestMapping("/api/v1/admin/videos")
@Tag(name = "Moderação de vídeos", description = "Administrador suspende e reativa vídeos que infringem as regras")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class VideosAdminController {
    private final ModeracaoVideoService moderacao;

    public VideosAdminController(ModeracaoVideoService moderacao) {
        this.moderacao = moderacao;
    }

    @GetMapping
    @Operation(summary = "Vídeos para moderação: estado=todos (publicados e suspensos), publicada ou suspensa "
            + "(paginado)")
    public ApiResponse<List<VideoModeracaoResponse>> listar(@RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + Pagina.TAMANHO_PADRAO) int tamanho, UsuarioAutenticado usuario) {
        return ApiResponse.sucesso(moderacao.listar(FiltroModeracaoVideo.deCodigo(estado), Pagina.de(pagina, tamanho),
                usuario).stream().map(VideoModeracaoResponse::de).toList());
    }

    @PostMapping("/{id}/suspensao")
    @Operation(summary = "Suspende o vídeo com motivo (10 a 1000 caracteres) e avisa o artesão dono")
    public ApiResponse<VideoModeracaoResponse> suspender(@PathVariable UUID id,
            @RequestBody(required = false) SuspensaoRequest corpo, UsuarioAutenticado usuario) {
        return ApiResponse.sucesso(VideoModeracaoResponse.de(
                moderacao.suspender(id, corpo == null ? null : corpo.motivo(), usuario)));
    }

    @PostMapping("/{id}/reativacao")
    @Operation(summary = "Reativa um vídeo suspenso e avisa o artesão dono")
    public ApiResponse<VideoModeracaoResponse> reativar(@PathVariable UUID id, UsuarioAutenticado usuario) {
        return ApiResponse.sucesso(VideoModeracaoResponse.de(moderacao.reativar(id, usuario)));
    }

    record SuspensaoRequest(String motivo) {
    }
}
