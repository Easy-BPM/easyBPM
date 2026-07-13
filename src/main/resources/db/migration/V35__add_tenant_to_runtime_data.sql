ALTER TABLE process_definition ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE process_instance ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE process_variable ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE task ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE task_variable ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE form ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE documents ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE incident ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE incident_event ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE message_subscription ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE message_event_inbox ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE worker_request ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE call_activity_mapping ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';
ALTER TABLE process_instance_event ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';

CREATE INDEX ix_process_definition_tenant_key_version ON process_definition (tenant_id, process_id, version DESC);
CREATE INDEX ix_process_instance_tenant_status_updated ON process_instance (tenant_id, status, updated_at);
CREATE INDEX ix_process_variable_tenant_instance ON process_variable (tenant_id, process_instance_id);
CREATE INDEX ix_task_tenant_status_assignee ON task (tenant_id, status, assignee);
CREATE INDEX ix_task_variable_tenant_task ON task_variable (tenant_id, task_id);
CREATE INDEX ix_form_tenant_form_version ON form (tenant_id, form_id, version DESC);
CREATE INDEX ix_documents_tenant_process_task ON documents (tenant_id, process_instance_id, task_id);
CREATE INDEX ix_incident_tenant_status_created ON incident (tenant_id, status, created_at DESC);
CREATE INDEX ix_message_subscription_tenant_status_timeout ON message_subscription (tenant_id, status, timeout_at);
CREATE INDEX ix_worker_request_tenant_status_attempt ON worker_request (tenant_id, status, last_attempt_at);

ALTER TABLE worker_request DROP CONSTRAINT IF EXISTS worker_request_idempotency_key_key;
ALTER TABLE worker_request DROP CONSTRAINT IF EXISTS uk_worker_request_idempotency_key;
CREATE UNIQUE INDEX ux_worker_request_tenant_idempotency_key ON worker_request (tenant_id, idempotency_key);
