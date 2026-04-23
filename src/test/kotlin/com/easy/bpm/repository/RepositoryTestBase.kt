package com.easy.bpm.repository

import com.easy.bpm.config.PostgresTestContainer
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for all repository tests (@DataJpaTest) using SHARED PostgreSQL TestContainer.
 * 
 * Uses a SINGLETON PostgreSQL container shared across entire test suite with:
 * - Persistent connections (no container restart overhead)
 * - update mode for schema management (avoids DDL conflicts in shared container)
 * - Per-test-class isolation via @DataJpaTest (new context per class)
 * - Tests use @Transactional for data isolation
 */
@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
abstract class RepositoryTestBase {

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
            // Use update mode to safely add tables without conflicts in shared container
            registry.add("spring.jpa.hibernate.ddl-auto") { "update" }
        }
    }
}
