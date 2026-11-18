package br.com.balaio.community.video.api;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import br.com.balaio.community.shared.api.ApiResponse;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.UsuarioAutenticado;
import br.com.balaio.community.video.application.ComentarioVideoService;
import br.com.balaio.community.video.application.VideoService;
import br.com.balaio.community.video.application.VideoService.DadosVideo;
import br.com.balaio.community.video.application.VideoService.EnvioMidia;
import br.com.balaio.community.video.domain.TipoMidia;

@RestController
@RequestMapping("/api/v1/videos")
@Tag(name = "Vídeos", description = "Vídeos dos ateliês, upload de arquivo/capa e comentários")
public class VideosController {
    private final VideoService videos;
    private final ComentarioVideoService comentarios;

    public VideosController(VideoService videos, ComentarioVideoService comentarios) {
        this.videos = videos;
        this.comentarios = comentarios;
    }

    @GetMapping
    @Operation(summary = "Vídeos publicados; com painel=true, todos os vídeos do artesão autenticado (paginado)")
    public ApiResponse<List<VideoResponse>> listar(@RequestParam(defaultValue = "false") boolean painel,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + Pagina.TAMANHO_PADRAO) int tamanho,
            Optional<UsuarioAutenticado> usuario) {
        return ApiResponse.sucesso(videos.listar(painel, usuario, Pagina.de(pagina, tamanho)).stream()
                .map(VideoResponse::de).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe; rascunho só aparece para o dono")
    public ApiResponse<VideoResponse> consultar(@PathVariable UUID id, Optional<UsuarioAutenticado> usuario) {
        return ApiResponse.sucesso(VideoResponse.de(videos.consultar(id, usuario)));
    }

    @PostMapping
    @Operation(summary = "Artesão cria o vídeo como rascunho; publicar exige arquivo enviado")
    public ResponseEntity<ApiResponse<VideoResponse>> criar(@RequestBody DadosVideo corpo,
            UsuarioAutenticado usuario) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.sucesso(VideoResponse.de(videos.criar(corpo, usuario))));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Dono altera legenda, etiquetas, peça ou situação")
    public ApiResponse<VideoResponse> alterar(@PathVariable UUID id, @RequestBody DadosVideo corpo,
            UsuarioAutenticado usuario) {
        return ApiResponse.sucesso(VideoResponse.de(videos.alterar(id, corpo, usuario)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Dono remove o vídeo, seus arquivos e comentários")
    public ApiResponse<VideoResponse> remover(@PathVariable UUID id, UsuarioAutenticado usuario) {
        return ApiResponse.sucesso(VideoResponse.de(videos.remover(id, usuario)));
    }

    @PutMapping("/{id}/arquivo")
    @Operation(summary = "Envia o arquivo do vídeo no corpo (video/mp4 ou video/webm, até 100 MB)")
    public ApiResponse<VideoResponse> enviarArquivo(@PathVariable UUID id,
            @RequestParam(required = false) String duracao, HttpServletRequest requisicao,
            UsuarioAutenticado usuario) throws IOException {
        return ApiResponse.sucesso(VideoResponse.de(videos.enviarMidia(id, envio(TipoMidia.VIDEO, requisicao,
                duracao), usuario)));
    }

    @PutMapping("/{id}/capa")
    @Operation(summary = "Envia a capa no corpo (image/jpeg, image/png ou image/webp, até 5 MB)")
    public ApiResponse<VideoResponse> enviarCapa(@PathVariable UUID id, HttpServletRequest requisicao,
            UsuarioAutenticado usuario) throws IOException {
        return ApiResponse.sucesso(VideoResponse.de(videos.enviarMidia(id, envio(TipoMidia.CAPA, requisicao,
                null), usuario)));
    }

    @GetMapping("/{id}/arquivo")
    @Operation(summary = "Reproduz o vídeo (aceita Range)")
    public ResponseEntity<StreamingResponseBody> arquivo(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            Optional<UsuarioAutenticado> usuario) {
        return EntregaDeMidia.responder(videos.abrirMidia(id, TipoMidia.VIDEO, usuario), range);
    }

    @GetMapping("/{id}/capa")
    @Operation(summary = "Imagem de capa do vídeo")
    public ResponseEntity<StreamingResponseBody> capa(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            Optional<UsuarioAutenticado> usuario) {
        return EntregaDeMidia.responder(videos.abrirMidia(id, TipoMidia.CAPA, usuario), range);
    }

    @PostMapping("/{id}/visualizacoes")
    @Operation(summary = "Registra uma reprodução iniciada; o player deve chamar uma vez por sessão de exibição")
    public ResponseEntity<Void> registrarVisualizacao(@PathVariable UUID id, Optional<UsuarioAutenticado> usuario) {
        videos.registrarVisualizacao(id, usuario);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/comentarios")
    @Operation(summary = "Comentários do vídeo visível")
    public ApiResponse<List<ComentarioResponse>> comentarios(@PathVariable UUID id,
            Optional<UsuarioAutenticado> usuario) {
        return ApiResponse.sucesso(comentarios.listar(id, usuario).stream().map(ComentarioResponse::de).toList());
    }

    @PostMapping("/{id}/comentarios")
    @Operation(summary = "Usuário autenticado comenta o vídeo")
    public ResponseEntity<ApiResponse<ComentarioResponse>> comentar(@PathVariable UUID id,
            @RequestBody NovoComentarioRequest corpo, UsuarioAutenticado usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.sucesso(
                ComentarioResponse.de(comentarios.comentar(id, corpo.texto(), usuario))));
    }

    private static EnvioMidia envio(TipoMidia tipo, HttpServletRequest requisicao, String duracao)
            throws IOException {
        return new EnvioMidia(tipo, requisicao.getContentType(), requisicao.getContentLengthLong(),
                requisicao.getInputStream(), duracao);
    }

    record NovoComentarioRequest(String texto) {
    }
}
