package br.com.balaio.community.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.core.io.Resource;

final class ChavePublicaJwtLoader {
    private static final String INICIO_PEM = "-----BEGIN PUBLIC KEY-----";
    private static final String FIM_PEM = "-----END PUBLIC KEY-----";
    private static final long TAMANHO_MAXIMO_ARQUIVO = 16_384;
    private static final int TAMANHO_MINIMO_CHAVE_BITS = 3072;

    private ChavePublicaJwtLoader() {
    }

    static RSAPublicKey carregar(Resource recurso) {
        try {
            var chave = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(lerDer(recurso)));
            if (chave.getModulus().bitLength() < TAMANHO_MINIMO_CHAVE_BITS) {
                throw new IllegalArgumentException("Chave RSA menor que " + TAMANHO_MINIMO_CHAVE_BITS + " bits.");
            }
            return chave;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | ClassCastException erro) {
            throw new IllegalStateException("Chave pública JWT da community inválida.", erro);
        }
    }

    private static byte[] lerDer(Resource recurso) throws IOException {
        if (!recurso.exists() || recurso.contentLength() > TAMANHO_MAXIMO_ARQUIVO) {
            throw new IllegalArgumentException("Arquivo da chave ausente ou grande demais.");
        }
        String pem;
        try (var input = recurso.getInputStream()) {
            pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII).strip();
        }
        if (!pem.startsWith(INICIO_PEM) || !pem.endsWith(FIM_PEM)) {
            throw new IllegalArgumentException("Arquivo da chave não está em PEM.");
        }
        String corpo = pem.substring(INICIO_PEM.length(), pem.length() - FIM_PEM.length());
        return Base64.getDecoder().decode(corpo.replaceAll("\\s", ""));
    }
}
