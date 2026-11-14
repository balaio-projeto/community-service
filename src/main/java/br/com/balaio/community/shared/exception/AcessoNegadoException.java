package br.com.balaio.community.shared.exception;

public class AcessoNegadoException extends CommunityException {
    public AcessoNegadoException() {
        this("Operação não permitida.");
    }

    public AcessoNegadoException(String mensagem) {
        super("SEM_PERMISSAO", mensagem);
    }
}
