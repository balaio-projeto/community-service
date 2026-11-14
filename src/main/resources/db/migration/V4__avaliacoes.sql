CREATE TABLE avaliacoes (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL UNIQUE,
    comprador_id UUID NOT NULL,
    artesao_id UUID NOT NULL,
    peca_slug VARCHAR(160) NOT NULL,
    nota SMALLINT NOT NULL CHECK (nota BETWEEN 1 AND 5),
    comentario VARCHAR(1000),
    criada_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_avaliacoes_artesao ON avaliacoes(artesao_id);

CREATE TABLE avaliacao_aspectos (
    avaliacao_id UUID NOT NULL REFERENCES avaliacoes(id) ON DELETE CASCADE,
    posicao INTEGER NOT NULL,
    aspecto VARCHAR(60) NOT NULL,
    PRIMARY KEY (avaliacao_id, posicao)
);
