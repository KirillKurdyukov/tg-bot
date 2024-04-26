package ru.itmo.tgbot.model

import jakarta.persistence.*
import ru.itmo.tgbot.exception.user.ParticipationInEventNotFoundException

typealias EventId = Long

@Entity
@Table(indexes = [Index(name = "idx_event_name", columnList = "name")])
class Event(
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    val id: EventId = 0L,
    @Column(nullable = false, unique = true)
    val name: String,
    @OneToMany(mappedBy = "event")
    val users: MutableList<User>,
) {
    fun addUser(user: User) {
        users.add(user)
        user.event = this
    }

    fun removeUser(user: User) {
        val removed = users.removeIf {  it.id == user.id }
        if (!removed) throw ParticipationInEventNotFoundException()
        user.event = null
    }
}