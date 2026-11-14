package br.com.balaio.community.shared.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import br.com.balaio.community.shared.api.ApiResponse;

@Configuration
public class SecurityConfig {
    static final String[] ROTAS_SEMPRE_AUTENTICADAS = {
        "/api/v1/admin/mediacoes", "/api/v1/admin/mediacoes/**", "/api/v1/estado/*",
        "/api/v1/pedidos/*/avaliacao"
    };
    static final String[] ROTAS_DE_ESCRITA_AUTENTICADAS = {
        "/api/v1/pecas/*/perguntas", "/api/v1/pecas/*/perguntas/*"
    };

    private final ObjectMapper json;

    public SecurityConfig(ObjectMapper json) {
        this.json = json;
    }

    @Bean
    JwtDecoder communityJwtDecoder(@Value("${balaio.jwt.public-key-location}") String localChave,
            @Value("${balaio.jwt.issuer}") String emissor,
            @Value("${balaio.jwt.audience}") String audiencia,
            ResourceLoader recursos) {
        if (localChave.isBlank() || emissor.isBlank() || audiencia.isBlank()) {
            throw new IllegalStateException("Configuração de JWT da community incompleta.");
        }
        var chave = ChavePublicaJwtLoader.carregar(recursos.getResource(localChave));
        var decoder = NimbusJwtDecoder.withPublicKey(chave).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var validadorPadrao = JwtValidators.createDefaultWithIssuer(emissor);
        decoder.setJwtValidator(jwt -> {
            var resultado = validadorPadrao.validate(jwt);
            if (resultado.hasErrors()) {
                return resultado;
            }
            return possuiClaimsObrigatorias(jwt, audiencia)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        });
        return decoder;
    }

    @Bean
    SecurityFilterChain communitySecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(ROTAS_SEMPRE_AUTENTICADAS).authenticated()
                        .requestMatchers(HttpMethod.GET, ROTAS_DE_ESCRITA_AUTENTICADAS).permitAll()
                        .requestMatchers(ROTAS_DE_ESCRITA_AUTENTICADAS).authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { })
                        .authenticationEntryPoint((requisicao, resposta, erro) -> escreverErro(resposta,
                                HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Autenticação necessária.")))
                .exceptionHandling(erros -> erros.accessDeniedHandler((requisicao, resposta, erro) -> escreverErro(
                        resposta, HttpStatus.FORBIDDEN, "SEM_PERMISSAO", "Operação não permitida.")))
                .build();
    }

    private static boolean possuiClaimsObrigatorias(Jwt jwt, String audiencia) {
        return jwt.getAudience().contains(audiencia) && jwt.getId() != null && jwt.getIssuedAt() != null
                && jwt.getExpiresAt() != null && ehUuidCanonico(jwt.getSubject());
    }

    private static boolean ehUuidCanonico(String valor) {
        if (valor == null) {
            return false;
        }
        try {
            return UUID.fromString(valor).toString().equals(valor);
        } catch (IllegalArgumentException erro) {
            return false;
        }
    }

    private void escreverErro(HttpServletResponse resposta, HttpStatus status, String codigo, String mensagem)
            throws IOException {
        resposta.setStatus(status.value());
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        json.writeValue(resposta.getWriter(), ApiResponse.falha(codigo, mensagem));
    }
}
