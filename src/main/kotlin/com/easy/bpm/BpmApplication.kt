package com.easy.bpm

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.flywaydb.core.Flyway

@SpringBootApplication(scanBasePackages = ["com.easy.bpm.*"])
@EnableScheduling
class BpmApplication

fun main(args: Array<String>) {
	runApplication<BpmApplication>(*args)
}
