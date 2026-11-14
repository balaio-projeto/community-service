package br.com.balaio.community.shared.exception;

public class RecursoNaoEncontradoException extends CommunityException {
    public RecursoNaoEncontradoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
