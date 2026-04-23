package com.easy.bpm.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * TestContainers configuration for PostgreSQL integration tests.
 * Manages the lifecycle of a PostgreSQL Docker container for testing.
 */
@TestConfiguration
class PostgresTestContainer {

    companion object {
        private var postgresContainer: PostgreSQLContainer<*>? = null

        fun getPostgresContainer(): PostgreSQLContainer<*> {
            if (postgresContainer == null) {
                postgresContainer = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("bpm_test")
                    .withUsername("bpm_user")
                    .withPassword("bpm_password")
                    .withReuse(false)
                postgresContainer!!.start()
            }
            return postgresContainer!!
        }

        fun stopPostgresContainer() {
            postgresContainer?.stop()
            postgresContainer = null
        }

        fun getJdbcUrl(): String = getPostgresContainer().jdbcUrl

        fun getUsername(): String = getPostgresContainer().username

        fun getPassword(): String = getPostgresContainer().password

        fun getDatabaseName(): String = getPostgresContainer().databaseName
    }
}
