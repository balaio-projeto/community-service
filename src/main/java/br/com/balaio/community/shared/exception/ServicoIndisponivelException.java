package br.com.balaio.community.shared.exception;

public class ServicoIndisponivelException extends CommunityException {
    public ServicoIndisponivelException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
