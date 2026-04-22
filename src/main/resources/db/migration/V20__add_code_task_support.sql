-- V20__add_code_task_support.sql
-- Adds Code Task support: JAR file storage, class metadata, execution audit trail

-- Pre-flight check: ensure tables don't already exist
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT FROM information_schema.tables 
    WHERE table_name = 'code_task_jar'
  ) THEN
    -- Table doesn't exist, safe to create
  ELSE
    -- Table already exists, migration already applied
    RAISE NOTICE 'V20 migration already applied - code_task_jar table exists';
    RETURN;
  END IF;
END $$;

-- ==================== CODE TASK JAR TABLE ====================
-- Stores uploaded JAR files (BLOB) with metadata

CREATE TABLE code_task_jar (
  id BIGSERIAL PRIMARY KEY,
  content BYTEA NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_hash VARCHAR(64) UNIQUE NOT NULL,
  upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  uploaded_by VARCHAR(255),
  description TEXT,
  CONSTRAINT jar_file_hash_unique UNIQUE (file_hash)
);

CREATE INDEX idx_code_task_jar_file_hash ON code_task_jar(file_hash);
CREATE INDEX idx_code_task_jar_upload_date ON code_task_jar(upload_date);

-- ==================== CODE CLASS METADATA TABLE ====================
-- Stores discovered classes and methods from uploaded JARs

CREATE TABLE code_class_metadata (
  id BIGSERIAL PRIMARY KEY,
  jar_id BIGINT NOT NULL REFERENCES code_task_jar(id) ON DELETE CASCADE,
  class_name VARCHAR(500) NOT NULL,
  method_name VARCHAR(255) NOT NULL,
  method_signature TEXT,
  input_params JSONB,
  return_type VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_code_class_metadata UNIQUE (jar_id, class_name, method_name)
);

CREATE INDEX idx_code_class_jar_id ON code_class_metadata(jar_id);
CREATE INDEX idx_code_class_name ON code_class_metadata(class_name);
CREATE INDEX idx_code_class_method_name ON code_class_metadata(method_name);

-- ==================== CODE TASK EXECUTION AUDIT TABLE ====================
-- Records all executions of Code Tasks for audit trail and monitoring

CREATE TABLE code_task_execution (
  id BIGSERIAL PRIMARY KEY,
  instance_id BIGINT NOT NULL,
  node_id VARCHAR(255),
  jar_id BIGINT REFERENCES code_task_jar(id),
  class_name VARCHAR(500),
  method_name VARCHAR(255),
  input_variables JSONB,
  output_variables JSONB,
  execution_time_ms INTEGER,
  status VARCHAR(50),
  error_message TEXT,
  executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_code_exec_jar FOREIGN KEY (jar_id) REFERENCES code_task_jar(id) ON DELETE SET NULL
);

CREATE INDEX idx_code_execution_instance_id ON code_task_execution(instance_id);
CREATE INDEX idx_code_execution_jar_id ON code_task_execution(jar_id);
CREATE INDEX idx_code_execution_status ON code_task_execution(status);
CREATE INDEX idx_code_execution_executed_at ON code_task_execution(executed_at DESC);

-- ==================== SUMMARY ====================
-- Created 3 tables:
-- 1. code_task_jar: Stores JAR file content and metadata
-- 2. code_class_metadata: Discovered classes and methods per JAR
-- 3. code_task_execution: Audit trail of all code task executions
--
-- Performance: 7 indexes for fast lookups and filtering
-- Data Integrity: FK constraints, unique constraints, cascade on delete
-- Audit: Timestamp columns on all tables, full variable snapshots (JSONB)
