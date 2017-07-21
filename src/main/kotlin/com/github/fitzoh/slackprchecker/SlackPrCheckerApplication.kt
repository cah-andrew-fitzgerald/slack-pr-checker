package com.github.fitzoh.slackprchecker

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class SlackPrCheckerApplication


fun main(args: Array<String>) {
    SpringApplication.run(SlackPrCheckerApplication::class.java, *args)
}
