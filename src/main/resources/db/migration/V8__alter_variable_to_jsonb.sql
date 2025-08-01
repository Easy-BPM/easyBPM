-- Altere o tipo da coluna `value` para JSONB
ALTER TABLE process_variable
ALTER COLUMN value TYPE JSONB
USING value::jsonb;

-- Altere o tipo da coluna `value` para JSONB
ALTER TABLE task_variable
ALTER COLUMN value TYPE JSONB
USING value::jsonb;