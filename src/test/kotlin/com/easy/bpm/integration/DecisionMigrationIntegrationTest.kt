package com.easy.bpm.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import org.flywaydb.core.Flyway

class DecisionMigrationIntegrationTest {
    @Test fun `append migration preserves existing process data and constrains decision versions`() {
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).use { pg ->
            pg.start()
            Flyway.configure().dataSource(pg.jdbcUrl, pg.username, pg.password).target("47").load().migrate()
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { connection ->
                connection.createStatement().use { sql ->
                    sql.execute("insert into process_definition(process_id,process_name,version,definition_json) values ('legacy','Legacy',1,'<xml/>')")
                    sql.execute("insert into process_instance(id,process_definition_id,status,created_at,updated_at) values (10,1,'ACTIVE',now(),now())")
                }
            }
            assertEquals(1, Flyway.configure().dataSource(pg.jdbcUrl, pg.username, pg.password).load().migrate().migrationsExecuted)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { connection ->
                connection.createStatement().use { sql ->
                    sql.execute("insert into decision_definition(decision_key,name,version,state,dmn_xml,created_at) values ('key','name',1,'PUBLISHED','<xml/>',now())")
                    assertThrows(java.sql.SQLException::class.java) { sql.execute("insert into decision_definition(decision_key,name,version,state,dmn_xml,created_at) values ('key','name',1,'PUBLISHED','changed',now())") }
                    sql.execute("insert into decision_task_pin(instance_id,node_id,definition_id) values(10,'rule',1)")
                    assertThrows(java.sql.SQLException::class.java) { sql.execute("insert into decision_task_pin(instance_id,node_id,definition_id) values(10,'rule',1)") }
                    sql.executeQuery("select count(*) from process_instance where id=10").use { rows -> rows.next(); assertEquals(1, rows.getInt(1)) }
                    sql.execute("delete from process_instance where id=10")
                    sql.executeQuery("select count(*) from decision_task_pin").use { rows -> rows.next(); assertEquals(0, rows.getInt(1)) }
                }
            }
        }
    }
}
