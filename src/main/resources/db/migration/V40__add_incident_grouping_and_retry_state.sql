ALTER TABLE incident
    ADD COLUMN IF NOT EXISTS process_definition_id BIGINT,
    ADD COLUMN IF NOT EXISTS incident_signature VARCHAR(512),
    ADD COLUMN IF NOT EXISTS retry_status VARCHAR(50) NOT NULL DEFAULT 'NOT_ELIGIBLE',
    ADD COLUMN IF NOT EXISTS retry_attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_retry_attempts INT NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_retry_error TEXT;

UPDATE incident i
SET process_definition_id = pi.process_definition_id,
    incident_signature = CONCAT_WS(
        '|',
        i.source,
        pi.process_definition_id,
        COALESCE(i.node_id, ''),
        COALESCE(NULLIF(i.external_reference_id, ''), LOWER(REGEXP_REPLACE(i.message, '[0-9]+', '#', 'g')))
    ),
    retry_status = CASE WHEN i.source = 'WORKER' AND i.status <> 'RESOLVED' THEN 'RETRY_ELIGIBLE' ELSE 'NOT_ELIGIBLE' END
FROM process_instance pi
WHERE pi.id = i.process_instance_id
  AND i.incident_signature IS NULL;

UPDATE incident
SET incident_signature = CONCAT_WS(
    '|',
    source,
    COALESCE(process_definition_id::TEXT, 'unknown'),
    COALESCE(node_id, ''),
    COALESCE(NULLIF(external_reference_id, ''), LOWER(REGEXP_REPLACE(message, '[0-9]+', '#', 'g')))
)
WHERE incident_signature IS NULL;

CREATE INDEX IF NOT EXISTS idx_incident_signature_open
    ON incident (incident_signature, status, last_occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_incident_retry_status
    ON incident (retry_status, last_occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_incident_process_definition
    ON incident (process_definition_id, node_id);
