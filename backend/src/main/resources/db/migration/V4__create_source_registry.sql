CREATE TABLE source_snapshots (
    id bigserial,
    repository_url text NOT NULL,
    commit_sha varchar(40) NOT NULL,
    archive_location text NOT NULL,
    registered_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_source_snapshots
        PRIMARY KEY (id),

    CONSTRAINT uq_source_snapshots_repository_commit
        UNIQUE (repository_url, commit_sha),

    CONSTRAINT ck_source_snapshots_repository_not_blank
        CHECK (btrim(repository_url) <> ''),

    CONSTRAINT ck_source_snapshots_commit_sha
        CHECK (commit_sha ~ '^[0-9a-f]{40}$'),

    CONSTRAINT ck_source_snapshots_archive_not_blank
        CHECK (btrim(archive_location) <> '')
);

CREATE TABLE source_files (
    id bigserial,
    snapshot_id bigint NOT NULL,
    relative_path text NOT NULL,
    tour varchar(3) NOT NULL,
    data_kind text NOT NULL,
    sha256 varchar(64) NOT NULL,
    byte_size bigint NOT NULL,
    record_count bigint NOT NULL,

    CONSTRAINT pk_source_files
        PRIMARY KEY (id),

    CONSTRAINT fk_source_files_snapshot
        FOREIGN KEY (snapshot_id)
        REFERENCES source_snapshots (id),

    CONSTRAINT uq_source_files_snapshot_path
        UNIQUE (snapshot_id, relative_path),

    CONSTRAINT ck_source_files_path_not_blank
        CHECK (btrim(relative_path) <> ''),

    CONSTRAINT ck_source_files_tour
        CHECK (tour IN ('ATP', 'WTA')),

    CONSTRAINT ck_source_files_data_kind
        CHECK (data_kind IN ('PLAYERS', 'MATCHES', 'RANKINGS')),

    CONSTRAINT ck_source_files_sha256
        CHECK (sha256 ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_source_files_byte_size_nonnegative
        CHECK (byte_size >= 0),

    CONSTRAINT ck_source_files_record_count_nonnegative
        CHECK (record_count >= 0)
);