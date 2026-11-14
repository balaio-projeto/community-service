package br.com.balaio.community.shared.security;

import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import br.com.balaio.community.shared.exception.NaoAutenticadoException;

@Component
public class UsuarioAutenticadoArgumentResolver implements HandlerMethodArgumentResolver {
    private static final String CLAIM_PAPEL = "papel";

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return UsuarioAutenticado.class.equals(parametro.getParameterType());
    }

    @Override
    public UsuarioAutenticado resolveArgument(MethodParameter parametro, ModelAndViewContainer modelo,
            NativeWebRequest requisicao, WebDataBinderFactory binder) {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken autenticacao)) {
            throw new NaoAutenticadoException();
        }
        var jwt = autenticacao.getToken();
        var papel = Papel.deCodigo(jwt.getClaimAsString(CLAIM_PAPEL)).orElseThrow(NaoAutenticadoException::new);
        return new UsuarioAutenticado(UUID.fromString(jwt.getSubject()), papel, jwt.getTokenValue());
    }
}
