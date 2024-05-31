package ru.itmo.tgbot.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.FetchType
import ru.itmo.tgbot.exception.ParticipationInEventNotFoundException

typealias EventId = Long

@Entity
@Table(name = "events", indexes = [Index(name = "idx_event_name", columnList = "name")])
class Event(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: EventId = 0L,
    @Column(nullable = false, unique = true)
    val name: String,
    @OneToMany(mappedBy = "event", fetch = FetchType.EAGER)
    val users: MutableList<User>,
) {
    fun addUser(user: User) {
        users.add(user)
        user.event = this
    }

    fun removeUser(user: User) {
        val removed = users.removeIf { it.id == user.id }
        if (!removed) throw ParticipationInEventNotFoundException()
        user.event = null
    }
}
