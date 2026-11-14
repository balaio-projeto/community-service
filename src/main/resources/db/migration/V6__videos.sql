CREATE TABLE videos (
    id UUID PRIMARY KEY,
    artesao_id UUID NOT NULL,
    peca_slug VARCHAR(160),
    legenda VARCHAR(500) NOT NULL,
    duracao VARCHAR(8),
    situacao VARCHAR(20) NOT NULL,
    arquivo_chave VARCHAR(200),
    arquivo_tipo VARCHAR(40),
    arquivo_tamanho BIGINT,
    capa_chave VARCHAR(200),
    capa_tipo VARCHAR(40),
    capa_tamanho BIGINT,
    criado_em TIMESTAMPTZ NOT NULL,
    publicado_em TIMESTAMPTZ
);

CREATE INDEX idx_videos_publicados ON videos(situacao, publicado_em DESC);
CREATE INDEX idx_videos_artesao ON videos(artesao_id, criado_em DESC);

CREATE TABLE video_etiquetas (
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    posicao INTEGER NOT NULL,
    etiqueta VARCHAR(30) NOT NULL,
    PRIMARY KEY (video_id, posicao)
);

CREATE TABLE comentarios_video (
    id UUID PRIMARY KEY,
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    autor_id UUID NOT NULL,
    autor_nome VARCHAR(120) NOT NULL,
    autor_artesao BOOLEAN NOT NULL,
    texto VARCHAR(1000) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_comentarios_video ON comentarios_video(video_id, criado_em);
