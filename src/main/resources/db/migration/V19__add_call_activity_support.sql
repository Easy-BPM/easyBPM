-- V19: Add Call Activity & Subprocess Support
-- This migration extends process_instance table for parent-child subprocess relationships
-- and creates call_activity_mapping table for variable input/output mapping tracking

DO $$
BEGIN
    -- Step 1: Add call activity columns to process_instance table
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_instance' AND column_name='parent_instance_id'
    ) THEN
        ALTER TABLE process_instance
            ADD COLUMN parent_instance_id BIGINT NULL
            REFERENCES process_instance(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_instance' AND column_name='call_activity_node_id'
    ) THEN
        ALTER TABLE process_instance
            ADD COLUMN call_activity_node_id VARCHAR(255) NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_instance' AND column_name='nesting_level'
    ) THEN
        ALTER TABLE process_instance
            ADD COLUMN nesting_level INT DEFAULT 0;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='process_instance' AND column_name='completion_node_id'
    ) THEN
        ALTER TABLE process_instance
            ADD COLUMN completion_node_id VARCHAR(255) NULL;
    END IF;

    -- Step 2: Create indexes on parent_instance_id for efficient parent-child queries
    
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename='process_instance' AND indexname='idx_process_instance_parent_id'
    ) THEN
        CREATE INDEX idx_process_instance_parent_id ON process_instance(parent_instance_id);
    END IF;

    -- Step 3: Create index on call_activity_node_id for efficient lookup
    
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename='process_instance' AND indexname='idx_process_instance_call_activity_node'
    ) THEN
        CREATE INDEX idx_process_instance_call_activity_node ON process_instance(call_activity_node_id);
    END IF;

    -- Step 4: Create index on nesting_level for query optimization
    
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename='process_instance' AND indexname='idx_process_instance_nesting_level'
    ) THEN
        CREATE INDEX idx_process_instance_nesting_level ON process_instance(nesting_level);
    END IF;

END$$;

-- Step 5: Create call_activity_mapping table
-- This table tracks input and output variable mappings between parent and child instances

CREATE TABLE IF NOT EXISTS call_activity_mapping (
    id SERIAL PRIMARY KEY,
    parent_instance_id BIGINT NOT NULL
        REFERENCES process_instance(id) ON DELETE CASCADE,
    child_instance_id BIGINT NOT NULL
        REFERENCES process_instance(id) ON DELETE CASCADE,
    call_activity_node_id VARCHAR(255) NOT NULL,
    input_mappings JSONB DEFAULT '{}',  -- e.g., {"orderId": "order_id", "customerId": "customer_id"}
    output_mappings JSONB DEFAULT '{}',  -- e.g., {"paymentStatus": "status", "transactionId": "tx_id"}
    propagate_all_variables BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_parent_instance FOREIGN KEY (parent_instance_id)
        REFERENCES process_instance(id) ON DELETE CASCADE,
    CONSTRAINT fk_child_instance FOREIGN KEY (child_instance_id)
        REFERENCES process_instance(id) ON DELETE CASCADE,
    CONSTRAINT unique_call_activity_mapping UNIQUE (parent_instance_id, child_instance_id, call_activity_node_id)
);

-- Create index for efficient lookup of mappings by parent instance
CREATE INDEX IF NOT EXISTS idx_call_activity_mapping_parent_id
    ON call_activity_mapping(parent_instance_id);

-- Create index for efficient lookup of mappings by child instance
CREATE INDEX IF NOT EXISTS idx_call_activity_mapping_child_id
    ON call_activity_mapping(child_instance_id);

-- Create index for efficient lookup of mappings by call activity node
CREATE INDEX IF NOT EXISTS idx_call_activity_mapping_call_activity_node
    ON call_activity_mapping(call_activity_node_id);

-- Add comment for documentation purposes
COMMENT ON TABLE call_activity_mapping IS 'Tracks variable input/output mappings for call activity (subprocess) executions. Maps parent process variables to child process variables and vice versa.';
COMMENT ON COLUMN call_activity_mapping.input_mappings IS 'JSON object mapping parent variable names to child variable names. E.g., {"orderId": "order_id"}';
COMMENT ON COLUMN call_activity_mapping.output_mappings IS 'JSON object mapping child variable names to parent variable names. E.g., {"status": "paymentStatus"}';
COMMENT ON COLUMN call_activity_mapping.propagate_all_variables IS 'If true, all parent variables are propagated to child, and all child variables are propagated back to parent.';
