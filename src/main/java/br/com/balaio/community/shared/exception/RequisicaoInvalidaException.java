package br.com.balaio.community.shared.exception;

public class RequisicaoInvalidaException extends CommunityException {
    public RequisicaoInvalidaException(String mensagem) {
        super("REQUISICAO_INVALIDA", mensagem);
    }
}
