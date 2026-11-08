package br.com.balaio.community.mediation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MediacoesController {
    private final MediacaoService mediacoes;

    public MediacoesController(MediacaoService mediacoes) {
        this.mediacoes = mediacoes;
    }

    public record Criar(UUID pedido, String assunto, String relato) { }
    public record Estado(String estado) { }
    public record Decisao(String decisao) { }
    public record Envelope(Object dados, Object erro) { }

    @PostMapping("/api/v1/admin/mediacoes")
    public ResponseEntity<Envelope> criar(@RequestBody Criar comando, @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Authorization") String bearer) {
        UUID comprador = exigirPapel(jwt, "comprador");
        return ResponseEntity.status(201).body(new Envelope(mediacoes.criar(comando.pedido(), comprador,
                comando.assunto(), comando.relato(), bearer), null));
    }

    @GetMapping("/api/v1/admin/mediacoes")
    public Envelope listar(@AuthenticationPrincipal Jwt jwt) {
        if (jwt != null && "comprador".equals(jwt.getClaimAsString("papel")))
            return new Envelope(mediacoes.listarPorComprador(UUID.fromString(jwt.getSubject())), null);
        exigirPapel(jwt, "admin");
        return new Envelope(mediacoes.listar(), null);
    }

    @PatchMapping("/api/v1/admin/mediacoes/{id}")
    public Envelope abrir(@PathVariable UUID id, @RequestBody Estado comando, @AuthenticationPrincipal Jwt jwt) {
        exigirPapel(jwt, "admin");
        if (comando == null || !"emAnalise".equals(comando.estado()))
            throw new MediacaoException(400, "REQUISICAO_INVALIDA", "Estado inválido.");
        return new Envelope(mediacoes.abrir(id), null);
    }

    @PostMapping("/api/v1/admin/mediacoes/{id}/decisao")
    public ResponseEntity<Envelope> decidir(@PathVariable UUID id, @RequestBody Decisao comando,
            @AuthenticationPrincipal Jwt jwt) {
        exigirPapel(jwt, "admin");
        var atual = mediacoes.decidir(id, comando.decisao());
        mediacoes.entregar(id);
        atual = mediacoes.consultar(id);
        return "decisao_pendente".equals(atual.get("estado"))
                ? ResponseEntity.accepted().body(new Envelope(atual, null))
                : ResponseEntity.ok(new Envelope(atual, null));
    }

    private UUID exigirPapel(Jwt jwt, String papel) {
        if (jwt == null || !papel.equals(jwt.getClaimAsString("papel")))
            throw new MediacaoException(403, "SEM_PERMISSAO", "Operação não permitida.");
        return UUID.fromString(jwt.getSubject());
    }
}
