-- Rename process_key -> process_id and add process_name
ALTER TABLE process_definition
RENAME COLUMN IF EXISTS process_key TO process_id;

-- Ensure process_id exists (fallback from legacy name)
ALTER TABLE process_definition
ADD COLUMN IF NOT EXISTS process_id VARCHAR(255);

UPDATE process_definition
SET process_id = COALESCE(process_id, name)
WHERE process_id IS NULL OR process_id = '';

ALTER TABLE process_definition
ALTER COLUMN process_id SET NOT NULL;

-- Add human-friendly title column
ALTER TABLE process_definition
ADD COLUMN IF NOT EXISTS process_name VARCHAR(255);

-- If legacy `name` column exists, copy it to process_name and then drop it
UPDATE process_definition
SET process_name = COALESCE(process_name, name)
WHERE process_name IS NULL OR process_name = '';

-- Cleanup: drop legacy `name` column if present
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_definition' AND column_name='name'
    ) THEN
        ALTER TABLE process_definition DROP COLUMN name;
    END IF;
END$$;

-- Create an index on process_id for fast lookups
CREATE INDEX IF NOT EXISTS idx_process_definition_process_id
ON process_definition(process_id);
