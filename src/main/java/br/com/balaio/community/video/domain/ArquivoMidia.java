package br.com.balaio.community.video.domain;

import java.util.UUID;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

public record ArquivoMidia(String chave, String tipoConteudo, long tamanhoBytes) {

    public static ArquivoMidia novo(UUID videoId, TipoMidia tipo, String tipoConteudo, long tamanhoBytes) {
        String extensao = tipo.extensaoPara(tipoConteudo).orElseThrow(
                () -> new RequisicaoInvalidaException("Formato de arquivo não aceito."));
        if (tamanhoBytes == 0 || tamanhoBytes > tipo.tamanhoMaximoBytes()) {
            throw new RequisicaoInvalidaException(
                    "O arquivo deve ter até " + tipo.tamanhoMaximoBytes() / (1024 * 1024) + " MB.");
        }
        String chave = tipo.name().toLowerCase() + "/" + videoId + "-" + UUID.randomUUID() + "." + extensao;
        return new ArquivoMidia(chave, tipoConteudo.split(";")[0].strip().toLowerCase(), tamanhoBytes);
    }

    public ArquivoMidia comTamanhoReal(long bytesGravados) {
        return new ArquivoMidia(chave, tipoConteudo, bytesGravados);
    }
}
