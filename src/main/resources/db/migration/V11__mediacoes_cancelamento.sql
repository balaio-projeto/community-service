ALTER TABLE mediacoes ADD COLUMN cancelada_por UUID;
ALTER TABLE mediacoes ADD COLUMN cancelada_em TIMESTAMPTZ;
ALTER TABLE mediacoes DROP CONSTRAINT mediacoes_pedido_id_key;
CREATE UNIQUE INDEX idx_mediacoes_pedido_ativa ON mediacoes(pedido_id) WHERE estado <> 'cancelada';
