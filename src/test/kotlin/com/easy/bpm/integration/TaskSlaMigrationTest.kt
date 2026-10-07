package com.easy.bpm.integration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

class TaskSlaMigrationTest {
    @Test fun `Flyway upgrades existing rows and preserves legacy clock with indexed durable schedules`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { database ->
            database.start()
            Flyway.configure().dataSource(database.jdbcUrl,database.username,database.password).target("46").load().migrate()
            DriverManager.getConnection(database.jdbcUrl,database.username,database.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("insert into process_definition(process_id,process_name,version,definition_json) values ('legacy','Legacy',1,'<xml/>')")
                    statement.execute("insert into process_instance(process_definition_id,status,created_at,updated_at) values (1,'ACTIVE','2026-01-01 12:00:00','2026-01-01 12:00:00')")
                    statement.execute("insert into task(process_instance_id,node_id,status,created_at) values (1,'review','PENDING','2026-01-01 12:00:00')")
                }
            }
            val result=Flyway.configure().dataSource(database.jdbcUrl,database.username,database.password).load().migrate()
            assertEquals(1,result.migrationsExecuted)
            DriverManager.getConnection(database.jdbcUrl,database.username,database.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("select due_date,sla_policy,created_at,assignment_version from task where id=1").use { rows ->
                        assertTrue(rows.next());assertNull(rows.getObject("due_date"));assertNull(rows.getObject("sla_policy"));assertEquals("2026-01-01 12:00:00.0",rows.getTimestamp("created_at").toString());assertEquals(0L,rows.getLong("assignment_version"))
                    }
                    statement.executeQuery("select count(*) from pg_indexes where indexname in ('idx_task_due_date','idx_task_pending_due','idx_sla_action_pending','idx_sla_event_history')").use { rows ->rows.next();assertEquals(4,rows.getInt(1)) }
                    statement.execute("insert into task_sla_action(task_id,action_id,action_type,scheduled_at,sequence_no) values (1,'one','ALERT',now(),0)")
                    assertThrows(java.sql.SQLException::class.java) { statement.execute("insert into task_sla_action(task_id,action_id,action_type,scheduled_at,sequence_no) values (1,'one','ALERT',now(),0)") }
                    statement.execute("delete from task where id=1")
                    statement.executeQuery("select count(*) from task_sla_action").use { rows ->rows.next();assertEquals(0,rows.getInt(1)) }
                }
            }
        }
    }
}
