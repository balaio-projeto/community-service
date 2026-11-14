package br.com.balaio.community.video.domain;

import java.util.Map;
import java.util.Optional;

public enum TipoMidia {
    VIDEO(100L * 1024 * 1024, Map.of("video/mp4", "mp4", "video/webm", "webm")),
    CAPA(5L * 1024 * 1024, Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp"));

    private final long tamanhoMaximoBytes;
    private final Map<String, String> extensoesPorConteudo;

    TipoMidia(long tamanhoMaximoBytes, Map<String, String> extensoesPorConteudo) {
        this.tamanhoMaximoBytes = tamanhoMaximoBytes;
        this.extensoesPorConteudo = extensoesPorConteudo;
    }

    public long tamanhoMaximoBytes() {
        return tamanhoMaximoBytes;
    }

    public Optional<String> extensaoPara(String tipoConteudo) {
        return Optional.ofNullable(tipoConteudo).map(tipo -> tipo.split(";")[0].strip().toLowerCase())
                .map(extensoesPorConteudo::get);
    }
}
