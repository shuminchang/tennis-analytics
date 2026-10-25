CREATE TABLE tournament_decisions (
    run_id bigint NOT NULL,
    snapshot_id bigint NOT NULL,
    tour varchar(3) NOT NULL,
    tourney_id text NOT NULL,
    source_file_id bigint NOT NULL,
    record_number bigint NOT NULL,
    data_kind text NOT NULL DEFAULT 'MATCHES',
    scope_status text NOT NULL,
    analysis_category text,
    rule_code text NOT NULL,
    reason text NOT NULL,
    evidence text NOT NULL,

    CONSTRAINT pk_tournament_decisions PRIMARY KEY (run_id, tour, tourney_id),
    CONSTRAINT fk_tournament_decisions_run FOREIGN KEY (run_id, snapshot_id)
        REFERENCES import_runs (id, snapshot_id),
    CONSTRAINT fk_tournament_decisions_file
        FOREIGN KEY (source_file_id, snapshot_id, tour, data_kind)
        REFERENCES source_files (id, snapshot_id, tour, data_kind),
    CONSTRAINT ck_tournament_decisions_id CHECK (btrim(tourney_id) <> ''),
    CONSTRAINT ck_tournament_decisions_record CHECK (record_number > 0),
    CONSTRAINT ck_tournament_decisions_kind CHECK (data_kind = 'MATCHES'),
    CONSTRAINT ck_tournament_decisions_status CHECK (scope_status IN ('INCLUDE', 'EXCLUDE', 'REVIEW')),
    CONSTRAINT ck_tournament_decisions_category CHECK (btrim(analysis_category) <> ''),
    CONSTRAINT ck_tournament_decisions_rule CHECK (btrim(rule_code) <> ''),
    CONSTRAINT ck_tournament_decisions_reason CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_tournament_decisions_evidence CHECK (btrim(evidence) <> '')
);

CREATE INDEX ix_tournament_decisions_event ON tournament_decisions (tour, tourney_id);
CREATE INDEX ix_tournament_decisions_file ON tournament_decisions (source_file_id);
