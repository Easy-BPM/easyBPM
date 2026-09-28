CREATE TABLE api_client_scope (
    api_client_id UUID NOT NULL REFERENCES api_client(id) ON DELETE CASCADE,
    scope_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (api_client_id, scope_code)
);

-- User permissions previously assigned to service identities are intentionally
-- not migrated. API clients restart with no scopes and must be explicitly
-- granted least-privilege API access by an administrator.
DROP TABLE api_client_permission;
