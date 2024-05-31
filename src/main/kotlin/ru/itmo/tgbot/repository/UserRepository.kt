package ru.itmo.tgbot.repository

import org.springframework.data.repository.CrudRepository
import ru.itmo.tgbot.model.User

interface UserRepository: CrudRepository<User, Long> {
    fun findUserByTelegramId(telegramId: String): User?
    fun existsByTelegramId(telegramId: String): Boolean
}