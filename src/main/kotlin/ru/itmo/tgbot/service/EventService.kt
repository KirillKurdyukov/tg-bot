package ru.itmo.tgbot.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.tgbot.exception.EventAlreadyExistsException
import ru.itmo.tgbot.exception.NoAdminPermissionException
import ru.itmo.tgbot.exception.NoEventFoundException
import ru.itmo.tgbot.exception.NoUserFoundException
import ru.itmo.tgbot.exception.UserAlreadyExistsException
import ru.itmo.tgbot.model.Event
import ru.itmo.tgbot.model.Role
import ru.itmo.tgbot.model.User
import ru.itmo.tgbot.repository.EventRepository
import ru.itmo.tgbot.repository.UserRepository
import ru.itmo.tgbot.utils.WithLogging
import ru.itmo.tgbot.utils.logger

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository
) : WithLogging {

    private val log = logger()

    fun addEvent(userTelegramId: String, eventName: String) {
        val user = userRepository.findUserByTelegramId(userTelegramId) ?: run {
            log.warning("User with id $userTelegramId not found")
            throw NoUserFoundException()
        }
        checkUserPermission(user)
        try {
            eventRepository.save(Event(name = eventName, users = mutableListOf()))
            log.info("Event $eventName added")
        } catch (e: DataIntegrityViolationException) {
            log.warning("Event $eventName already exists")
            throw EventAlreadyExistsException(e)
        }
    }

    fun getUsers(eventName: String): List<String> {
        val event = eventRepository.findEventByName(eventName) ?: throw NoEventFoundException()
        return event.users.map { it.userName }
    }

    fun addUser(userName: String, telegramUserId: String) {
        try {
            userRepository.save(User(userName = userName, telegramId = telegramUserId, event = null))
            log.info("User $userName with id $telegramUserId added")
        } catch (e: DataIntegrityViolationException) {
            log.warning("User with telegram id $telegramUserId already exists")
            throw UserAlreadyExistsException(e)
        }
    }

    fun hasUser(telegramUserId: String): Boolean = userRepository.existsByTelegramId(telegramUserId)

    fun getUser(telegramUserId: String): User {
        return userRepository.findUserByTelegramId(telegramUserId) ?: run {
            log.warning("User with id $telegramUserId not found")
            throw NoUserFoundException()
        }
    }

    @Transactional
    fun addUserToEvent(userTelegramId: String, eventName: String) {
        val event = eventRepository.findEventByName(eventName) ?: throw NoEventFoundException()
        val user = userRepository.findUserByTelegramId(userTelegramId) ?: throw NoUserFoundException()
        event.addUser(user)
    }

    @Transactional
    fun deleteUserFromEvent(userTelegramId: String, eventName: String) {
        val event = eventRepository.findEventByName(eventName) ?: throw NoEventFoundException()
        val user = userRepository.findUserByTelegramId(userTelegramId) ?: throw NoUserFoundException()
        event.removeUser(user)
    }


    private fun checkUserPermission(user: User) {
        if (user.role != Role.ADMIN) {
            log.warning("User with telegram id ${user.telegramId} doesn't have admin permission")
            throw NoAdminPermissionException()
        }
    }
}
