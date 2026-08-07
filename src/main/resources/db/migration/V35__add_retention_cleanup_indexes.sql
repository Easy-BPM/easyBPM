CREATE INDEX IF NOT EXISTS idx_process_instance_status_updated
    ON process_instance(status, updated_at, id);

CREATE INDEX IF NOT EXISTS idx_process_instance_definition_status_updated
    ON process_instance(process_definition_id, status, updated_at, id);

CREATE INDEX IF NOT EXISTS idx_task_process_instance
    ON task(process_instance_id);

CREATE INDEX IF NOT EXISTS idx_task_status_completed_at
    ON task(status, completed_at, id);

CREATE INDEX IF NOT EXISTS idx_task_variable_task
    ON task_variable(task_id);

CREATE INDEX IF NOT EXISTS idx_process_variable_instance
    ON process_variable(process_instance_id);
