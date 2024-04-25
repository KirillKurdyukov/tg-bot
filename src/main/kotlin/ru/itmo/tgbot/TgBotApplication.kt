package ru.itmo.tgbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TgBotApplication

fun main(args: Array<String>) {
    runApplication<TgBotApplication>(*args)
}
