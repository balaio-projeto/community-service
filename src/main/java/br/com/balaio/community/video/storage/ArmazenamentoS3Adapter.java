package br.com.balaio.community.video.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import br.com.balaio.community.video.application.port.out.ArmazenamentoMidiaPort;

@Component
@ConditionalOnProperty(name = "balaio.midia.armazenamento", havingValue = "s3")
class ArmazenamentoS3Adapter implements ArmazenamentoMidiaPort {
    private static final int HTTP_NAO_ENCONTRADO = 404;

    private final S3Client s3;
    private final String bucket;

    ArmazenamentoS3Adapter(@Value("${balaio.midia.s3.endpoint}") String endpoint,
            @Value("${balaio.midia.s3.regiao:auto}") String regiao,
            @Value("${balaio.midia.s3.bucket}") String bucket,
            @Value("${balaio.midia.s3.chave-acesso}") String chaveAcesso,
            @Value("${balaio.midia.s3.chave-secreta}") String chaveSecreta) {
        this(criarCliente(endpoint, regiao, chaveAcesso, chaveSecreta), bucket);
    }

    static S3Client criarCliente(String endpoint, String regiao, String chaveAcesso, String chaveSecreta) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(regiao))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(chaveAcesso, chaveSecreta)))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    ArmazenamentoS3Adapter(S3Client s3, String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("Bucket de mídia não configurado.");
        }
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public long gravar(String chave, InputStream conteudo, long tamanhoMaximoBytes) {
        Path temporario = null;
        try {
            temporario = Files.createTempFile("community-envio-", ".parcial");
            long gravados = ArquivoTemporarioLimitado.copiar(conteudo, temporario, tamanhoMaximoBytes);
            s3.putObject(pedido -> pedido.bucket(bucket).key(chave).contentLength(gravados),
                    RequestBody.fromFile(temporario));
            return gravados;
        } catch (IOException falha) {
            throw new UncheckedIOException("Falha ao preparar envio de mídia.", falha);
        } finally {
            ArquivoTemporarioLimitado.apagar(temporario);
        }
    }

    @Override
    public Optional<MidiaArmazenada> abrir(String chave) {
        try {
            long tamanho = s3.headObject(pedido -> pedido.bucket(bucket).key(chave)).contentLength();
            return Optional.of(new MidiaNoS3(chave, tamanho));
        } catch (NoSuchKeyException inexistente) {
            return Optional.empty();
        } catch (S3Exception falha) {
            if (falha.statusCode() == HTTP_NAO_ENCONTRADO) {
                return Optional.empty();
            }
            throw falha;
        }
    }

    @Override
    public void remover(String chave) {
        s3.deleteObject(pedido -> pedido.bucket(bucket).key(chave));
    }

    private final class MidiaNoS3 implements MidiaArmazenada {
        private final String chave;
        private final long tamanho;

        private MidiaNoS3(String chave, long tamanho) {
            this.chave = chave;
            this.tamanho = tamanho;
        }

        @Override
        public long tamanho() {
            return tamanho;
        }

        @Override
        public InputStream ler(long primeiroByte, long ultimoByte) {
            return s3.getObject(pedido -> pedido.bucket(bucket).key(chave)
                    .range("bytes=" + primeiroByte + "-" + ultimoByte));
        }
    }
}
