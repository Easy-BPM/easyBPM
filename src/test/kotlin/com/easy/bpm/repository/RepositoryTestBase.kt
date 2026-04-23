package com.easy.bpm.repository

import com.easy.bpm.config.PostgresTestContainer
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for all repository tests (@DataJpaTest) using PostgreSQL TestContainers.
 * Automatically manages PostgreSQL container lifecycle and datasource configuration.
 */
@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
abstract class RepositoryTestBase {

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
