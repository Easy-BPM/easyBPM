-- Rename process_key -> process_id and add process_name
DO $$
BEGIN
    -- Rename column if old column exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_definition' AND column_name='process_key'
    ) THEN
        EXECUTE 'ALTER TABLE process_definition RENAME COLUMN process_key TO process_id';
    END IF;

    -- Add process_id if it does not exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_definition' AND column_name='process_id'
    ) THEN
        EXECUTE 'ALTER TABLE process_definition ADD COLUMN process_id VARCHAR(255)';
    END IF;

    -- Migrate values from legacy `name` if needed
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_definition' AND column_name='name'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_definition' AND column_name='process_id'
    ) THEN
        EXECUTE $sql$
            UPDATE process_definition
            SET process_id = COALESCE(process_id, name)
            WHERE process_id IS NULL OR process_id = ''
        $sql$;
    END IF;

    -- Make process_id NOT NULL if present
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_definition' AND column_name='process_id'
    ) THEN
        EXECUTE 'ALTER TABLE process_definition ALTER COLUMN process_id SET NOT NULL';
    END IF;

    -- Add human-friendly title column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_definition' AND column_name='process_name'
    ) THEN
        EXECUTE 'ALTER TABLE process_definition ADD COLUMN process_name VARCHAR(255)';
    END IF;

    -- Copy legacy `name` into `process_name` if available
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_definition' AND column_name='name'
    ) THEN
        EXECUTE $sql$
            UPDATE process_definition
            SET process_name = COALESCE(process_name, name)
            WHERE process_name IS NULL OR process_name = ''
        $sql$;
    END IF;

    -- Drop legacy `name` column if present
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_definition' AND column_name='name'
    ) THEN
        EXECUTE 'ALTER TABLE process_definition DROP COLUMN name';
    END IF;
END$$;

-- Create an index on process_id for fast lookups
CREATE INDEX IF NOT EXISTS idx_process_definition_process_id
ON process_definition(process_id);
