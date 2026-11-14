CREATE TABLE perguntas (
    id UUID PRIMARY KEY,
    peca_slug VARCHAR(160) NOT NULL,
    autor_id UUID NOT NULL,
    autor_nome VARCHAR(120) NOT NULL,
    texto VARCHAR(1000) NOT NULL,
    resposta VARCHAR(2000),
    criada_em TIMESTAMPTZ NOT NULL,
    respondida_em TIMESTAMPTZ
);

CREATE INDEX idx_perguntas_peca ON perguntas(peca_slug, criada_em);
