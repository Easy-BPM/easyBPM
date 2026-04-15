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
    "process_key" VARCHAR(255) NOT NULL,
    "name" VARCHAR(255) NOT NULL,
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
    "updated_at" TIMESTAMP
);

CREATE TABLE "process_variable" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "process_instance_id" BIGINT,
    "name" VARCHAR(255) NOT NULL,
    "value" CLOB
);

CREATE TABLE "form" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "name" VARCHAR(255) NOT NULL,
    "schema" CLOB NOT NULL,
    "version" INTEGER NOT NULL,
    "created_at" TIMESTAMP
);

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
