package com.easy.bpm.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Test configuration that explicitly scans for entities, ensuring CallActivityMapping is discovered.
 */
@Configuration
@TestConfiguration
@EntityScan(basePackages = [
    "com.easy.bpm.model",
    "com.easy.bpm.entity"
])
@EnableJpaRepositories(basePackages = [
    "com.easy.bpm.repository",
    "com.easy.bpm.repository.process"
])
class TestEntityScanConfiguration {
    // Explicitly ensures all entities are discovered in test environment
}
