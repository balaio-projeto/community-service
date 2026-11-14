package br.com.balaio.community.shared.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
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
        return UsuarioAutenticado.class.equals(parametro.getParameterType()) || ehUsuarioOpcional(parametro);
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer modelo,
            NativeWebRequest requisicao, WebDataBinderFactory binder) {
        var usuario = usuarioDoContexto();
        if (ehUsuarioOpcional(parametro)) {
            return usuario;
        }
        return usuario.orElseThrow(NaoAutenticadoException::new);
    }

    private static Optional<UsuarioAutenticado> usuarioDoContexto() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken autenticacao)) {
            return Optional.empty();
        }
        var jwt = autenticacao.getToken();
        var papel = Papel.deCodigo(jwt.getClaimAsString(CLAIM_PAPEL)).orElseThrow(NaoAutenticadoException::new);
        return Optional.of(new UsuarioAutenticado(UUID.fromString(jwt.getSubject()), papel, jwt.getTokenValue()));
    }

    private static boolean ehUsuarioOpcional(MethodParameter parametro) {
        return Optional.class.equals(parametro.getParameterType())
                && UsuarioAutenticado.class.equals(ResolvableType.forMethodParameter(parametro).getGeneric(0).resolve());
    }
}
