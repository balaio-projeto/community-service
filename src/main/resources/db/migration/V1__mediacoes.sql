CREATE TABLE mediacoes (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL UNIQUE,
    comprador_id UUID NOT NULL,
    assunto VARCHAR(160) NOT NULL,
    relato TEXT NOT NULL,
    estado VARCHAR(40) NOT NULL,
    decisao VARCHAR(40),
    decisao_id UUID UNIQUE,
    tentativas INTEGER NOT NULL DEFAULT 0,
    ultimo_erro TEXT,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizada_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mediacoes_pendentes ON mediacoes(estado, atualizada_em);
