CREATE TABLE elo_runs (
    id bigserial PRIMARY KEY,
    status text NOT NULL CHECK (status IN ('RUNNING','SUCCEEDED','FAILED','REUSED')),
    rules_sha256 varchar(64) NOT NULL,
    input_sha256 varchar(64),
    reused_run_id bigint REFERENCES elo_runs(id),
    matches bigint NOT NULL DEFAULT 0,
    started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at timestamptz,
    error_summary text
);
CREATE TABLE elo_history (
    run_id bigint NOT NULL REFERENCES elo_runs(id),
    tour varchar(3) NOT NULL,
    player_id integer NOT NULL,
    match_id bigint NOT NULL REFERENCES matches(id),
    dimension text NOT NULL,
    sequence integer NOT NULL,
    observed_date date NOT NULL,
    rating_before double precision NOT NULL,
    rating_after double precision NOT NULL,
    matches integer NOT NULL,
    PRIMARY KEY(run_id,tour,player_id,match_id,dimension),
    FOREIGN KEY(tour,player_id) REFERENCES players(tour,player_id)
);
CREATE INDEX ix_elo_history_player ON elo_history(run_id,tour,player_id,dimension,sequence);
CREATE TABLE elo_current (
    run_id bigint NOT NULL REFERENCES elo_runs(id),
    tour varchar(3) NOT NULL,
    player_id integer NOT NULL,
    dimension text NOT NULL,
    rating double precision NOT NULL,
    matches integer NOT NULL,
    observed_date date NOT NULL,
    PRIMARY KEY(run_id,tour,player_id,dimension),
    FOREIGN KEY(tour,player_id) REFERENCES players(tour,player_id)
);
