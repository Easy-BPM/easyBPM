package com.easy.bpm.integration

import com.easy.bpm.config.PostgresTestContainer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for all integration tests using SHARED PostgreSQL TestContainer.
 * 
 * Uses a SINGLETON PostgreSQL container shared across entire test suite with:
 * - Persistent connections (no container restart overhead)
 * - create-drop mode for schema management (ensures fresh schema for each test class context)
 * - Per-test-class isolation via @SpringBootTest (new context per class)
 * - Tests use @Transactional for data isolation
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
abstract class IntegrationTestBase {

    companion object {
        // Reference to the SHARED singleton container
        private val postgresContainer = PostgresTestContainer.instance

        @JvmStatic
        @DynamicPropertySource
        fun configurePostgres(registry: DynamicPropertyRegistry) {
            // Use SHARED singleton container for all tests
            registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
            registry.add("spring.datasource.username") { postgresContainer.username }
            registry.add("spring.datasource.password") { postgresContainer.password }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.PostgreSQLDialect" }
            // Use update mode for shared container (avoids drop-on-close issues)
            // Hibernate will create any missing tables on startup
            registry.add("spring.jpa.hibernate.ddl-auto") { "update" }
        }
    }
}
