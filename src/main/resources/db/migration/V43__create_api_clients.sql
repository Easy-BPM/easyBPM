CREATE TABLE api_client (
    id UUID PRIMARY KEY,
    selector VARCHAR(64) NOT NULL UNIQUE,
    normalized_name VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    secret_hash VARCHAR(100) NOT NULL,
    credential_generation INTEGER NOT NULL DEFAULT 1,
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (lifecycle_status IN ('ACTIVE', 'REVOKED')),
    expires_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    last_used_ip VARCHAR(64),
    revoked_at TIMESTAMP,
    revoked_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE api_client_permission (
    api_client_id UUID NOT NULL REFERENCES api_client(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES app_permission(id),
    PRIMARY KEY (api_client_id, permission_id)
);

CREATE TABLE api_client_audit (
    id BIGSERIAL PRIMARY KEY,
    api_client_id UUID NOT NULL REFERENCES api_client(id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL CHECK (category IN ('LIFECYCLE', 'USE')),
    action VARCHAR(40) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    actor VARCHAR(255),
    credential_generation INTEGER,
    remote_ip VARCHAR(64),
    http_method VARCHAR(12),
    request_path VARCHAR(1024),
    http_status INTEGER,
    request_id VARCHAR(128),
    duration_ms BIGINT,
    changed_fields JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_client_status ON api_client(lifecycle_status);
CREATE INDEX idx_api_client_expiry ON api_client(expires_at);
CREATE INDEX idx_api_client_audit_client_time ON api_client_audit(api_client_id, created_at DESC);
CREATE INDEX idx_api_client_audit_category_time ON api_client_audit(category, created_at DESC);
CREATE INDEX idx_api_client_audit_time ON api_client_audit(created_at);

INSERT INTO app_permission(code, name, created_at, updated_at)
VALUES
    ('VIEW_API_CLIENTS', 'VIEW API CLIENTS', NOW(), NOW()),
    ('MANAGE_API_CLIENTS', 'MANAGE API CLIENTS', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;
