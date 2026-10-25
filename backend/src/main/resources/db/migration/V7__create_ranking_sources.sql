CREATE TABLE ranking_sources (
    run_id bigint NOT NULL,
    snapshot_id bigint NOT NULL,
    source_file_id bigint NOT NULL,
    record_number bigint NOT NULL,
    tour varchar(3) NOT NULL,
    data_kind text NOT NULL DEFAULT 'RANKINGS',
    ranking_date date NOT NULL,
    player_id integer NOT NULL,

    CONSTRAINT pk_ranking_sources
        PRIMARY KEY (run_id, source_file_id, record_number),

    CONSTRAINT fk_ranking_sources_run
        FOREIGN KEY (run_id, snapshot_id)
        REFERENCES import_runs (id, snapshot_id),

    CONSTRAINT fk_ranking_sources_file
        FOREIGN KEY (source_file_id, snapshot_id, tour, data_kind)
        REFERENCES source_files (id, snapshot_id, tour, data_kind),

    CONSTRAINT fk_ranking_sources_ranking
        FOREIGN KEY (tour, ranking_date, player_id)
        REFERENCES rankings (tour, ranking_date, player_id),

    CONSTRAINT ck_ranking_sources_record_positive
        CHECK (record_number > 0),

    CONSTRAINT ck_ranking_sources_data_kind
        CHECK (data_kind = 'RANKINGS')
);

CREATE INDEX ix_ranking_sources_ranking
    ON ranking_sources (tour, ranking_date, player_id);

CREATE INDEX ix_ranking_sources_file
    ON ranking_sources (source_file_id);
