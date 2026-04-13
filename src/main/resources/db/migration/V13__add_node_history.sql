ALTER TABLE process_instance
    ADD COLUMN node_history jsonb DEFAULT '[]'::jsonb;
