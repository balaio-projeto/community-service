package br.com.balaio.community.video.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.balaio.community.video.application.port.out.ArmazenamentoMidiaPort;

@Component
@ConditionalOnProperty(name = "balaio.midia.armazenamento", havingValue = "local", matchIfMissing = true)
class ArmazenamentoLocalAdapter implements ArmazenamentoMidiaPort {
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
            long gravados = ArquivoTemporarioLimitado.copiar(conteudo, temporario, tamanhoMaximoBytes);
            Files.move(temporario, destino, StandardCopyOption.ATOMIC_MOVE);
            return gravados;
        } catch (IOException falha) {
            throw new UncheckedIOException("Falha ao gravar mídia.", falha);
        } finally {
            ArquivoTemporarioLimitado.apagar(temporario);
        }
    }

    @Override
    public Optional<MidiaArmazenada> abrir(String chave) {
        Path arquivo = resolver(chave);
        if (!Files.isRegularFile(arquivo)) {
            return Optional.empty();
        }
        return Optional.of(new MidiaEmDisco(arquivo));
    }

    @Override
    public void remover(String chave) {
        try {
            Files.deleteIfExists(resolver(chave));
        } catch (IOException falha) {
            throw new UncheckedIOException("Não foi possível remover a mídia " + chave, falha);
        }
    }

    private Path resolver(String chave) {
        Path caminho = raiz.resolve(chave).normalize();
        if (!caminho.startsWith(raiz)) {
            throw new IllegalArgumentException("Chave de mídia fora do diretório.");
        }
        return caminho;
    }

    private record MidiaEmDisco(Path arquivo) implements MidiaArmazenada {
        @Override
        public long tamanho() {
            try {
                return Files.size(arquivo);
            } catch (IOException falha) {
                throw new UncheckedIOException(falha);
            }
        }

        @Override
        public InputStream ler(long primeiroByte, long ultimoByte) {
            try {
                var canal = FileChannel.open(arquivo).position(primeiroByte);
                return new TrechoInputStream(Channels.newInputStream(canal), ultimoByte - primeiroByte + 1);
            } catch (IOException falha) {
                throw new UncheckedIOException(falha);
            }
        }
    }
}
