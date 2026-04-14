package com.easy.bpm.actuator

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import javax.sql.DataSource
import com.rabbitmq.client.ConnectionFactory
import java.sql.Connection

/**
 * Custom health indicator for PostgreSQL database connectivity.
 */
@Component("database")
class DatabaseHealthIndicator(
    private val dataSource: DataSource
) : HealthIndicator {
    override fun health(): Health {
        return try {
            val connection: Connection = dataSource.connection
            val timeout = 2
            val isValid = connection.isValid(timeout)
            connection.close()
            
            if (isValid) {
                Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("validationTimeout", "${timeout}s")
                    .build()
            } else {
                Health.down()
                    .withDetail("reason", "Connection validation failed")
                    .build()
            }
        } catch (e: Exception) {
            Health.down()
                .withException(e)
                .withDetail("error", e.message)
                .build()
        }
    }
}

/**
 * Custom health indicator for RabbitMQ connectivity.
 */
@Component("rabbitmq")
class RabbitMQHealthIndicator : HealthIndicator {
    override fun health(): Health {
        return try {
            val factory = ConnectionFactory()
            factory.host = System.getenv("RABBITMQ_HOST") ?: "localhost"
            factory.port = System.getenv("RABBITMQ_PORT")?.toIntOrNull() ?: 5672
            factory.username = System.getenv("RABBITMQ_USER") ?: "easybpm"
            factory.password = System.getenv("RABBITMQ_PASSWORD") ?: "easybpm"
            
            val connection = factory.newConnection()
            connection.close()
            
            Health.up()
                .withDetail("broker", "RabbitMQ")
                .withDetail("host", factory.host)
                .withDetail("port", factory.port)
                .build()
        } catch (e: Exception) {
            Health.down()
                .withException(e)
                .withDetail("error", e.message)
                .build()
        }
    }
}
