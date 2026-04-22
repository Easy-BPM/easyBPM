package com.easy.bpm

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class BpmApplication

fun main(args: Array<String>) {
    runApplication<BpmApplication>(*args)
}


