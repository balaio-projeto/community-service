package br.com.balaio.community.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {
    public static final String ESQUEMA_BEARER = "bearer-jwt";

    @Bean
    OpenAPI communityOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Balaio community-service")
                        .description("Perguntas, conversas, avaliações, mediações e vídeos da comunidade.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
