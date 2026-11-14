package br.com.balaio.community.shared.exception;

public abstract class CommunityException extends RuntimeException {
    private final String codigo;

    protected CommunityException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
