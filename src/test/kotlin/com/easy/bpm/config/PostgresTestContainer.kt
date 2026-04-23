package com.easy.bpm.config

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * SINGLETON PostgreSQL TestContainer - Shared across ALL tests.
 * 
 * Uses Testcontainers' reuse capability to maintain a single PostgreSQL instance
 * for the entire test suite, preventing container startup/shutdown overhead and
 * connection timeout issues.
 * 
 * Enable with: export TESTCONTAINERS_RYUK_DISABLED=true
 * Or use Docker for Desktop with container reuse enabled.
 */
object PostgresTestContainer {
    /**
     * SHARED instance - Created once, reused for entire test suite.
     * Static initialization ensures only one container across all test classes.
     */
    val instance: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
        .withDatabaseName("bpm_test")
        .withUsername("bpm_user")
        .withPassword("bpm_password")
        .withReuse(true)  // CRITICAL: Reuse same container across test runs
        .apply {
            start()
            // Log container info for debugging
            println("PostgreSQL TestContainer started:")
            println("  JDBC URL: $jdbcUrl")
            println("  Host: $host")
            println("  Port: $firstMappedPort")
            println("  Database: $databaseName")
        }

    // Convenience accessors
    fun getPostgresContainer(): PostgreSQLContainer<*> = instance
    fun getJdbcUrl(): String = instance.jdbcUrl
    fun getUsername(): String = instance.username
    fun getPassword(): String = instance.password
    fun getDatabaseName(): String = instance.databaseName
}
