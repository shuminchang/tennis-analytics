ALTER TABLE matches
    ADD CONSTRAINT uq_matches_id_tour UNIQUE (id, tour);

CREATE TABLE match_sources (
    run_id bigint NOT NULL,
    snapshot_id bigint NOT NULL,
    source_file_id bigint NOT NULL,
    record_number bigint NOT NULL,
    tour varchar(3) NOT NULL,
    data_kind text NOT NULL DEFAULT 'MATCHES',
    match_id bigint NOT NULL,

    CONSTRAINT pk_match_sources
        PRIMARY KEY (run_id, source_file_id, record_number),

    CONSTRAINT fk_match_sources_run
        FOREIGN KEY (run_id, snapshot_id)
        REFERENCES import_runs (id, snapshot_id),

    CONSTRAINT fk_match_sources_file
        FOREIGN KEY (source_file_id, snapshot_id, tour, data_kind)
        REFERENCES source_files (id, snapshot_id, tour, data_kind),

    CONSTRAINT fk_match_sources_match
        FOREIGN KEY (match_id, tour)
        REFERENCES matches (id, tour),

    CONSTRAINT ck_match_sources_record_positive
        CHECK (record_number > 0),

    CONSTRAINT ck_match_sources_data_kind
        CHECK (data_kind = 'MATCHES')
);

CREATE INDEX ix_match_sources_match
    ON match_sources (match_id, tour);

CREATE INDEX ix_match_sources_file
    ON match_sources (source_file_id);
