package com.easy.bpm.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

class ApiClientMigrationIntegrationTest {

    @Test
    fun `V43 creates API client schema and permissions on PostgreSQL`() {
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).use { postgres ->
            postgres.start()
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        create table app_permission (
                            id bigserial primary key,
                            code varchar(100) not null unique,
                            name varchar(255) not null,
                            created_at timestamp not null,
                            updated_at timestamp not null
                        )
                        """.trimIndent()
                    )
                    val migration = requireNotNull(javaClass.getResource("/db/migration/V43__create_api_clients.sql"))
                        .readText()
                    statement.execute(migration)
                }

                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        """
                        select count(*)
                          from information_schema.tables
                         where table_schema = 'public'
                           and table_name in ('api_client', 'api_client_permission', 'api_client_audit')
                        """.trimIndent()
                    ).use { rows ->
                        rows.next()
                        assertThat(rows.getInt(1)).isEqualTo(3)
                    }
                    statement.executeQuery(
                        """
                        select count(*)
                          from pg_indexes
                         where schemaname = 'public'
                           and indexname in (
                               'idx_api_client_status',
                               'idx_api_client_expiry',
                               'idx_api_client_audit_client_time',
                               'idx_api_client_audit_category_time',
                               'idx_api_client_audit_time'
                           )
                        """.trimIndent()
                    ).use { rows ->
                        rows.next()
                        assertThat(rows.getInt(1)).isEqualTo(5)
                    }
                    statement.executeQuery(
                        """
                        select code
                          from app_permission
                         where code in ('VIEW_API_CLIENTS', 'MANAGE_API_CLIENTS')
                         order by code
                        """.trimIndent()
                    ).use { rows ->
                        val codes = buildList {
                            while (rows.next()) add(rows.getString(1))
                        }
                        assertThat(codes).containsExactly("MANAGE_API_CLIENTS", "VIEW_API_CLIENTS")
                    }
                }
            }
        }
    }
}
