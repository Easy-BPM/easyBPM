ALTER TABLE process_definition
ADD COLUMN IF NOT EXISTS process_key VARCHAR(255),
ADD COLUMN IF NOT EXISTS description TEXT;

UPDATE process_definition
SET process_key = name
WHERE process_key IS NULL OR process_key = '';

ALTER TABLE process_definition
ALTER COLUMN process_key SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_process_definition_process_key
ON process_definition(process_key);
