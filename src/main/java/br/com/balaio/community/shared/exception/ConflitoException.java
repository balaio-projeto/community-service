package br.com.balaio.community.shared.exception;

public class ConflitoException extends CommunityException {
    public ConflitoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
