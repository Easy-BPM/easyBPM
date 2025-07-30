CREATE TABLE task(
    id BIGSERIAL PRIMARY KEY,
    process_instance_id BIGINT NOT NULL REFERENCES process_instance(id),
    node_id VARCHAR(255) NOT NULL,
    assignee VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);
