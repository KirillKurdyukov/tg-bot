package ru.itmo.tgbot.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
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

class EventServiceTest {

    companion object {
        private val adminUser = User(1L, "1", "test", null, Role.ADMIN)
        private val regularUser = User(id = 2L, telegramId = "2", userName = "user", event = null, role = Role.REGULAR)
        val newEvent = Event(0L, "NewEvent", mutableListOf())
        val newEventName = "NewEvent"
    }

    private val userRepository = mockk<UserRepository>()
    private val eventRepository = mockk<EventRepository>()
    private val eventService = EventService(eventRepository, userRepository)


    @BeforeEach
    fun reset() {
        clearMocks(userRepository, eventRepository)

        every { userRepository.findUserByTelegramId(adminUser.telegramId) } returns adminUser
        every { userRepository.findUserByTelegramId(regularUser.telegramId) } returns regularUser
    }

    @Test
    fun `should add new event`() {
        every { eventRepository.save<Event>(any()) } returns newEvent

        eventService.addEvent(adminUser.telegramId, "NewEvent")

        verify(exactly = 1) { eventRepository.save<Event>(match { it.name == "NewEvent" }) }
    }

    @Test
    fun `addEvent should throw NoAdminPermissionException if user is not admin`() {
        assertThrows<NoAdminPermissionException> {
            eventService.addEvent(regularUser.telegramId, "EventName")
        }
    }

    @Test
    fun `should reject adding existing event`() {
        every { eventRepository.save(any()) } throws DataIntegrityViolationException("error")

        assertThrows<EventAlreadyExistsException> { eventService.addEvent(adminUser.telegramId, newEvent.name) }
    }

    @Test
    fun `getUsers should return list of user names for event`() {
        val eventName = "ExistingEvent"
        val event = Event(name = eventName, users = mutableListOf(adminUser, regularUser))

        every { eventRepository.findEventByName(eventName) } returns event

        val userNames = eventService.getUsers(eventName)

        assert(userNames.size == 2)
        assert(userNames.containsAll(listOf(adminUser.userName, regularUser.userName)))
    }

    @Test
    fun `addUserToEvent should throw NoEventFoundException if event doesn't exist`() {
        val eventName = "UnknownEvent"
        every { eventRepository.findEventByName(eventName) } returns null
        assertThrows<NoEventFoundException> { eventService.addUserToEvent(adminUser.telegramId, eventName) }
    }

    @Test
    fun `addUserToEvent should throw NoUserFoundException if user doesn't exist`() {
        val unknownUserTelegramId = "unknown"

        every { eventRepository.findEventByName(newEventName) } returns newEvent
        every { userRepository.findUserByTelegramId(unknownUserTelegramId) } returns null

        assertThrows<NoUserFoundException> { eventService.addUserToEvent(unknownUserTelegramId, newEventName) }
    }

    @Test
    fun `addUserToEvent should add user to event`() {
        every { eventRepository.findEventByName(newEventName) } returns newEvent

        eventService.addUserToEvent(regularUser.telegramId, newEventName)

        assert(newEvent.users.contains(regularUser))
    }

    @Test
    fun `deleteUserFromEvent should remove user from event`() {
        val eventName = "ExistingEvent"
        val event = Event(name = eventName, users = mutableListOf(adminUser))

        every { eventRepository.findEventByName(eventName) } returns event

        eventService.deleteUserFromEvent(adminUser.telegramId, eventName)

        assert(!event.users.contains(adminUser))
    }

    @Test
    fun `deleteUserFromEvent should throw ParticipationInEventNotFoundException if user not found`() {
        val eventName = "ExistingEvent"
        val event = Event(name = eventName, users = mutableListOf(regularUser))

        every { eventRepository.findEventByName(eventName) } returns event

        assertThrows<ParticipationInEventNotFoundException> {
            eventService.deleteUserFromEvent(adminUser.telegramId, eventName)
        }
    }
}