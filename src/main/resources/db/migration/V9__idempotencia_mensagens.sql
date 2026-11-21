ALTER TABLE mensagens_pedido ADD COLUMN chave_idempotencia VARCHAR(160);
CREATE UNIQUE INDEX idx_mensagens_autor_chave ON mensagens_pedido(autor_id, chave_idempotencia)
    WHERE chave_idempotencia IS NOT NULL;
