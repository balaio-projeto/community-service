package br.com.balaio.community.shared.api;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.CommunityException;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.NaoAutenticadoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;
import br.com.balaio.community.shared.exception.ServicoIndisponivelException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String MENSAGEM_DADOS_INVALIDOS = "Dados inválidos.";

    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ResponseEntity<ApiResponse<Void>> requisicaoInvalida(RequisicaoInvalidaException erro) {
        return responder(HttpStatus.BAD_REQUEST, erro);
    }

    @ExceptionHandler(NaoAutenticadoException.class)
    public ResponseEntity<ApiResponse<Void>> naoAutenticado(NaoAutenticadoException erro) {
        return responder(HttpStatus.UNAUTHORIZED, erro);
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ApiResponse<Void>> acessoNegado(AcessoNegadoException erro) {
        return responder(HttpStatus.FORBIDDEN, erro);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ApiResponse<Void>> naoEncontrado(RecursoNaoEncontradoException erro) {
        return responder(HttpStatus.NOT_FOUND, erro);
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ApiResponse<Void>> conflito(ConflitoException erro) {
        return responder(HttpStatus.CONFLICT, erro);
    }

    @ExceptionHandler(ServicoIndisponivelException.class)
    public ResponseEntity<ApiResponse<Void>> servicoIndisponivel(ServicoIndisponivelException erro) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, erro);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class })
    public ResponseEntity<ApiResponse<Void>> corpoOuParametroInvalido() {
        return responder(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", MENSAGEM_DADOS_INVALIDOS);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> rotaInexistente() {
        return responder(HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO", "Recurso não encontrado.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> alteracaoConcorrente() {
        return responder(HttpStatus.CONFLICT, "ALTERACAO_CONCORRENTE",
                "O recurso foi alterado por outra requisição. Recarregue e tente novamente.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> bancoIndisponivel() {
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Não foi possível acessar os dados.");
    }

    private ResponseEntity<ApiResponse<Void>> responder(HttpStatus status, CommunityException erro) {
        return responder(status, erro.codigo(), erro.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> responder(HttpStatus status, String codigo, String mensagem) {
        return ResponseEntity.status(status).body(ApiResponse.falha(codigo, mensagem));
    }
}
