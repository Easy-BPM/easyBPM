-- V24: Add ad-hoc subprocess decision audit trail

CREATE TABLE IF NOT EXISTS ad_hoc_decision_audit (
    id BIGSERIAL PRIMARY KEY,
    process_instance_id BIGINT NOT NULL REFERENCES process_instance(id) ON DELETE CASCADE,
    ad_hoc_node_id VARCHAR(255) NOT NULL,
    activity_node_id VARCHAR(255),
    decision_type VARCHAR(100) NOT NULL,
    actor_type VARCHAR(50) NOT NULL DEFAULT 'system',
    actor_id VARCHAR(255),
    confidence DOUBLE PRECISION,
    recommendation TEXT,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ad_hoc_audit_instance_node
    ON ad_hoc_decision_audit(process_instance_id, ad_hoc_node_id);

CREATE INDEX IF NOT EXISTS idx_ad_hoc_audit_created_at
    ON ad_hoc_decision_audit(created_at DESC);

