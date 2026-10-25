ALTER TABLE import_runs
    ADD CONSTRAINT uq_import_runs_id_snapshot
    UNIQUE (id, snapshot_id);

ALTER TABLE source_files
    ADD CONSTRAINT uq_source_files_id_snapshot_tour_kind
    UNIQUE (id, snapshot_id, tour, data_kind);

CREATE TABLE player_sources (
    run_id bigint NOT NULL,
    snapshot_id bigint NOT NULL,
    source_file_id bigint NOT NULL,
    record_number bigint NOT NULL,
    tour varchar(3) NOT NULL,
    data_kind text NOT NULL DEFAULT 'PLAYERS',
    player_id integer NOT NULL,

    CONSTRAINT pk_player_sources
        PRIMARY KEY (run_id, source_file_id, record_number),

    CONSTRAINT fk_player_sources_run
        FOREIGN KEY (run_id, snapshot_id)
        REFERENCES import_runs (id, snapshot_id),

    CONSTRAINT fk_player_sources_file
        FOREIGN KEY (source_file_id, snapshot_id, tour, data_kind)
        REFERENCES source_files (id, snapshot_id, tour, data_kind),

    CONSTRAINT fk_player_sources_player
        FOREIGN KEY (tour, player_id)
        REFERENCES players (tour, player_id),

    CONSTRAINT ck_player_sources_record_positive
        CHECK (record_number > 0),

    CONSTRAINT ck_player_sources_data_kind
        CHECK (data_kind = 'PLAYERS')
);

CREATE INDEX ix_player_sources_player
    ON player_sources (tour, player_id);

CREATE INDEX ix_player_sources_file
    ON player_sources (source_file_id);