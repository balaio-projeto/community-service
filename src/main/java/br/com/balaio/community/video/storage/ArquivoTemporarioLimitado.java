package br.com.balaio.community.video.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

final class ArquivoTemporarioLimitado {
    private static final int TAMANHO_BLOCO = 64 * 1024;

    private ArquivoTemporarioLimitado() {
    }

    static long copiar(InputStream origem, Path destino, long limite) throws IOException {
        try (var saida = Files.newOutputStream(destino)) {
            byte[] bloco = new byte[TAMANHO_BLOCO];
            long total = 0;
            int lidos;
            while ((lidos = origem.read(bloco)) != -1) {
                total += lidos;
                if (total > limite) {
                    throw new RequisicaoInvalidaException("O arquivo excede o tamanho permitido.");
                }
                saida.write(bloco, 0, lidos);
            }
            if (total == 0) {
                throw new RequisicaoInvalidaException("O arquivo está vazio.");
            }
            return total;
        }
    }

    static void apagar(Path caminho) {
        if (caminho == null) {
            return;
        }
        try {
            Files.deleteIfExists(caminho);
        } catch (IOException ignorada) {
            caminho.toFile().deleteOnExit();
        }
    }
}
