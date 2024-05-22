package ru.itmo.tgbot.repository

import org.springframework.data.repository.CrudRepository
import ru.itmo.tgbot.model.User
import ru.itmo.tgbot.model.UserId

interface UserRepository: CrudRepository<User, UserId> {
    fun findUserByTelegramId(telegramId: String): User?
    fun existsByTelegramId(telegramId: String): Boolean
}