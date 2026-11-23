ALTER TABLE mediacoes ADD COLUMN artesao_id UUID;
CREATE INDEX idx_mediacoes_artesao ON mediacoes(artesao_id, criada_em DESC);
