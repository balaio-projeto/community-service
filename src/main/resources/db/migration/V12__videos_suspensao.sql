ALTER TABLE videos ADD COLUMN suspenso_motivo VARCHAR(1000);
ALTER TABLE videos ADD COLUMN suspenso_por UUID;
ALTER TABLE videos ADD COLUMN suspenso_em TIMESTAMPTZ;

ALTER TABLE videos ADD CONSTRAINT ck_videos_suspensao_completa CHECK (
    (suspenso_em IS NULL AND suspenso_motivo IS NULL AND suspenso_por IS NULL)
    OR (suspenso_em IS NOT NULL AND suspenso_motivo IS NOT NULL AND suspenso_por IS NOT NULL)
);

CREATE INDEX idx_videos_suspensos ON videos(suspenso_em DESC) WHERE suspenso_em IS NOT NULL;
