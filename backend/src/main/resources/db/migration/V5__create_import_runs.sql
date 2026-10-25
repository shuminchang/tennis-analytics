CREATE TABLE import_runs (
    id bigserial,
    snapshot_id bigint NOT NULL,
    importer_commit_sha varchar(40) NOT NULL,
    transform_rules_sha256 varchar(64) NOT NULL,
    quality_rules_sha256 varchar(64) NOT NULL,
    scope_rules_sha256 varchar(64) NOT NULL,
    status text NOT NULL DEFAULT 'RUNNING',
    started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at timestamptz,
    error_summary text,

    CONSTRAINT pk_import_runs
        PRIMARY KEY (id),

    CONSTRAINT fk_import_runs_snapshot
        FOREIGN KEY (snapshot_id)
        REFERENCES source_snapshots (id),

    CONSTRAINT ck_import_runs_importer_commit
        CHECK (importer_commit_sha ~ '^[0-9a-f]{40}$'),

    CONSTRAINT ck_import_runs_transform_rules
        CHECK (transform_rules_sha256 ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_import_runs_quality_rules
        CHECK (quality_rules_sha256 ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_import_runs_scope_rules
        CHECK (scope_rules_sha256 ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_import_runs_status
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),

    CONSTRAINT ck_import_runs_time_order
        CHECK (finished_at IS NULL OR finished_at >= started_at),

    CONSTRAINT ck_import_runs_state_details
        CHECK (
            (
                status = 'RUNNING'
                AND finished_at IS NULL
                AND error_summary IS NULL
            )
            OR
            (
                status = 'SUCCEEDED'
                AND finished_at IS NOT NULL
                AND error_summary IS NULL
            )
            OR
            (
                status = 'FAILED'
                AND finished_at IS NOT NULL
                AND error_summary IS NOT NULL
                AND btrim(error_summary) <> ''
            )
        )
);

CREATE INDEX ix_import_runs_snapshot_started
    ON import_runs (snapshot_id, started_at);