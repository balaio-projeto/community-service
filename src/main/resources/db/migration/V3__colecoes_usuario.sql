CREATE TABLE itens_colecao (
    usuario_id UUID NOT NULL,
    colecao VARCHAR(30) NOT NULL,
    valor VARCHAR(160) NOT NULL,
    posicao INTEGER NOT NULL,
    PRIMARY KEY (usuario_id, colecao, valor)
);

CREATE INDEX idx_itens_colecao_ordem ON itens_colecao(usuario_id, colecao, posicao);
