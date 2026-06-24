CREATE TABLE IF NOT EXISTS agent_process_definition (
    id BIGSERIAL PRIMARY KEY,
    process_key VARCHAR(255) NOT NULL,
    process_name VARCHAR(255),
    description TEXT,
    definition_json JSONB NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_process_definition_key_version UNIQUE (process_key, version)
);

CREATE INDEX IF NOT EXISTS idx_agent_process_definition_key ON agent_process_definition(process_key);
CREATE INDEX IF NOT EXISTS idx_agent_process_definition_key_version ON agent_process_definition(process_key, version DESC);

CREATE TABLE IF NOT EXISTS agent_process_execution (
    id BIGSERIAL PRIMARY KEY,
    agent_process_definition_id BIGINT NOT NULL REFERENCES agent_process_definition(id),
    process_instance_id BIGINT NOT NULL REFERENCES process_instance(id) ON DELETE CASCADE,
    node_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    input_payload JSONB,
    decision_trace JSONB,
    output_payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_process_execution_definition ON agent_process_execution(agent_process_definition_id);
CREATE INDEX IF NOT EXISTS idx_agent_process_execution_instance ON agent_process_execution(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_agent_process_execution_node ON agent_process_execution(node_id);
CREATE INDEX IF NOT EXISTS idx_agent_process_execution_status ON agent_process_execution(status);
