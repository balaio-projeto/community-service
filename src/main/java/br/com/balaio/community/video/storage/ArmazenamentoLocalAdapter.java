package br.com.balaio.community.video.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;
import br.com.balaio.community.video.application.port.out.ArmazenamentoMidiaPort;

@Component
class ArmazenamentoLocalAdapter implements ArmazenamentoMidiaPort {
    private static final int TAMANHO_BLOCO = 64 * 1024;

    private final Path raiz;

    ArmazenamentoLocalAdapter(@Value("${balaio.midia.diretorio:./dados/midia}") String diretorio) {
        this.raiz = Path.of(diretorio).toAbsolutePath().normalize();
    }

    @Override
    public long gravar(String chave, InputStream conteudo, long tamanhoMaximoBytes) {
        Path destino = resolver(chave);
        Path temporario = null;
        try {
            Files.createDirectories(destino.getParent());
            temporario = Files.createTempFile(destino.getParent(), ".envio-", ".parcial");
            long gravados = copiarComLimite(conteudo, temporario, tamanhoMaximoBytes);
            Files.move(temporario, destino, StandardCopyOption.ATOMIC_MOVE);
            return gravados;
        } catch (IOException falha) {
            throw new UncheckedIOException("Falha ao gravar mídia.", falha);
        } finally {
            apagarSilenciosamente(temporario);
        }
    }

    @Override
    public Optional<Resource> abrir(String chave) {
        Path arquivo = resolver(chave);
        return Files.isRegularFile(arquivo) ? Optional.of(new FileSystemResource(arquivo)) : Optional.empty();
    }

    @Override
    public void remover(String chave) {
        apagarSilenciosamente(resolver(chave));
    }

    private Path resolver(String chave) {
        Path caminho = raiz.resolve(chave).normalize();
        if (!caminho.startsWith(raiz)) {
            throw new IllegalArgumentException("Chave de mídia fora do diretório.");
        }
        return caminho;
    }

    private static long copiarComLimite(InputStream origem, Path destino, long limite) throws IOException {
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

    private static void apagarSilenciosamente(Path caminho) {
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
