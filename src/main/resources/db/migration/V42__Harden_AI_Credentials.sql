ALTER TABLE ai_credentials ADD COLUMN IF NOT EXISTS masked_token VARCHAR(128) NOT NULL DEFAULT '****';
ALTER TABLE ai_credentials ADD COLUMN IF NOT EXISTS token_fingerprint VARCHAR(128);

UPDATE ai_credentials
SET masked_token = '****'
WHERE masked_token IS NULL OR masked_token = '';
