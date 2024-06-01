package ru.itmo.tgbot.exception

class EventAlreadyExistsException(cause: Throwable): RuntimeException(cause)

class NoEventFoundException: RuntimeException()

class NoAdminPermissionException: RuntimeException()

class NoUserFoundException: RuntimeException()

class ParticipationInEventNotFoundException: RuntimeException()

class UserAlreadyExistsException(cause: Throwable): RuntimeException(cause)

class AlreadyParticipatingException(val eventName: String): RuntimeException()
