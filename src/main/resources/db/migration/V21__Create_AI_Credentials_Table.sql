CREATE TABLE ai_credentials (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    provider_id VARCHAR(50) NOT NULL,
    credential_type VARCHAR(20) NOT NULL,
    encrypted_token VARCHAR(2048) NOT NULL,
    owner_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    description VARCHAR(500) NULL,
    
    CONSTRAINT uk_ai_creds_provider_owner UNIQUE (provider_id, owner_id)
);

CREATE INDEX idx_ai_creds_owner ON ai_credentials(owner_id);
CREATE INDEX idx_ai_creds_provider ON ai_credentials(provider_id);
CREATE INDEX idx_ai_creds_created ON ai_credentials(created_at);
CREATE INDEX idx_ai_creds_active ON ai_credentials(is_active);

-- RBAC permissions: many-to-many between credentials and roles
CREATE TABLE ai_credential_permissions (
    credential_id VARCHAR(36) NOT NULL,
    role VARCHAR(50) NOT NULL,
    
    PRIMARY KEY (credential_id, role),
    CONSTRAINT fk_ai_cred_perms_cred FOREIGN KEY (credential_id) REFERENCES ai_credentials(id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_cred_perms_cred ON ai_credential_permissions(credential_id);
CREATE INDEX idx_ai_cred_perms_role ON ai_credential_permissions(role);

-- Audit log for credential access (optional, for compliance)
CREATE TABLE ai_credential_audit_log (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    action VARCHAR(50) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    provider_id VARCHAR(50) NOT NULL,
    credential_id VARCHAR(36) NULL,
    success BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_ai_audit_cred FOREIGN KEY (credential_id) REFERENCES ai_credentials(id) ON DELETE SET NULL
);

CREATE INDEX idx_ai_audit_user ON ai_credential_audit_log(user_id);
CREATE INDEX idx_ai_audit_action ON ai_credential_audit_log(action);
CREATE INDEX idx_ai_audit_created ON ai_credential_audit_log(created_at);
