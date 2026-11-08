package br.com.balaio.community.mediation;

public class MediacaoException extends RuntimeException {
    private final int status;
    private final String codigo;

    public MediacaoException(int status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public int status() { return status; }
    public String codigo() { return codigo; }
}
