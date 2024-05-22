package ru.itmo.tgbot.utils

import java.util.logging.Logger

interface WithLogging
inline fun<reified T: WithLogging> T.logger(): Logger = Logger.getLogger(T::class.java.name)