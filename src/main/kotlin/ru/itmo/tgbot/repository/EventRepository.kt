package ru.itmo.tgbot.repository

import org.springframework.data.repository.CrudRepository
import ru.itmo.tgbot.model.Event
import ru.itmo.tgbot.model.EventId

interface EventRepository: CrudRepository<Event, EventId> {
    fun findEventByName(name: String): Event?
}