package ru.itmo.tgbot

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.beans.factory.annotation.Autowired
import org.telegram.telegrambots.longpolling.starter.TelegramBotInitializer
import ru.itmo.tgbot.exception.EventAlreadyExistsException
import ru.itmo.tgbot.exception.NoAdminPermissionException
import ru.itmo.tgbot.exception.NoEventFoundException
import ru.itmo.tgbot.exception.NoUserFoundException
import ru.itmo.tgbot.exception.ParticipationInEventNotFoundException
import ru.itmo.tgbot.model.Event
import ru.itmo.tgbot.model.Role
import ru.itmo.tgbot.model.User
import ru.itmo.tgbot.repository.EventRepository
import ru.itmo.tgbot.repository.UserRepository
import ru.itmo.tgbot.service.EventService

class TgBotApplicationTests : BaseTest() {

    companion object {
        private val adminUser = User(telegramId = "1", userName = "test", event = null, role = Role.ADMIN)
        private val regularUser = User(telegramId = "2", userName = "user", event = null, role = Role.REGULAR)
        val newEvent = Event(0L, "NewEvent", mutableListOf())
        val newEventName = "NewEvent"
    }
    
    @Autowired
    private lateinit var userRepository: UserRepository
    @Autowired
    private lateinit var eventRepository: EventRepository
    @Autowired
    private lateinit var eventService: EventService

    @MockBean
    private lateinit var botInitializer: TelegramBotInitializer

    @Test
    fun contextLoads() {
    }

    @BeforeEach
    fun reset() {
        userRepository.deleteAll()
        eventRepository.deleteAll()
        userRepository.save(adminUser)
        userRepository.save(regularUser)
    }

    @Test
    fun `addEvent should throw NoAdminPermissionException if user is not admin`() {
        assertThrows<NoAdminPermissionException> {
            eventService.addEvent(regularUser.telegramId, "EventName")
        }
    }

    @Test
    fun `should reject adding existing event`() {
        eventService.addEvent(adminUser.telegramId, "EventName")

        assertThrows<EventAlreadyExistsException> { eventService.addEvent(adminUser.telegramId, "EventName") }
    }

    @Test
    fun `getUsers should return list of user names for event`() {
        eventService.addEvent(adminUser.telegramId, "EventName")

        eventService.addUserToEvent(regularUser.telegramId, "EventName")
        eventService.addUserToEvent(adminUser.telegramId, "EventName")

        val userNames = eventService.getUsers("EventName")
        assert(userNames.size == 2)
        assert(userNames.containsAll(listOf(adminUser.userName, regularUser.userName)))
    }

    @Test
    fun `addUserToEvent should throw NoEventFoundException if event doesn't exist`() {
        val unknownEventName = "UnknownEvent"
        assertThrows<NoEventFoundException> { eventService.addUserToEvent(adminUser.telegramId, unknownEventName) }
    }

    @Test
    fun `addUserToEvent should throw NoUserFoundException if user doesn't exist`() {
        val unknownUserTelegramId = "unknown"
        eventService.addEvent(adminUser.telegramId, "EventName")        
        assertThrows<NoUserFoundException> { eventService.addUserToEvent(unknownUserTelegramId, "EventName") }
    }

    @Test
    fun `addUserToEvent should add user to event`() {
        eventService.addEvent(adminUser.telegramId, "EventName")
        eventService.addUserToEvent(regularUser.telegramId, "EventName")

        assert(eventService.getUsers("EventName").contains(regularUser.userName))
    }

    @Test
    fun `deleteUserFromEvent should remove user from event`() {
        eventService.addEvent(adminUser.telegramId, "EventName")
        eventService.addUserToEvent(regularUser.telegramId, "EventName")

        eventService.deleteUserFromEvent(regularUser.telegramId, "EventName")
        assert(!eventService.getUsers("EventName").contains(regularUser.userName))
    }

    @Test
    fun `deleteUserFromEvent should throw ParticipationInEventNotFoundException if user not found`() {
        eventService.addEvent(adminUser.telegramId, "EventName")
        eventService.addUserToEvent(regularUser.telegramId, "EventName")

        assertThrows<ParticipationInEventNotFoundException> {
            eventService.deleteUserFromEvent(adminUser.telegramId, "EventName")
        }
    }
}
