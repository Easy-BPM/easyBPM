-- Create table form
CREATE TABLE form (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version INT NOT NULL,
    definition JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Alter Task to deal with form_id
ALTER TABLE task
ADD COLUMN form_id BIGINT;