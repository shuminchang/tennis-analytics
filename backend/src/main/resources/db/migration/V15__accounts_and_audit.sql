CREATE TABLE app_users (
    id bigserial PRIMARY KEY,
    username varchar(64) NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9_.-]{3,64}$'),
    password_hash varchar(100) NOT NULL CHECK (length(password_hash)>=60),
    role varchar(16) NOT NULL CHECK (role IN ('MEMBER','ADVANCED','ADMIN')),
    enabled boolean NOT NULL DEFAULT true,
    failed_attempts integer NOT NULL DEFAULT 0 CHECK (failed_attempts>=0),
    locked_until timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at timestamptz
);
CREATE TABLE audit_log (
    id bigserial PRIMARY KEY,
    actor text,
    action text NOT NULL,
    subject text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_audit_created ON audit_log(created_at);
