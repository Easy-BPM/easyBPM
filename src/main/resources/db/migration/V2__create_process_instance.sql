CREATE TABLE IF NOT EXISTS process_instance (
    id SERIAL PRIMARY KEY,
    process_definition_id BIGINT NOT NULL REFERENCES process_definition(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    current_node VARCHAR(255),
    context JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);