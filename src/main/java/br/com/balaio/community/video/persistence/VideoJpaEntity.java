package br.com.balaio.community.video.persistence;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import br.com.balaio.community.video.domain.ArquivoMidia;
import br.com.balaio.community.video.domain.SituacaoVideo;
import br.com.balaio.community.video.domain.Video;

@Entity
@Table(name = "videos")
class VideoJpaEntity {
    @Id
    private UUID id;

    @Column(name = "artesao_id", nullable = false)
    private UUID artesaoId;

    @Column(name = "peca_slug", length = 160)
    private String pecaSlug;

    @Column(nullable = false, length = Video.TAMANHO_MAXIMO_LEGENDA)
    private String legenda;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "video_etiquetas", joinColumns = @JoinColumn(name = "video_id"))
    @OrderColumn(name = "posicao")
    @Column(name = "etiqueta", nullable = false, length = 30)
    private List<String> etiquetas = new ArrayList<>();

    @Column(length = 8)
    private String duracao;

    @Column(nullable = false, length = 20)
    private String situacao;

    @Column(name = "arquivo_chave", length = 200)
    private String arquivoChave;

    @Column(name = "arquivo_tipo", length = 40)
    private String arquivoTipo;

    @Column(name = "arquivo_tamanho")
    private Long arquivoTamanho;

    @Column(name = "capa_chave", length = 200)
    private String capaChave;

    @Column(name = "capa_tipo", length = 40)
    private String capaTipo;

    @Column(name = "capa_tamanho")
    private Long capaTamanho;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "publicado_em")
    private OffsetDateTime publicadoEm;

    protected VideoJpaEntity() {
    }

    static VideoJpaEntity de(Video video) {
        var entidade = new VideoJpaEntity();
        entidade.id = video.id();
        entidade.artesaoId = video.artesaoId();
        entidade.criadoEm = video.criadoEm();
        entidade.copiarDe(video);
        return entidade;
    }

    void copiarDe(Video video) {
        pecaSlug = video.pecaSlug();
        legenda = video.legenda();
        etiquetas.clear();
        etiquetas.addAll(video.etiquetas());
        duracao = video.duracao();
        situacao = video.situacao().codigo();
        arquivoChave = video.arquivo() == null ? null : video.arquivo().chave();
        arquivoTipo = video.arquivo() == null ? null : video.arquivo().tipoConteudo();
        arquivoTamanho = video.arquivo() == null ? null : video.arquivo().tamanhoBytes();
        capaChave = video.capa() == null ? null : video.capa().chave();
        capaTipo = video.capa() == null ? null : video.capa().tipoConteudo();
        capaTamanho = video.capa() == null ? null : video.capa().tamanhoBytes();
        publicadoEm = video.publicadoEm();
    }

    Video paraDominio() {
        return Video.reconstituir(new Video.Dados(id, artesaoId, criadoEm, pecaSlug, legenda, etiquetas, duracao,
                SituacaoVideo.deCodigo(situacao), arquivo(arquivoChave, arquivoTipo, arquivoTamanho),
                arquivo(capaChave, capaTipo, capaTamanho), publicadoEm));
    }

    private static ArquivoMidia arquivo(String chave, String tipo, Long tamanho) {
        return chave == null ? null : new ArquivoMidia(chave, tipo, tamanho);
    }
}
