package br.com.balaio.community.shared.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.balaio.community.shared.security.UsuarioAutenticadoArgumentResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final UsuarioAutenticadoArgumentResolver usuarioAutenticado;

    public WebConfig(UsuarioAutenticadoArgumentResolver usuarioAutenticado) {
        this.usuarioAutenticado = usuarioAutenticado;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(usuarioAutenticado);
    }
}
