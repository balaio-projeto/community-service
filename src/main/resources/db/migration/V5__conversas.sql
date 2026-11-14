CREATE TABLE mensagens_pedido (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL,
    autor_id UUID NOT NULL,
    autor VARCHAR(20) NOT NULL,
    texto VARCHAR(2000) NOT NULL,
    enviada_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_mensagens_pedido ON mensagens_pedido(pedido_id, enviada_em);

CREATE TABLE leituras_conversa (
    pedido_id UUID NOT NULL,
    leitor_id UUID NOT NULL,
    lida_em TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (pedido_id, leitor_id)
);
