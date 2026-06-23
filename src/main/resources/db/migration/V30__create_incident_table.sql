CREATE TABLE IF NOT EXISTS incident (
    id BIGSERIAL PRIMARY KEY,
    process_instance_id BIGINT NOT NULL,
    node_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    severity VARCHAR(50) NOT NULL DEFAULT 'HIGH',
    source VARCHAR(50) NOT NULL DEFAULT 'PROCESS_ENGINE',
    message TEXT NOT NULL,
    technical_details TEXT,
    external_reference_id VARCHAR(255),
    occurrence_count INT NOT NULL DEFAULT 1,
    last_occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(255),
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(255),
    resolution_note TEXT,
    resolution_action VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_incident_status ON incident(status);
CREATE INDEX IF NOT EXISTS idx_incident_source ON incident(source);
CREATE INDEX IF NOT EXISTS idx_incident_process_instance ON incident(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_incident_created_at ON incident(created_at DESC);

CREATE TABLE IF NOT EXISTS incident_event (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    actor VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_incident_event_incident ON incident_event(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_event_created_at ON incident_event(created_at DESC);
