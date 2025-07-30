ALTER TABLE process_instance
    DROP COLUMN IF EXISTS current_node;

ALTER TABLE process_instance
    DROP COLUMN IF EXISTS context;

ALTER TABLE process_instance
    ADD COLUMN current_nodes JSONB;