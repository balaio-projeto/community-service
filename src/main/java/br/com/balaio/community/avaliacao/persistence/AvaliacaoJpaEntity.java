package br.com.balaio.community.avaliacao.persistence;

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

import br.com.balaio.community.avaliacao.domain.Avaliacao;

@Entity
@Table(name = "avaliacoes")
class AvaliacaoJpaEntity {
    @Id
    private UUID id;

    @Column(name = "pedido_id", nullable = false, unique = true)
    private UUID pedidoId;

    @Column(name = "comprador_id", nullable = false)
    private UUID compradorId;

    @Column(name = "artesao_id", nullable = false)
    private UUID artesaoId;

    @Column(name = "peca_slug", nullable = false, length = 160)
    private String pecaSlug;

    @Column(nullable = false)
    private short nota;

    @Column(length = Avaliacao.TAMANHO_MAXIMO_COMENTARIO)
    private String comentario;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "avaliacao_aspectos", joinColumns = @JoinColumn(name = "avaliacao_id"))
    @OrderColumn(name = "posicao")
    @Column(name = "aspecto", nullable = false, length = Avaliacao.TAMANHO_MAXIMO_ASPECTO)
    private List<String> aspectos = new ArrayList<>();

    @Column(name = "criada_em", nullable = false)
    private OffsetDateTime criadaEm;

    protected AvaliacaoJpaEntity() {
    }

    static AvaliacaoJpaEntity de(Avaliacao avaliacao) {
        var entidade = new AvaliacaoJpaEntity();
        entidade.id = avaliacao.id();
        entidade.pedidoId = avaliacao.pedidoId();
        entidade.compradorId = avaliacao.compradorId();
        entidade.artesaoId = avaliacao.artesaoId();
        entidade.pecaSlug = avaliacao.pecaSlug();
        entidade.nota = (short) avaliacao.nota();
        entidade.comentario = avaliacao.comentario();
        entidade.aspectos = new ArrayList<>(avaliacao.aspectos());
        entidade.criadaEm = avaliacao.criadaEm();
        return entidade;
    }

    Avaliacao paraDominio() {
        return new Avaliacao(id, pedidoId, compradorId, artesaoId, pecaSlug, nota, comentario, aspectos, criadaEm);
    }
}
