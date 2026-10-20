package com.easy.bpm.decision

import com.fasterxml.jackson.databind.DeserializationFeature
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DecisionJsonConfig {
    /** Preserve typed process/decision decimals at the HTTP boundary, before any evaluation. */
    @Bean
    fun exactDecisionNumbers() = Jackson2ObjectMapperBuilderCustomizer { it.featuresToEnable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS) }
}
