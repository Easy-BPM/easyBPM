-- V14: Create worker_request table for tracking retries and idempotency
CREATE TABLE worker_request (
    id BIGSERIAL PRIMARY KEY,
    process_instance_id BIGINT NOT NULL,
    node_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    retry_count INT DEFAULT 0,
    status VARCHAR(50) DEFAULT 'PENDING',
    last_error VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_worker_request_idempotency_key ON worker_request(idempotency_key);
CREATE INDEX idx_worker_request_process_node ON worker_request(process_instance_id, node_id);
CREATE INDEX idx_worker_request_status ON worker_request(status);
