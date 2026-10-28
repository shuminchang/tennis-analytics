-- Existing runs were created by the player-only importer.
ALTER TABLE import_runs ADD COLUMN data_kind text NOT NULL DEFAULT 'PLAYERS';
ALTER TABLE import_runs ADD CONSTRAINT ck_import_runs_data_kind
    CHECK (data_kind IN ('PLAYERS', 'RANKINGS', 'MATCHES'));
CREATE INDEX ix_import_runs_kind_status ON import_runs(data_kind, status, snapshot_id);
