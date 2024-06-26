package ru.itmo.tgbot.api

import io.micrometer.core.instrument.MeterRegistry
import mu.KLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.objects.Update
import ru.itmo.tgbot.service.EventService
import ru.itmo.tgbot.exception.EventAlreadyExistsException
import ru.itmo.tgbot.exception.NoAdminPermissionException
import ru.itmo.tgbot.exception.NoEventFoundException
import ru.itmo.tgbot.exception.ParticipationInEventNotFoundException
import ru.itmo.tgbot.exception.AlreadyParticipatingException
import ru.itmo.tgbot.model.Role

@Controller
class TgApiController(
    private val eventService: EventService,
    @Value("\${telegram.bot.token:}") private val token: String,
    private val meterRegistry: MeterRegistry
) : SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    companion object : KLogging() {
        private const val INCORRECT_NUMBER_ARGUMENTS = "Incorrect number of arguments\n\nUsage: "
    }

    private val telegramClient = OkHttpTelegramClient(botToken)

    final override fun getBotToken(): String {
        return token
    }

    override fun getUpdatesConsumer(): LongPollingUpdateConsumer {
        return this
    }

    override fun consume(update: Update) {
        meterRegistry.counter("telegram.request").increment();

        if (needProcessUpdate(update)) {
            val message = update.message
            val user = message.from
            val userTelegramId = user.id.toString()

            if (!eventService.hasUser(userTelegramId)) {
                eventService.addUser(user.userName, userTelegramId)
            }

            val words = message.text.split("\\s+".toRegex())
            val cmd = words[0]
            val args = words.slice(1 until words.size)

            val response = when (cmd) {
                "/start"      -> start()
                "/help"       -> help(userTelegramId)
                "/whoami"     -> whoAmI(userTelegramId)
                "/add_event"  -> addEvent(userTelegramId, args)
                "/check_in"   -> checkIn(userTelegramId, args)
                "/check_out"  -> checkOut(userTelegramId, args)
                "/list_users" -> listUsers(args)
                else          -> unknownCommand()
            }
            val sendMessage = response.chatId(message.chatId).build()

            logger.info("Processed message from user $userTelegramId with cmd $cmd, responding ${sendMessage.text}")
            telegramClient.execute(sendMessage)
        }
    }

    private fun needProcessUpdate(update: Update): Boolean {
        return update.hasMessage() 
            && update.message.isUserMessage
            && update.message.hasText()
    }

    private fun start(): SendMessage.SendMessageBuilder<*, *> {
        val text = "Hi, you are using ✨✨✨super✨✨✨ bot that "
            .plus(" lets you track who came to and left event!\n\n")
            .plus("We suggest using the /help command if this is")
            .plus(" your first time using a bot 📚")
        
        meterRegistry.counter("telegram.response.success").increment();
        return SendMessage
                .builder()
                .text(text)
    }

    private fun help(userTelegramId: String): SendMessage.SendMessageBuilder<*, *> {
        var text = """
            |List of available commands:
            |
            |/start - Show start message
            |/help  - Show help
            |/whoami - Who am I?
            |/check_in <event> - Check in
            |/check_out <event> - Check out
            |/list_users <event> - List users that still participate in event
            |
            |
        """.trimMargin()

        val user = eventService.getUser(userTelegramId)
        if (user.role == Role.ADMIN) {
            text = text.plus("""
                |Admin commands:
                |
                |/add_event <event> - Add event
            """.trimMargin())
        }

        meterRegistry.counter("telegram.response.success").increment();
        return SendMessage
                .builder()
                .text(text)
    }

    private fun whoAmI(userTelegramId: String): SendMessage.SendMessageBuilder<*, *> {
        val user = eventService.getUser(userTelegramId)
        var text = """
            |id: `${user.telegramId}`
            |username: @${user.name}
        """.trimMargin()
        
        if (user.role == Role.ADMIN) {
            text = text.plus("\n\nyou are admin\\!")
        }

        meterRegistry.counter("telegram.response.success").increment();
        return SendMessage
                .builder()
                .parseMode(ParseMode.MARKDOWNV2)
                .text(text)
    }

    private fun checkIn(userTelegramId: String, args: List<String>): SendMessage.SendMessageBuilder<*, *> {
        if (args.size != 1) {
            val text = INCORRECT_NUMBER_ARGUMENTS
                .plus("/check_in <event>")

            meterRegistry.counter("telegram.response.error").increment();
            return SendMessage.builder().text(text)
        }

        val eventName = args[0]
        try {
            eventService.addUserToEvent(userTelegramId, eventName)

            meterRegistry.counter("telegram.response.success").increment();
            return SendMessage.builder().text("Successfully checked-in!")
        } catch (e: NoEventFoundException) {
            meterRegistry.counter("telegram.response.error").increment();
            return SendMessage.builder().text("Event doesn't exist")
        } catch (e: AlreadyParticipatingException) {
            meterRegistry.counter("telegram.response.error").increment();
            return SendMessage.builder().text("Already participating in ${e.eventName}")
        }
    }

    private fun checkOut(userTelegramId: String, args: List<String>): SendMessage.SendMessageBuilder<*, *> {
        if (args.size != 1) {
            val text = INCORRECT_NUMBER_ARGUMENTS
                .plus("/check_out <event>")

            meterRegistry.counter("telegram.response.error").increment();
            return SendMessage.builder().text(text)
        }

        val eventName = args[0]
        try {
            eventService.deleteUserFromEvent(userTelegramId, eventName)

            meterRegistry.counter("telegram.response.success").increment();
            return SendMessage.builder().text("Successfully checked-out!")
        } catch (e: NoEventFoundException) {
            meterRegistry.counter("telegram.response.error").increment();
            return SendMessage.builder().text("Event doesn't exist")
        } catch (e: ParticipationInEventNotFoundException) {
            meterRegistry.counter("telegram.response.error").increment();
            return SendMessage.builder().text("You didn't participate in this event")
        }
    }

    private fun listUsers(args: List<String>): SendMessage.SendMessageBuilder<*, *> {
        if (args.size != 1) {
            val text = INCORRECT_NUMBER_ARGUMENTS
                .plus("/list_users <event>")

            meterRegistry.counter("telegram.response.error").increment();
            return SendMessage
                .builder()
                .text(text)
        }

        val eventName = args[0]
        try {
            val users = eventService.getUsers(eventName)
            return if (users.isNotEmpty()) {
                meterRegistry.counter("telegram.response.success").increment();
                SendMessage
                    .builder()
                    .text(users.joinToString("\n"))
            } else {
                meterRegistry.counter("telegram.response.success").increment();
                SendMessage
                    .builder()
                    .text("No users")
            }
        } catch (e: NoEventFoundException) {
            meterRegistry.counter("telegram.response.error").increment();
            return SendMessage
                .builder()
                .text("Event doesn't exist")
        }
    }

    private fun addEvent(userTelegramId: String, args: List<String>): SendMessage.SendMessageBuilder<*, *> {
        if (args.size != 1) {
            val text = INCORRECT_NUMBER_ARGUMENTS
                .plus("/add_event <event>")

            meterRegistry.counter("telegram.response.error").increment();
            return SendMessage
                .builder()
                .text(text)
        }

        val eventName = args[0]
        try {
            eventService.addEvent(userTelegramId, eventName)

            meterRegistry.counter("telegram.response.success").increment();
            return SendMessage
                .builder()
                .text("Event is successfully added!")
        } catch (e: NoAdminPermissionException) {
            meterRegistry.counter("telegram.response.error").increment();
            return SendMessage
                .builder()
                .text("You don't have enough permissions")
        } catch (e: EventAlreadyExistsException) {
            meterRegistry.counter("telegram.response.error").increment();
            return SendMessage
                .builder()
                .text("Event already exist")
        }
    }

    private fun unknownCommand(): SendMessage.SendMessageBuilder<*, *> {
        meterRegistry.counter("telegram.response.error").increment();

        return SendMessage
                .builder()
                .text("Unknown command: try to use /help\n")
    }
}
