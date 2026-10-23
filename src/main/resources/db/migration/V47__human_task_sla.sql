ALTER TABLE task ADD COLUMN due_date timestamptz,
 ADD COLUMN sla_risk_at timestamptz,
 ADD COLUMN sla_time_zone varchar(255),
 ADD COLUMN sla_policy text,
 ADD COLUMN sla_completed_at timestamptz,
 ADD COLUMN completed_within_sla boolean,
 ADD COLUMN assignment_version bigint NOT NULL DEFAULT 0;
CREATE INDEX idx_task_due_date ON task(due_date, id);
CREATE INDEX idx_task_pending_due ON task(due_date, id) WHERE status = 'PENDING' AND due_date IS NOT NULL;
CREATE TABLE task_sla_action (
 id bigserial PRIMARY KEY,
 task_id bigint NOT NULL REFERENCES task(id) ON DELETE CASCADE,
 action_id varchar(100) NOT NULL,
 action_type varchar(50) NOT NULL,
 target varchar(255),
 scheduled_at timestamptz NOT NULL,
 sequence_no integer NOT NULL,
 outcome varchar(30) NOT NULL DEFAULT 'PENDING',
 attempts integer NOT NULL DEFAULT 0,
 executed_at timestamptz,
 incident_id bigint REFERENCES incident(id) ON DELETE SET NULL,
 assignment_version_at_failure bigint,
 UNIQUE(task_id, action_id)
);
CREATE INDEX idx_sla_action_pending ON task_sla_action(scheduled_at, task_id, sequence_no) WHERE outcome = 'PENDING';
CREATE TABLE task_sla_event (
 id uuid PRIMARY KEY,
 task_id bigint NOT NULL REFERENCES task(id) ON DELETE CASCADE,
 process_instance_id bigint NOT NULL,
 action_id varchar(100),
 event_type varchar(60) NOT NULL,
 scheduled_at timestamptz NOT NULL,
 occurred_at timestamptz NOT NULL,
 due_date timestamptz NOT NULL,
 outcome varchar(30) NOT NULL,
 message text NOT NULL,
 actor varchar(255) NOT NULL,
 incident_id bigint REFERENCES incident(id) ON DELETE SET NULL
);
CREATE INDEX idx_sla_event_history ON task_sla_event(task_id, occurred_at, id);
