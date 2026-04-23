package com.easy.bpm.integration

import com.easy.bpm.config.PostgresTestContainer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for all integration tests using PostgreSQL TestContainers.
 * Automatically manages PostgreSQL container lifecycle and datasource configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
abstract class IntegrationTestBase {

    companion object {
        @Container
        val postgreSQLContainer = PostgresTestContainer.getPostgresContainer()

        @JvmStatic
        @DynamicPropertySource
        fun configurePostgres(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgreSQLContainer.jdbcUrl }
            registry.add("spring.datasource.username") { postgreSQLContainer.username }
            registry.add("spring.datasource.password") { postgreSQLContainer.password }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.PostgreSQLDialect" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
        }
    }
}
