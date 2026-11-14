package br.com.balaio.community.video.application.port.out;

import java.io.InputStream;
import java.util.Optional;

public interface ArmazenamentoMidiaPort {
    long gravar(String chave, InputStream conteudo, long tamanhoMaximoBytes);

    Optional<MidiaArmazenada> abrir(String chave);

    void remover(String chave);

    interface MidiaArmazenada {
        long tamanho();

        InputStream ler(long primeiroByte, long ultimoByte);
    }
}
