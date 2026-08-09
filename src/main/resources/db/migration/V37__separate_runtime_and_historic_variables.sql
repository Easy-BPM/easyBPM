ALTER TABLE task_variable
    ADD COLUMN IF NOT EXISTS process_instance_id BIGINT;

UPDATE task_variable tv
SET process_instance_id = t.process_instance_id
FROM task t
WHERE tv.task_id = t.id
  AND tv.process_instance_id IS NULL;

ALTER TABLE task_variable
    ALTER COLUMN process_instance_id SET NOT NULL;

CREATE TABLE IF NOT EXISTS historic_process_variable (
    id BIGINT PRIMARY KEY,
    process_instance_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    value JSONB
);

CREATE TABLE IF NOT EXISTS historic_task_variable (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    process_instance_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    value JSONB
);

CREATE INDEX IF NOT EXISTS idx_process_variable_process_instance_id
    ON process_variable(process_instance_id);

CREATE INDEX IF NOT EXISTS idx_historic_process_variable_process_instance_id
    ON historic_process_variable(process_instance_id);

CREATE INDEX IF NOT EXISTS idx_task_variable_task_id
    ON task_variable(task_id);

CREATE INDEX IF NOT EXISTS idx_task_variable_process_instance_id
    ON task_variable(process_instance_id);

CREATE INDEX IF NOT EXISTS idx_historic_task_variable_task_id
    ON historic_task_variable(task_id);

CREATE INDEX IF NOT EXISTS idx_historic_task_variable_process_instance_id
    ON historic_task_variable(process_instance_id);
