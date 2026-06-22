CREATE TABLE IF NOT EXISTS message_event_inbox (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(255) NOT NULL,
    message_name VARCHAR(255) NOT NULL,
    correlation_key VARCHAR(255) NOT NULL,
    payload JSONB,
    status VARCHAR(50) NOT NULL DEFAULT 'RECEIVED',
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    CONSTRAINT uk_message_event_inbox_message_id UNIQUE (message_id)
);

CREATE INDEX IF NOT EXISTS idx_message_event_inbox_status_created
    ON message_event_inbox(status, created_at);

CREATE INDEX IF NOT EXISTS idx_message_event_inbox_correlation
    ON message_event_inbox(message_name, correlation_key, status);
