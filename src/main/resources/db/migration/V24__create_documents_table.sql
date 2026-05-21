-- V24__create_documents_table.sql
-- Adds document storage for form-based file upload/download/preview capabilities

CREATE TABLE IF NOT EXISTS documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name    VARCHAR(255)  NOT NULL,
    content_type VARCHAR(255)  NOT NULL,
    file_size    BIGINT        NOT NULL,
    content      BYTEA         NOT NULL,
    task_id      BIGINT        REFERENCES task(id) ON DELETE SET NULL,
    process_instance_id BIGINT REFERENCES process_instance(id) ON DELETE SET NULL,
    form_field_key VARCHAR(255),
    uploaded_by  VARCHAR(255),
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_documents_task_id         ON documents(task_id);
CREATE INDEX IF NOT EXISTS idx_documents_instance_id     ON documents(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_documents_uploaded_by     ON documents(uploaded_by);
CREATE INDEX IF NOT EXISTS idx_documents_created_at      ON documents(created_at DESC);
