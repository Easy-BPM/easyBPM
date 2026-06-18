ALTER TABLE process_instance
    ADD COLUMN IF NOT EXISTS error_message TEXT;

ALTER TABLE process_instance
    ADD COLUMN IF NOT EXISTS error_node_id VARCHAR(255);
