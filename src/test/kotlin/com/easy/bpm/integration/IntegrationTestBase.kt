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
 * Uses a SINGLETON PostgreSQL container shared across entire test suite.
 * This prevents container startup/shutdown overhead and connection timeout issues.
 * 
 * Key Benefits:
 * - Single container instance for all tests (reduces startup time)
 * - Persistent connections (no timeout issues)
 * - Faster test execution (container already running)
 * - Consistent test environment
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
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
        }
    }
}
