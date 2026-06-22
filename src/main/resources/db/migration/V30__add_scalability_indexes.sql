CREATE INDEX IF NOT EXISTS idx_process_variable_instance_name
    ON process_variable(process_instance_id, name);

CREATE INDEX IF NOT EXISTS idx_task_status
    ON task(status);

CREATE INDEX IF NOT EXISTS idx_task_assignee_status
    ON task(assignee, status);

CREATE INDEX IF NOT EXISTS idx_task_process_node_status
    ON task(process_instance_id, node_id, status);

CREATE INDEX IF NOT EXISTS idx_worker_request_status_last_attempt
    ON worker_request(status, last_attempt_at);
