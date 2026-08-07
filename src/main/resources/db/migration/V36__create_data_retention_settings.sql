CREATE TABLE IF NOT EXISTS data_retention_settings (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    completed_process_retention_days BIGINT NOT NULL DEFAULT 90,
    completed_task_retention_days BIGINT NOT NULL DEFAULT 90,
    batch_size INT NOT NULL DEFAULT 500,
    cron VARCHAR(120) NOT NULL DEFAULT '0 0 3 * * *',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
