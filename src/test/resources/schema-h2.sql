CREATE TABLE "message_subscription" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "process_instance_id" BIGINT NOT NULL,
    "node_id" VARCHAR(255) NOT NULL,
    "message_name" VARCHAR(255) NOT NULL,
    "correlation_key" VARCHAR(255) NOT NULL,
    "status" VARCHAR(50) NOT NULL DEFAULT 'AWAITING',
    "message_payload" CLOB,
    "timeout_at" TIMESTAMP,
    "created_at" TIMESTAMP,
    "received_at" TIMESTAMP
);

CREATE UNIQUE INDEX "idx_message_subscription_unique" ON "message_subscription"("process_instance_id", "node_id");
CREATE INDEX "idx_message_subscription_lookup" ON "message_subscription"("message_name", "correlation_key", "status");
CREATE INDEX "idx_message_subscription_timeout" ON "message_subscription"("timeout_at");
CREATE TABLE "process_definition" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "process_id" VARCHAR(255) NOT NULL,
    "process_name" VARCHAR(255),
    "description" CLOB,
    "version" INTEGER NOT NULL,
    "definition_json" CLOB NOT NULL
);

CREATE TABLE "process_instance" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "process_definition_id" BIGINT,
    "status" INTEGER,
    "current_nodes" CLOB,
    "node_history" CLOB,
    "created_at" TIMESTAMP,
    "updated_at" TIMESTAMP,
    "parent_instance_id" BIGINT REFERENCES "process_instance"("id"),
    "call_activity_node_id" VARCHAR(255),
    "nesting_level" INT DEFAULT 0,
    "completion_node_id" VARCHAR(255)
);

CREATE TABLE "process_variable" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "process_instance_id" BIGINT,
    "name" VARCHAR(255) NOT NULL,
    "value" CLOB,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "form" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "form_id" VARCHAR(255) NOT NULL,
    "name" VARCHAR(255) NOT NULL,
    "schema" CLOB NOT NULL,
    "version" INTEGER NOT NULL,
    "created_at" TIMESTAMP
);

CREATE UNIQUE INDEX "uk_form_form_id_version" ON "form"("form_id", "version");

CREATE TABLE "task" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "process_instance_id" BIGINT NOT NULL,
    "title" VARCHAR(255),
    "node_id" VARCHAR(255) NOT NULL,
    "assignee" VARCHAR(255),
    "status" VARCHAR(255),
    "created_at" TIMESTAMP,
    "completed_at" TIMESTAMP,
    "form_id" BIGINT
);

CREATE TABLE "task_variable" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "task_id" BIGINT NOT NULL,
    "name" VARCHAR(255) NOT NULL,
    "value" CLOB
);

CREATE TABLE "worker_request" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "process_instance_id" BIGINT NOT NULL,
    "node_id" VARCHAR(255) NOT NULL,
    "idempotency_key" VARCHAR(255) NOT NULL,
    "retry_count" INTEGER DEFAULT 0,
    "status" VARCHAR(50) DEFAULT 'PENDING',
    "last_error" VARCHAR(1000),
    "created_at" TIMESTAMP,
    "last_attempt_at" TIMESTAMP,
    "completed_at" TIMESTAMP
);

CREATE UNIQUE INDEX "idx_worker_request_idempotency_key" ON "worker_request"("idempotency_key");
CREATE INDEX "idx_worker_request_process_node" ON "worker_request"("process_instance_id", "node_id");
CREATE INDEX "idx_worker_request_status" ON "worker_request"("status");

-- V19: Call Activity Support - mapping table
CREATE TABLE "call_activity_mapping" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "parent_instance_id" BIGINT NOT NULL,
    "child_instance_id" BIGINT NOT NULL,
    "call_activity_node_id" VARCHAR(255) NOT NULL,
    "input_mappings" CLOB DEFAULT '{}',
    "output_mappings" CLOB DEFAULT '{}',
    "propagate_all_variables" BOOLEAN DEFAULT FALSE,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "fk_call_activity_parent" FOREIGN KEY ("parent_instance_id") REFERENCES "process_instance"("id"),
    CONSTRAINT "fk_call_activity_child" FOREIGN KEY ("child_instance_id") REFERENCES "process_instance"("id"),
    CONSTRAINT "uk_call_activity_mapping" UNIQUE ("parent_instance_id", "child_instance_id", "call_activity_node_id")
);

CREATE INDEX "idx_call_activity_mapping_parent_id" ON "call_activity_mapping"("parent_instance_id");
CREATE INDEX "idx_call_activity_mapping_child_id" ON "call_activity_mapping"("child_instance_id");
CREATE INDEX "idx_call_activity_mapping_call_activity_node" ON "call_activity_mapping"("call_activity_node_id");

-- V20: Code Task Support - JAR storage
CREATE TABLE "code_task_jar" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "content" BLOB NOT NULL,
    "file_name" VARCHAR(255) NOT NULL,
    "file_hash" VARCHAR(64) NOT NULL UNIQUE,
    "upload_date" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "uploaded_by" VARCHAR(255),
    "description" CLOB
);

CREATE INDEX "idx_code_task_jar_file_hash" ON "code_task_jar"("file_hash");
CREATE INDEX "idx_code_task_jar_upload_date" ON "code_task_jar"("upload_date");

-- V20: Code Task Support - class metadata
CREATE TABLE "code_class_metadata" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "jar_id" BIGINT NOT NULL,
    "class_name" VARCHAR(500) NOT NULL,
    "method_name" VARCHAR(255) NOT NULL,
    "method_signature" CLOB,
    "input_params" CLOB,
    "return_type" VARCHAR(255),
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "fk_code_class_jar" FOREIGN KEY ("jar_id") REFERENCES "code_task_jar"("id"),
    CONSTRAINT "uk_code_class_metadata" UNIQUE ("jar_id", "class_name", "method_name")
);

CREATE INDEX "idx_code_class_jar_id" ON "code_class_metadata"("jar_id");
CREATE INDEX "idx_code_class_name" ON "code_class_metadata"("class_name");
CREATE INDEX "idx_code_class_method_name" ON "code_class_metadata"("method_name");

-- V20: Code Task Support - execution audit trail
CREATE TABLE "code_task_execution" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "instance_id" BIGINT NOT NULL,
    "node_id" VARCHAR(255),
    "jar_id" BIGINT,
    "class_name" VARCHAR(500),
    "method_name" VARCHAR(255),
    "input_variables" CLOB,
    "output_variables" CLOB,
    "execution_time_ms" INTEGER,
    "status" VARCHAR(50),
    "error_message" CLOB,
    "executed_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "fk_code_exec_jar" FOREIGN KEY ("jar_id") REFERENCES "code_task_jar"("id")
);

CREATE INDEX "idx_code_execution_instance_id" ON "code_task_execution"("instance_id");
CREATE INDEX "idx_code_execution_jar_id" ON "code_task_execution"("jar_id");
CREATE INDEX "idx_code_execution_status" ON "code_task_execution"("status");
CREATE INDEX "idx_code_execution_executed_at" ON "code_task_execution"("executed_at");

-- Indexes on process_instance for V19 call activity columns
CREATE INDEX "idx_process_instance_parent_id" ON "process_instance"("parent_instance_id");
CREATE INDEX "idx_process_instance_call_activity_node" ON "process_instance"("call_activity_node_id");
CREATE INDEX "idx_process_instance_nesting_level" ON "process_instance"("nesting_level");
