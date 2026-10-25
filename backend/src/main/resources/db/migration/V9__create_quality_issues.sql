CREATE TABLE quality_issues (
    id bigserial PRIMARY KEY,
    run_id bigint NOT NULL,
    snapshot_id bigint NOT NULL,
    source_file_id bigint NOT NULL,
    record_number bigint NOT NULL,
    tour varchar(3) NOT NULL,
    data_kind text NOT NULL,
    rule_code text NOT NULL,
    field_name text NOT NULL DEFAULT '',
    role text NOT NULL DEFAULT 'RECORD',
    action text NOT NULL,
    reason text NOT NULL,
    evidence jsonb,
    status text NOT NULL DEFAULT 'OPEN',
    resolution_note text,
    resolved_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_quality_issues_source_rule
        UNIQUE (run_id, source_file_id, record_number, rule_code, field_name, role),
    CONSTRAINT fk_quality_issues_run FOREIGN KEY (run_id, snapshot_id)
        REFERENCES import_runs (id, snapshot_id),
    CONSTRAINT fk_quality_issues_file
        FOREIGN KEY (source_file_id, snapshot_id, tour, data_kind)
        REFERENCES source_files (id, snapshot_id, tour, data_kind),
    CONSTRAINT ck_quality_issues_record_positive CHECK (record_number > 0),
    CONSTRAINT ck_quality_issues_rule CHECK (btrim(rule_code) <> ''),
    CONSTRAINT ck_quality_issues_field CHECK (field_name = '' OR btrim(field_name) <> ''),
    CONSTRAINT ck_quality_issues_role CHECK (role IN ('RECORD', 'PLAYER', 'WINNER', 'LOSER')),
    CONSTRAINT ck_quality_issues_action CHECK (action IN ('FLAG', 'QUARANTINE')),
    CONSTRAINT ck_quality_issues_reason CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_quality_issues_evidence CHECK (jsonb_typeof(evidence) = 'object'),
    CONSTRAINT ck_quality_issues_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_quality_issues_resolution CHECK (
        (status = 'OPEN' AND resolution_note IS NULL AND resolved_at IS NULL)
        OR (status IN ('RESOLVED', 'DISMISSED') AND resolution_note IS NOT NULL
            AND btrim(resolution_note) <> '' AND resolved_at IS NOT NULL
            AND resolved_at >= created_at)
    )
);

CREATE INDEX ix_quality_issues_file ON quality_issues (source_file_id);

-- One issue may affect several metrics. Codes and their meanings live in versioned rules.
CREATE TABLE quality_issue_impacts (
    issue_id bigint NOT NULL REFERENCES quality_issues (id),
    metric_code text NOT NULL,
    CONSTRAINT pk_quality_issue_impacts PRIMARY KEY (issue_id, metric_code),
    CONSTRAINT ck_quality_issue_impacts_metric CHECK (btrim(metric_code) <> '')
);
