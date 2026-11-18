package br.com.balaio.community.shared.integration;

public class RespostaIncompativelException extends RuntimeException {
    public RespostaIncompativelException(String motivo) {
        super(motivo);
    }
}
