ALTER TABLE process_definition
    ALTER COLUMN definition_json TYPE TEXT USING definition_json::TEXT;
