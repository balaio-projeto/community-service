package br.com.balaio.community.shared.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import br.com.balaio.community.shared.api.ApiResponse;

@Configuration
public class SecurityConfig {
    static final String[] INFRAESTRUTURA_PUBLICA = {
        "/actuator/health", "/actuator/health/**", "/actuator/info",
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/error"
    };
    static final String[] LEITURAS_PUBLICAS = {
        "/api/v1/pecas/*/perguntas", "/api/v1/artesaos/*/reputacao",
        "/api/v1/videos", "/api/v1/videos/*", "/api/v1/videos/*/arquivo", "/api/v1/videos/*/capa",
        "/api/v1/videos/*/comentarios"
    };
    static final String[] ESCRITAS_PUBLICAS = {
        "/api/v1/videos/*/visualizacoes"
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
        decoder.setJwtValidator(new ValidadorClaimsJwt(emissor, audiencia, java.time.Clock.systemUTC()));
        return decoder;
    }

    @Bean
    SecurityFilterChain communitySecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(INFRAESTRUTURA_PUBLICA).permitAll()
                        .requestMatchers(HttpMethod.GET, LEITURAS_PUBLICAS).permitAll()
                        .requestMatchers(HttpMethod.HEAD, LEITURAS_PUBLICAS).permitAll()
                        .requestMatchers(HttpMethod.POST, ESCRITAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { })
                        .authenticationEntryPoint((requisicao, resposta, erro) -> escreverErro(resposta,
                                HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Autenticação necessária.")))
                .exceptionHandling(erros -> erros.accessDeniedHandler((requisicao, resposta, erro) -> escreverErro(
                        resposta, HttpStatus.FORBIDDEN, "SEM_PERMISSAO", "Operação não permitida.")))
                .build();
    }

    private void escreverErro(HttpServletResponse resposta, HttpStatus status, String codigo, String mensagem)
            throws IOException {
        resposta.setStatus(status.value());
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        json.writeValue(resposta.getWriter(), ApiResponse.falha(codigo, mensagem));
    }
}
