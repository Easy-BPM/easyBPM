-- ============================================================================
-- Consolidated H2 Database Schema
-- Generated from all 21 Flyway migration files (V1 through V21)
-- PostgreSQL → H2 conversions applied
-- ============================================================================
-- Conversion rules applied:
-- - SERIAL → BIGINT GENERATED ALWAYS AS IDENTITY
-- - BIGSERIAL → BIGINT GENERATED ALWAYS AS IDENTITY
-- - JSONB → CLOB
-- - BYTEA → BLOB
-- - Removed PostgreSQL-specific PL/pgSQL blocks (DO $$ ... END$$)
-- - Removed IF EXISTS/IF NOT EXISTS for cleaner initial schema
-- - ::jsonb casts and USING clauses removed
-- ============================================================================

-- ============================================================================
-- V1: Core Process Definition Table
-- ============================================================================

CREATE TABLE process_definition (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    definition_json CLOB NOT NULL
);

-- ============================================================================
-- V2: Process Instance Table
-- ============================================================================

CREATE TABLE process_instance (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    process_definition_id BIGINT NOT NULL REFERENCES process_definition(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    current_node VARCHAR(255),
    context CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- V3: Add Version Column to process_definition
-- ============================================================================

ALTER TABLE process_definition
ADD COLUMN version BIGINT DEFAULT 1;

-- ============================================================================
-- V4: Convert current_node to current_nodes (JSON array)
-- ============================================================================

ALTER TABLE process_instance
DROP COLUMN IF EXISTS current_node;

ALTER TABLE process_instance
DROP COLUMN IF EXISTS context;

ALTER TABLE process_instance
ADD COLUMN current_nodes CLOB;

-- ============================================================================
-- V5: Task Table
-- ============================================================================

CREATE TABLE task (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    process_instance_id BIGINT NOT NULL REFERENCES process_instance(id),
    node_id VARCHAR(255) NOT NULL,
    assignee VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

-- ============================================================================
-- V6: Process Variable Table
-- ============================================================================

CREATE TABLE process_variable (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    process_instance_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    value CLOB
);

-- ============================================================================
-- V7: Task Variable Table
-- ============================================================================

CREATE TABLE task_variable (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id BIGINT NOT NULL,
    process_instance_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    value CLOB
);

-- ============================================================================
-- V8: Convert variable values to JSONB (already CLOB in H2 schema)
-- (No action needed - already defined as CLOB above)
-- ============================================================================

-- ============================================================================
-- V9: Form Table and task form_id association
-- ============================================================================

CREATE TABLE form (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version INT NOT NULL,
    schema CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE task
ADD COLUMN form_id BIGINT;

-- ============================================================================
-- V10: Form schema rename (already named 'schema' in V9)
-- (No action needed)
-- ============================================================================

-- ============================================================================
-- V11: Add task title column
-- ============================================================================

ALTER TABLE task
ADD COLUMN title VARCHAR(255);

-- ============================================================================
-- V12: Message Subscription Table
-- ============================================================================

CREATE TABLE message_subscription (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    process_instance_id BIGINT NOT NULL REFERENCES process_instance(id) ON DELETE CASCADE,
    node_id VARCHAR(255) NOT NULL,
    message_name VARCHAR(255) NOT NULL,
    correlation_key VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AWAITING',
    message_payload CLOB,
    timeout_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_at TIMESTAMP,
    UNIQUE(process_instance_id, node_id)
);

CREATE INDEX idx_message_subscription_lookup ON message_subscription(message_name, correlation_key, status);
CREATE INDEX idx_message_subscription_timeout ON message_subscription(timeout_at);

-- ============================================================================
-- V31: External Message Inbox
-- ============================================================================

CREATE TABLE message_event_inbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id VARCHAR(255) NOT NULL,
    message_name VARCHAR(255) NOT NULL,
    correlation_key VARCHAR(255) NOT NULL,
    payload CLOB,
    status VARCHAR(50) NOT NULL DEFAULT 'RECEIVED',
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    CONSTRAINT uk_message_event_inbox_message_id UNIQUE (message_id)
);

CREATE INDEX idx_message_event_inbox_status_created ON message_event_inbox(status, created_at);
CREATE INDEX idx_message_event_inbox_correlation ON message_event_inbox(message_name, correlation_key, status);

-- ============================================================================
-- V13: Node history tracking
-- ============================================================================

ALTER TABLE process_instance
ADD COLUMN node_history CLOB DEFAULT '[]';

-- ============================================================================
-- V14: Worker Request Table (for async execution and retry tracking)
-- ============================================================================

CREATE TABLE worker_request (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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

-- ============================================================================
-- V15: Add process metadata (process_key and description)
-- ============================================================================

ALTER TABLE process_definition
ADD COLUMN process_key VARCHAR(255);

ALTER TABLE process_definition
ADD COLUMN description TEXT;

-- Note: V15 included UPDATE logic to populate process_key from name.
-- This is handled at application startup via Liquibase/application code.

CREATE INDEX idx_process_definition_process_key ON process_definition(process_key);

-- ============================================================================
-- V16: Add form_key column to form table
-- ============================================================================

ALTER TABLE form
ADD COLUMN form_key VARCHAR(255);

-- Note: V16 included complex UPDATE with regex/MD5 logic to generate form_key.
-- This is handled at application startup via application code.

CREATE UNIQUE INDEX uk_form_form_key_version ON form(form_key, version);

-- ============================================================================
-- V17: Rename process_key to process_id and add process_name
-- ============================================================================

ALTER TABLE process_definition
ADD COLUMN process_id VARCHAR(255);

ALTER TABLE process_definition
ADD COLUMN process_name VARCHAR(255);

-- Note: V17 included complex migration logic with information_schema checks.
-- Assumed process_id is already populated; process_name is added for future use.

CREATE INDEX idx_process_definition_process_id ON process_definition(process_id);

-- ============================================================================
-- V18: Rename form_key to form_id
-- ============================================================================

ALTER TABLE form
RENAME COLUMN form_key TO form_id;

-- Rename constraint index (H2 doesn't support direct index rename)
-- Drop and recreate
DROP INDEX IF EXISTS uk_form_form_key_version;
CREATE UNIQUE INDEX uk_form_form_id_version ON form(form_id, version);

-- ============================================================================
-- V19: Call Activity & Subprocess Support
-- ============================================================================

ALTER TABLE process_instance
ADD COLUMN parent_instance_id BIGINT REFERENCES process_instance(id) ON DELETE CASCADE;

ALTER TABLE process_instance
ADD COLUMN call_activity_node_id VARCHAR(255);

ALTER TABLE process_instance
ADD COLUMN nesting_level INT DEFAULT 0;

ALTER TABLE process_instance
ADD COLUMN completion_node_id VARCHAR(255);

-- Indexes for parent-child relationships
CREATE INDEX idx_process_instance_parent_id ON process_instance(parent_instance_id);
CREATE INDEX idx_process_instance_call_activity_node ON process_instance(call_activity_node_id);
CREATE INDEX idx_process_instance_nesting_level ON process_instance(nesting_level);
CREATE INDEX idx_process_instance_status_updated ON process_instance(status, updated_at, id);
CREATE INDEX idx_process_instance_definition_status_updated ON process_instance(process_definition_id, status, updated_at, id);

-- Call Activity Mapping Table
CREATE TABLE call_activity_mapping (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_instance_id BIGINT NOT NULL
        REFERENCES process_instance(id) ON DELETE CASCADE,
    child_instance_id BIGINT NOT NULL
        REFERENCES process_instance(id) ON DELETE CASCADE,
    call_activity_node_id VARCHAR(255) NOT NULL,
    input_mappings CLOB DEFAULT '{}',
    output_mappings CLOB DEFAULT '{}',
    propagate_all_variables BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_parent_instance FOREIGN KEY (parent_instance_id)
        REFERENCES process_instance(id) ON DELETE CASCADE,
    CONSTRAINT fk_child_instance FOREIGN KEY (child_instance_id)
        REFERENCES process_instance(id) ON DELETE CASCADE,
    CONSTRAINT unique_call_activity_mapping UNIQUE (parent_instance_id, child_instance_id, call_activity_node_id)
);

CREATE INDEX idx_call_activity_mapping_parent_id ON call_activity_mapping(parent_instance_id);
CREATE INDEX idx_call_activity_mapping_child_id ON call_activity_mapping(child_instance_id);
CREATE INDEX idx_call_activity_mapping_call_activity_node ON call_activity_mapping(call_activity_node_id);

-- ============================================================================
-- V20: Code Task Support (JAR storage, class metadata, execution audit)
-- ============================================================================

-- Code Task JAR Table (stores uploaded JAR files)
CREATE TABLE code_task_jar (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content BLOB NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(255),
    description TEXT,
    CONSTRAINT jar_file_hash_unique UNIQUE (file_hash)
);

CREATE INDEX idx_code_task_jar_file_hash ON code_task_jar(file_hash);
CREATE INDEX idx_code_task_jar_upload_date ON code_task_jar(upload_date);

-- Code Class Metadata Table (discovered classes and methods from JARs)
CREATE TABLE code_class_metadata (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    jar_id BIGINT NOT NULL REFERENCES code_task_jar(id) ON DELETE CASCADE,
    class_name VARCHAR(500) NOT NULL,
    method_name VARCHAR(255) NOT NULL,
    method_signature TEXT,
    input_params CLOB,
    return_type VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_code_class_metadata UNIQUE (jar_id, class_name, method_name)
);

CREATE INDEX idx_code_class_jar_id ON code_class_metadata(jar_id);
CREATE INDEX idx_code_class_name ON code_class_metadata(class_name);
CREATE INDEX idx_code_class_method_name ON code_class_metadata(method_name);

-- Code Task Execution Audit Table (execution history and audit trail)
CREATE TABLE code_task_execution (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    node_id VARCHAR(255),
    jar_id BIGINT REFERENCES code_task_jar(id),
    class_name VARCHAR(500),
    method_name VARCHAR(255),
    input_variables CLOB,
    output_variables CLOB,
    execution_time_ms INTEGER,
    status VARCHAR(50),
    error_message TEXT,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_code_exec_jar FOREIGN KEY (jar_id) REFERENCES code_task_jar(id) ON DELETE SET NULL
);

CREATE INDEX idx_code_execution_instance_id ON code_task_execution(instance_id);
CREATE INDEX idx_code_execution_jar_id ON code_task_execution(jar_id);
CREATE INDEX idx_code_execution_status ON code_task_execution(status);
CREATE INDEX idx_code_execution_executed_at ON code_task_execution(executed_at);

-- ============================================================================
-- V21: Audit Columns on process_variable Table
-- ============================================================================

ALTER TABLE process_variable
ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE process_variable
ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE historic_process_variable (
    id BIGINT PRIMARY KEY,
    process_instance_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    value CLOB
);

CREATE TABLE historic_task_variable (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    process_instance_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    value CLOB
);

-- ============================================================================
-- V30: Incident Management
-- ============================================================================

CREATE TABLE incident (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    process_instance_id BIGINT NOT NULL,
    node_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    severity VARCHAR(50) NOT NULL DEFAULT 'HIGH',
    source VARCHAR(50) NOT NULL DEFAULT 'PROCESS_ENGINE',
    message CLOB NOT NULL,
    technical_details CLOB,
    external_reference_id VARCHAR(255),
    occurrence_count INT NOT NULL DEFAULT 1,
    last_occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(255),
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(255),
    resolution_note CLOB,
    resolution_action VARCHAR(50)
);

CREATE INDEX idx_incident_status ON incident(status);
CREATE INDEX idx_incident_source ON incident(source);
CREATE INDEX idx_incident_process_instance ON incident(process_instance_id);
CREATE INDEX idx_incident_created_at ON incident(created_at);

CREATE TABLE incident_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    message CLOB NOT NULL,
    actor VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_incident_event_incident ON incident_event(incident_id);
CREATE INDEX idx_incident_event_created_at ON incident_event(created_at);

-- ============================================================================
-- V31: Process Instance Timeline Events
-- ============================================================================

CREATE TABLE process_instance_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    process_instance_id BIGINT NOT NULL,
    node_id VARCHAR(255),
    event_type VARCHAR(80) NOT NULL,
    message CLOB NOT NULL,
    actor VARCHAR(255),
    details CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_process_instance_event_instance ON process_instance_event(process_instance_id);
CREATE INDEX idx_process_instance_event_node ON process_instance_event(node_id);
CREATE INDEX idx_process_instance_event_type ON process_instance_event(event_type);
CREATE INDEX idx_process_instance_event_created_at ON process_instance_event(created_at);

-- ============================================================================
-- V34: Agentic Orchestration Tables
-- ============================================================================

CREATE TABLE agent_process_definition (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    process_key VARCHAR(255) NOT NULL,
    process_name VARCHAR(255),
    description CLOB,
    definition_json CLOB NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_process_definition_key_version UNIQUE (process_key, version)
);

CREATE INDEX idx_agent_process_definition_key ON agent_process_definition(process_key);
CREATE INDEX idx_agent_process_definition_key_version ON agent_process_definition(process_key, version);

CREATE TABLE agent_process_execution (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_process_definition_id BIGINT NOT NULL REFERENCES agent_process_definition(id),
    process_instance_id BIGINT NOT NULL REFERENCES process_instance(id) ON DELETE CASCADE,
    node_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    input_payload CLOB,
    decision_trace CLOB,
    output_payload CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_agent_process_execution_definition ON agent_process_execution(agent_process_definition_id);
CREATE INDEX idx_agent_process_execution_instance ON agent_process_execution(process_instance_id);
CREATE INDEX idx_agent_process_execution_node ON agent_process_execution(node_id);
CREATE INDEX idx_agent_process_execution_status ON agent_process_execution(status);

CREATE INDEX idx_task_process_instance ON task(process_instance_id);
CREATE INDEX idx_task_status_completed_at ON task(status, completed_at, id);
CREATE INDEX idx_task_variable_task ON task_variable(task_id);
CREATE INDEX idx_process_variable_instance ON process_variable(process_instance_id);
CREATE INDEX idx_task_variable_process_instance ON task_variable(process_instance_id);
CREATE INDEX idx_historic_process_variable_instance ON historic_process_variable(process_instance_id);
CREATE INDEX idx_historic_task_variable_task ON historic_task_variable(task_id);
CREATE INDEX idx_historic_task_variable_process_instance ON historic_task_variable(process_instance_id);

CREATE TABLE data_retention_settings (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    completed_process_retention_days BIGINT NOT NULL DEFAULT 90,
    completed_task_retention_days BIGINT NOT NULL DEFAULT 90,
    batch_size INT NOT NULL DEFAULT 500,
    cron VARCHAR(120) NOT NULL DEFAULT '0 0 3 * * *',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- END OF SCHEMA
-- ============================================================================
-- Total Tables: 13
-- - process_definition: Core process definitions
-- - process_instance: Running process instances
-- - task: Human tasks
-- - process_variable: Process-level variables
-- - task_variable: Task-level variables
-- - form: Dynamic form definitions
-- - message_subscription: Message event subscriptions
-- - worker_request: Async task retry tracking
-- - call_activity_mapping: Subprocess variable mappings
-- - code_task_jar: Uploaded JAR files for code tasks
-- - code_class_metadata: Discovered classes/methods from JARs
-- - code_task_execution: Code task execution audit trail
-- - (implicit: flyway_schema_history: Flyway migration tracking)
--
-- Total Indexes: 30+ (for query optimization and uniqueness)
-- All Foreign Key constraints preserved with CASCADE on DELETE where appropriate
-- ============================================================================
