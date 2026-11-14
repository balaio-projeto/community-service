package br.com.balaio.community.video.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

class ArmazenamentoLocalAdapterTest {
    @TempDir Path raiz;

    @Test
    void gravaLeERemoveArquivo() throws Exception {
        var armazenamento = new ArmazenamentoLocalAdapter(raiz.toString());

        long gravados = armazenamento.gravar("video/a.mp4", new ByteArrayInputStream(new byte[] { 1, 2, 3 }), 10);

        assertThat(gravados).isEqualTo(3);
        assertThat(armazenamento.abrir("video/a.mp4")).isPresent();
        armazenamento.remover("video/a.mp4");
        assertThat(armazenamento.abrir("video/a.mp4")).isEmpty();
    }

    @Test
    void leApenasOTrechoPedido() throws Exception {
        var armazenamento = new ArmazenamentoLocalAdapter(raiz.toString());
        armazenamento.gravar("video/c.mp4", new ByteArrayInputStream("0123456789".getBytes()), 100);

        var midia = armazenamento.abrir("video/c.mp4").orElseThrow();
        assertThat(midia.tamanho()).isEqualTo(10);
        try (var trecho = midia.ler(3, 6)) {
            assertThat(new String(trecho.readAllBytes())).isEqualTo("3456");
        }
    }

    @Test
    void arquivoAcimaDoLimiteNaoDeixaRestos() throws Exception {
        var armazenamento = new ArmazenamentoLocalAdapter(raiz.toString());

        assertThatThrownBy(() -> armazenamento.gravar("video/b.mp4", new ByteArrayInputStream(new byte[20]), 10))
                .isInstanceOf(RequisicaoInvalidaException.class);
        try (var arquivos = Files.walk(raiz)) {
            assertThat(arquivos.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void chaveNaoEscapaDoDiretorio() {
        var armazenamento = new ArmazenamentoLocalAdapter(raiz.toString());

        assertThatThrownBy(() -> armazenamento.abrir("../fora.txt")).isInstanceOf(IllegalArgumentException.class);
    }
}
