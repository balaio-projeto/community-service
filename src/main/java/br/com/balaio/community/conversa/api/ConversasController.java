package br.com.balaio.community.conversa.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.balaio.community.conversa.application.ConversaService;
import br.com.balaio.community.conversa.domain.Mensagem;
import br.com.balaio.community.conversa.domain.ResumoConversa;
import br.com.balaio.community.shared.api.ApiResponse;
import br.com.balaio.community.shared.config.OpenApiConfig;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@RestController
@Tag(name = "Conversas", description = "Mensagens entre comprador e artesão de cada pedido")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class ConversasController {
    private static final ZoneId FUSO_EXIBICAO = ZoneId.of("America/Recife");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ConversaService conversas;

    public ConversasController(ConversaService conversas) {
        this.conversas = conversas;
    }

    @GetMapping("/api/v1/pedidos/{pedidoId}/conversa")
    @Operation(summary = "Lê a conversa do pedido; só comprador e artesão do pedido. "
            + "Ainda marca como lida por compatibilidade — prefira POST /leitura")
    public ApiResponse<List<MensagemResponse>> ler(@PathVariable UUID pedidoId, UsuarioAutenticado usuario) {
        return ApiResponse.sucesso(conversas.lerConversa(pedidoId, usuario).stream()
                .map(MensagemResponse::de).toList());
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/conversa")
    @Operation(summary = "Envia mensagem na conversa do pedido")
    public ResponseEntity<ApiResponse<MensagemResponse>> enviar(@PathVariable UUID pedidoId,
            @RequestBody NovaMensagemRequest corpo, @RequestHeader("Idempotency-Key") String chave,
            UsuarioAutenticado usuario) {
        var mensagem = conversas.enviar(pedidoId, corpo.texto(), chave, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.sucesso(MensagemResponse.de(mensagem)));
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/conversa/leitura")
    @Operation(summary = "Confirma que o usuário leu a conversa do pedido até agora")
    public ResponseEntity<Void> confirmarLeitura(@PathVariable UUID pedidoId, UsuarioAutenticado usuario) {
        conversas.confirmarLeitura(pedidoId, usuario);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/artesao/conversas")
    @Operation(summary = "Conversas dos pedidos do artesão autenticado, com prévia e não lidas")
    public ApiResponse<List<ConversaArtesaoResponse>> doArtesao(UsuarioAutenticado usuario) {
        return ApiResponse.sucesso(conversas.conversasDoArtesao(usuario).stream()
                .map(ConversaArtesaoResponse::de).toList());
    }

    private static String horaLocal(OffsetDateTime instante) {
        return instante.atZoneSameInstant(FUSO_EXIBICAO).format(FORMATO_HORA);
    }

    record NovaMensagemRequest(String texto) {
    }

    public record MensagemResponse(UUID id, UUID pedidoId, String autor, String texto, String hora,
            OffsetDateTime enviadaEm) {
        static MensagemResponse de(Mensagem mensagem) {
            return new MensagemResponse(mensagem.id(), mensagem.pedidoId(), mensagem.autor().codigo(),
                    mensagem.texto(), horaLocal(mensagem.enviadaEm()), mensagem.enviadaEm());
        }
    }

    public record ConversaArtesaoResponse(UUID id, String pessoa, String assunto, String previa, String quando,
            boolean naoLida, String retrato) {
        static ConversaArtesaoResponse de(ResumoConversa resumo) {
            var pedido = resumo.pedido();
            return new ConversaArtesaoResponse(pedido.id(), pedido.compradorNome(), "Pedido " + pedido.id(),
                    resumo.ultimaMensagem().map(Mensagem::texto).orElse(ResumoConversa.PREVIA_SEM_MENSAGENS),
                    resumo.ultimaMensagem().map(mensagem -> horaLocal(mensagem.enviadaEm())).orElse(pedido.data()),
                    resumo.naoLida(), null);
        }
    }
}
