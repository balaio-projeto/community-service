package br.com.balaio.community.mediacao.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import br.com.balaio.community.mediacao.domain.DecisaoMediacao;
import br.com.balaio.community.mediacao.domain.EstadoMediacao;
import br.com.balaio.community.mediacao.domain.Mediacao;

@Entity
@Table(name = "mediacoes")
class MediacaoJpaEntity {
    @Id
    private UUID id;

    @Column(name = "pedido_id", nullable = false, unique = true)
    private UUID pedidoId;

    @Column(name = "comprador_id", nullable = false)
    private UUID compradorId;

    @Column(name = "artesao_id")
    private UUID artesaoId;

    @Column(nullable = false, length = Mediacao.TAMANHO_MAXIMO_ASSUNTO)
    private String assunto;

    @Column(nullable = false, columnDefinition = "text")
    private String relato;

    @Column(nullable = false, length = 40)
    private String estado;

    @Column(length = 40)
    private String decisao;

    @Column(name = "decisao_id", unique = true)
    private UUID decisaoId;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", columnDefinition = "text")
    private String ultimoErro;

    @Column(name = "criada_em", nullable = false)
    private OffsetDateTime criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private OffsetDateTime atualizadaEm;

    protected MediacaoJpaEntity() {
    }

    static MediacaoJpaEntity de(Mediacao mediacao) {
        var entidade = new MediacaoJpaEntity();
        entidade.id = mediacao.id();
        entidade.pedidoId = mediacao.pedidoId();
        entidade.compradorId = mediacao.compradorId();
        entidade.artesaoId = mediacao.artesaoId();
        entidade.assunto = mediacao.assunto();
        entidade.relato = mediacao.relato();
        entidade.criadaEm = mediacao.criadaEm();
        entidade.copiarEstadoDe(mediacao);
        return entidade;
    }

    void copiarEstadoDe(Mediacao mediacao) {
        estado = mediacao.estado().codigo();
        decisao = mediacao.decisao() == null ? null : mediacao.decisao().codigo();
        decisaoId = mediacao.decisaoId();
        tentativas = mediacao.tentativasEntrega();
        ultimoErro = mediacao.ultimoErroEntrega();
        atualizadaEm = mediacao.atualizadaEm();
    }

    Mediacao paraDominio() {
        return Mediacao.reconstituir(new Mediacao.Dados(id, pedidoId, compradorId, artesaoId, assunto, relato,
                EstadoMediacao.deCodigo(estado), decisao == null ? null : DecisaoMediacao.deCodigo(decisao),
                decisaoId, tentativas, ultimoErro, criadaEm, atualizadaEm));
    }
}
