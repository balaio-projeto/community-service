package br.com.balaio.community.video.application.port.out;

import java.io.InputStream;
import java.util.Optional;

import org.springframework.core.io.Resource;

public interface ArmazenamentoMidiaPort {
    long gravar(String chave, InputStream conteudo, long tamanhoMaximoBytes);

    Optional<Resource> abrir(String chave);

    void remover(String chave);
}
