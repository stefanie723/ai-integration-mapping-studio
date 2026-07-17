package com.aims

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AimsApplication

fun main(args: Array<String>) {
    runApplication<AimsApplication>(*args)
}
