package br.com.balaio.community.pergunta.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import br.com.balaio.community.pergunta.domain.Pergunta;

@Entity
@Table(name = "perguntas")
class PerguntaJpaEntity {
    @Id
    private UUID id;

    @Column(name = "peca_slug", nullable = false, length = 160)
    private String pecaSlug;

    @Column(name = "autor_id", nullable = false)
    private UUID autorId;

    @Column(name = "autor_nome", nullable = false, length = Pergunta.TAMANHO_MAXIMO_NOME_AUTOR)
    private String autorNome;

    @Column(nullable = false, length = Pergunta.TAMANHO_MAXIMO_PERGUNTA)
    private String texto;

    @Column(length = Pergunta.TAMANHO_MAXIMO_RESPOSTA)
    private String resposta;

    @Column(name = "criada_em", nullable = false)
    private OffsetDateTime criadaEm;

    @Column(name = "respondida_em")
    private OffsetDateTime respondidaEm;

    protected PerguntaJpaEntity() {
    }

    static PerguntaJpaEntity de(Pergunta pergunta) {
        var entidade = new PerguntaJpaEntity();
        entidade.id = pergunta.id();
        entidade.pecaSlug = pergunta.pecaSlug();
        entidade.autorId = pergunta.autorId();
        entidade.autorNome = pergunta.autorNome();
        entidade.texto = pergunta.texto();
        entidade.resposta = pergunta.resposta();
        entidade.criadaEm = pergunta.criadaEm();
        entidade.respondidaEm = pergunta.respondidaEm();
        return entidade;
    }

    Pergunta paraDominio() {
        return Pergunta.reconstituir(new Pergunta.Dados(id, pecaSlug, autorId, autorNome, texto, criadaEm,
                resposta, respondidaEm));
    }
}
