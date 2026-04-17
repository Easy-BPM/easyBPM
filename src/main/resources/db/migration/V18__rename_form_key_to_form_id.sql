DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='form' AND column_name='form_key'
    ) THEN
        EXECUTE 'ALTER TABLE form RENAME COLUMN form_key TO form_id';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename='form' AND indexname='uk_form_form_key_version'
    ) THEN
        EXECUTE 'ALTER INDEX uk_form_form_key_version RENAME TO uk_form_form_id_version';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename='form' AND indexname='uk_form_form_id_version'
    ) THEN
        EXECUTE 'CREATE UNIQUE INDEX uk_form_form_id_version ON form(form_id, version)';
    END IF;
END$$;