ALTER TABLE ai_credentials ADD COLUMN IF NOT EXISTS secret_name VARCHAR(100);

UPDATE ai_credentials
SET secret_name = provider_id
WHERE secret_name IS NULL OR secret_name = '';

ALTER TABLE ai_credentials ALTER COLUMN secret_name SET NOT NULL;

ALTER TABLE ai_credentials DROP CONSTRAINT IF EXISTS uk_ai_creds_provider_owner;
ALTER TABLE ai_credentials ADD CONSTRAINT uk_ai_creds_owner_secret_name UNIQUE (owner_id, secret_name);

CREATE INDEX IF NOT EXISTS idx_ai_creds_owner_active ON ai_credentials(owner_id, is_active);
