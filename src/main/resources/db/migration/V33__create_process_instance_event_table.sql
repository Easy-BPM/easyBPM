CREATE TABLE IF NOT EXISTS process_instance_event (
    id BIGSERIAL PRIMARY KEY,
    process_instance_id BIGINT NOT NULL,
    node_id VARCHAR(255),
    event_type VARCHAR(80) NOT NULL,
    message TEXT NOT NULL,
    actor VARCHAR(255),
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_process_instance_event_instance ON process_instance_event(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_process_instance_event_node ON process_instance_event(node_id);
CREATE INDEX IF NOT EXISTS idx_process_instance_event_type ON process_instance_event(event_type);
CREATE INDEX IF NOT EXISTS idx_process_instance_event_created_at ON process_instance_event(created_at DESC);
