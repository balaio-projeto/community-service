package br.com.balaio.community.shared.exception;

public class NaoAutenticadoException extends CommunityException {
    public NaoAutenticadoException() {
        super("NAO_AUTENTICADO", "Autenticação necessária.");
    }
}
