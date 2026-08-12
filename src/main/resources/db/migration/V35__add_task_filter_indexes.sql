CREATE INDEX IF NOT EXISTS idx_task_process_instance_id
    ON task(process_instance_id);

CREATE INDEX IF NOT EXISTS idx_task_title
    ON task(title);

CREATE INDEX IF NOT EXISTS idx_task_created_at
    ON task(created_at);

CREATE INDEX IF NOT EXISTS idx_task_candidate_user_username
    ON task_candidate_user(username, task_id);

CREATE INDEX IF NOT EXISTS idx_task_candidate_group_code
    ON task_candidate_group(group_code, task_id);

CREATE INDEX IF NOT EXISTS idx_task_variable_task_name
    ON task_variable(task_id, name);

CREATE INDEX IF NOT EXISTS idx_task_variable_name
    ON task_variable(name);
