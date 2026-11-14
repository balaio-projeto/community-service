package br.com.balaio.community.video.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

final class TrechoInputStream extends FilterInputStream {
    private long restantes;

    TrechoInputStream(InputStream origem, long quantidade) {
        super(origem);
        this.restantes = quantidade;
    }

    @Override
    public int read() throws IOException {
        if (restantes <= 0) {
            return -1;
        }
        int lido = super.read();
        if (lido != -1) {
            restantes--;
        }
        return lido;
    }

    @Override
    public int read(byte[] destino, int deslocamento, int tamanho) throws IOException {
        if (restantes <= 0) {
            return -1;
        }
        int lidos = super.read(destino, deslocamento, (int) Math.min(tamanho, restantes));
        if (lidos > 0) {
            restantes -= lidos;
        }
        return lidos;
    }
}
