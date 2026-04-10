CREATE TABLE message_subscription (
    id BIGSERIAL PRIMARY KEY,
    process_instance_id BIGINT NOT NULL REFERENCES process_instance(id) ON DELETE CASCADE,
    node_id VARCHAR(255) NOT NULL,
    message_name VARCHAR(255) NOT NULL,
    correlation_key VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AWAITING',
    message_payload JSONB,
    timeout_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    received_at TIMESTAMP,
    UNIQUE(process_instance_id, node_id)
);

-- Index for fast lookup when message arrives
CREATE INDEX idx_message_subscription_lookup ON message_subscription(message_name, correlation_key, status);

-- Index for timeout processing
CREATE INDEX idx_message_subscription_timeout ON message_subscription(timeout_at) WHERE status = 'AWAITING';
