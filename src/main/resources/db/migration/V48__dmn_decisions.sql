CREATE TABLE decision_definition (
 id BIGSERIAL PRIMARY KEY, decision_key VARCHAR(255) NOT NULL, name VARCHAR(255) NOT NULL,
 version INTEGER NOT NULL CHECK (version > 0), state VARCHAR(32) NOT NULL CHECK (state = 'PUBLISHED'),
 dmn_xml TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT decision_definition_key_version UNIQUE (decision_key, version)
);
CREATE TABLE decision_task_pin (
 id BIGSERIAL PRIMARY KEY, instance_id BIGINT NOT NULL REFERENCES process_instance(id) ON DELETE CASCADE,
 node_id VARCHAR(255) NOT NULL, definition_id BIGINT NOT NULL REFERENCES decision_definition(id),
 CONSTRAINT decision_task_pin_instance_node UNIQUE (instance_id, node_id)
);
CREATE TABLE decision_test_case (
 id BIGSERIAL PRIMARY KEY, decision_key VARCHAR(255) NOT NULL, name VARCHAR(255) NOT NULL,
 inputs_json TEXT NOT NULL, expected_outputs_json TEXT NOT NULL
);
CREATE INDEX decision_test_case_key_idx ON decision_test_case(decision_key);
CREATE TABLE decision_execution (
 id BIGSERIAL PRIMARY KEY, decision_key VARCHAR(255) NOT NULL, decision_version INTEGER,
 process_instance_id BIGINT, node_id VARCHAR(255), status VARCHAR(32) NOT NULL,
 duration_ms BIGINT NOT NULL, matched_rule_ids_json TEXT NOT NULL, inputs_json TEXT NOT NULL,
 outputs_json TEXT NOT NULL, explanations_json TEXT NOT NULL, error_code VARCHAR(255), executed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX decision_execution_time_idx ON decision_execution(executed_at DESC, id DESC);
CREATE INDEX decision_execution_instance_idx ON decision_execution(process_instance_id, node_id);
CREATE INDEX decision_execution_key_idx ON decision_execution(decision_key, decision_version);
