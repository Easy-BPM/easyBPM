package com.easy.bpm.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.event.EventListener
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.stereotype.Component
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource
import java.sql.Connection

/**
 * Ensures CallActivityMapping table exists in test database.
 * This workaround addresses issue where Hibernate's update DDL mode
 * doesn't create the table in test environment with shared PostgreSQL container.
 */
@Component
class SchemaInitializer(@Autowired val dataSource: DataSource) {
    
    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun ensureSchemaExists() {
        println("SchemaInitializer: Ensuring call_activity_mapping table exists...")
        var connection: Connection? = null
        try {
            connection = dataSource.connection
            
            val statements = listOf(
                """
                CREATE TABLE IF NOT EXISTS call_activity_mapping (
                    id BIGSERIAL PRIMARY KEY,
                    parent_instance_id BIGINT NOT NULL,
                    child_instance_id BIGINT NOT NULL,
                    call_activity_node_id VARCHAR(255) NOT NULL,
                    input_mappings JSONB DEFAULT '{}',
                    output_mappings JSONB DEFAULT '{}',
                    propagate_all_variables BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    CONSTRAINT fk_parent_instance FOREIGN KEY (parent_instance_id)
                        REFERENCES process_instance(id) ON DELETE CASCADE,
                    CONSTRAINT fk_child_instance FOREIGN KEY (child_instance_id)
                        REFERENCES process_instance(id) ON DELETE CASCADE,
                    CONSTRAINT unique_call_activity_mapping UNIQUE (parent_instance_id, child_instance_id, call_activity_node_id)
                )
                """,
                """
                CREATE INDEX IF NOT EXISTS idx_call_activity_mapping_parent_id 
                ON call_activity_mapping(parent_instance_id)
                """,
                """
                CREATE INDEX IF NOT EXISTS idx_call_activity_mapping_child_id 
                ON call_activity_mapping(child_instance_id)
                """,
                """
                CREATE INDEX IF NOT EXISTS idx_call_activity_mapping_call_activity_node 
                ON call_activity_mapping(call_activity_node_id)
                """
            )
            
            for (sql in statements) {
                try {
                    val stmt = connection.createStatement()
                    stmt.execute(sql.trimIndent())
                    stmt.close()
                    println("SchemaInitializer: Executed: ${sql.trim().split("\n").first()}")
                } catch (e: Exception) {
                    println("SchemaInitializer: Ignored error (likely already exists): ${e.message}")
                }
            }
            
            connection.commit()
            println("SchemaInitializer: Schema initialization complete")
            
        } catch (e: Exception) {
            System.err.println("SchemaInitializer: Error: ${e.message}")
            e.printStackTrace()
        } finally {
            connection?.close()
        }
    }
}
