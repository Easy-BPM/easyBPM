CREATE TABLE app_tenant (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO app_tenant (code, name, enabled)
VALUES ('default', 'Default Tenant', TRUE)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE app_user ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE app_group ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';

ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_username_key;
ALTER TABLE app_group DROP CONSTRAINT IF EXISTS app_group_code_key;

CREATE UNIQUE INDEX ux_app_user_tenant_username ON app_user (tenant_id, username);
CREATE UNIQUE INDEX ux_app_group_tenant_code ON app_group (tenant_id, code);
CREATE INDEX ix_app_user_tenant ON app_user (tenant_id);
CREATE INDEX ix_app_group_tenant ON app_group (tenant_id);
