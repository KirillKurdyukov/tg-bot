package ru.itmo.tgbot.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "users", indexes = [Index(name = "user_tg_id", columnList = "telegramId")])
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val telegramId: String,

    @Column(nullable = false)
    val name: String,

    @ManyToOne
    @JoinColumn(name = "event_id")
    var event: Event?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role = Role.REGULAR,
)