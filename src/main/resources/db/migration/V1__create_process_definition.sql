CREATE TABLE IF NOT EXISTS process_definition (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    definition_json JSONB NOT NULL
);