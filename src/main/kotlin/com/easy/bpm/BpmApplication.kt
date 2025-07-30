package com.easy.bpm

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.flywaydb.core.Flyway
@SpringBootApplication(scanBasePackages = ["com.easy.bpm.*"])
class BpmApplication

fun main(args: Array<String>) {
	runApplication<BpmApplication>(*args)
}
