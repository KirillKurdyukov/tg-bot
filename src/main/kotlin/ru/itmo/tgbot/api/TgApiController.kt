package ru.itmo.tgbot.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.itmo.tgbot.service.EventService
import ru.itmo.tgbot.exception.EventAlreadyExistsException
import ru.itmo.tgbot.exception.NoAdminPermissionException
import ru.itmo.tgbot.exception.NoEventFoundException
import ru.itmo.tgbot.exception.ParticipationInEventNotFoundException
import ru.itmo.tgbot.model.Role

@Controller
class TgApiController(
    private val eventService: EventService,
    @Value("\${telegram.bot.token:}") private val token: String,
) : SpringLongPollingBot
  , LongPollingSingleThreadUpdateConsumer
{

    companion object {
        private val INCORRECT_NUMBER_ARGUMENTS = "Incorrect number of arguments\n\nUsage: "
    }

    private val telegramClient = OkHttpTelegramClient(getBotToken())

    override fun getBotToken(): String {
        return token
    }

    override fun getUpdatesConsumer(): LongPollingUpdateConsumer {
        return this
    }

    override fun consume(update: Update) {
        if (needProcessUpdate(update)) {
            val message = update.getMessage()
            val user = message.from
            val userTelegramId = user.id.toString()

            if (!eventService.hasUser(userTelegramId)) {
                eventService.addUser(user.userName, userTelegramId)
            }
            
            val words = message.text.split("\\s+".toRegex())
            val cmd = words[0]
            val args = words.slice(1..words.size-1)

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
            
            telegramClient.execute(
                response
                    .chatId(message.chatId)
                    .build()
            )
        }
    }

    private fun needProcessUpdate(update: Update): Boolean {
        return update.hasMessage() 
            && update.getMessage().isUserMessage()
            && update.getMessage().hasText()
    }

    private fun start(): SendMessage.SendMessageBuilder<*, *> {
        val text = "Hi, you are using ✨✨✨super✨✨✨ bot that "
            .plus(" lets you track who came to and left event!\n\n")
            .plus("We suggest using the /help command if this is")
            .plus(" your first time using a bot 📚")
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

        val user = eventService.getUser(userTelegramId);
        if (user.role == Role.ADMIN) {
            text = text.plus("""
                |Admin commands:
                |
                |/add_event <event> - Add event
            """.trimMargin())
        }

        return SendMessage
                .builder()
                .text(text)
    }

    private fun whoAmI(userTelegramId: String): SendMessage.SendMessageBuilder<*, *> {
        val user = eventService.getUser(userTelegramId);
        var text = """
            |id: `${user.telegramId}`
            |username: @${user.userName}
        """.trimMargin()
        
        if (user.role == Role.ADMIN) {
            text = text.plus("\n\nyou are admin\\!")
        }
        
        return SendMessage
                .builder()
                .parseMode(ParseMode.MARKDOWNV2)
                .text(text)
    }

    private fun checkIn(userTelegramId: String, args: List<String>): SendMessage.SendMessageBuilder<*, *> {
        if (args.size != 1) {
            val text = INCORRECT_NUMBER_ARGUMENTS
                .plus("/check_in <event>")

            return SendMessage
                .builder()
                .text(text)
        }

        val eventName = args[0]
        try {
            eventService.addUserToEvent(userTelegramId, eventName)
            return SendMessage
                .builder()
                .text("Successfully checked-in!")
        } catch (e: NoEventFoundException) {
            return SendMessage
                .builder()
                .text("Event doesn't exist")
        }
    }

    private fun checkOut(userTelegramId: String, args: List<String>): SendMessage.SendMessageBuilder<*, *> {
        if (args.size != 1) {
            val text = INCORRECT_NUMBER_ARGUMENTS
                .plus("/check_out <event>")

            return SendMessage
                .builder()
                .text(text)
        }

        val eventName = args[0]
        try {
            eventService.deleteUserFromEvent(userTelegramId, eventName)
            return SendMessage
                .builder()
                .text("Successfully checked-out!")
        } catch (e: NoEventFoundException) {
            return SendMessage
                .builder()
                .text("Event doesn't exist")
        } catch (e: ParticipationInEventNotFoundException) {
            return SendMessage
                .builder()
                .text("You didn't participate in this event")
        }
    }

    private fun listUsers(args: List<String>): SendMessage.SendMessageBuilder<*, *> {
        if (args.size != 1) {
            val text = INCORRECT_NUMBER_ARGUMENTS
                .plus("/list_users <event>")

            return SendMessage
                .builder()
                .text(text)
        }

        val eventName = args[0]
        try {
            val users = eventService.getUsers(eventName)
            if (!users.isEmpty()) {
                return SendMessage
                    .builder()
                    .text(users.joinToString("\n"))
            } else {
                return SendMessage
                    .builder()
                    .text("No users")
            }
        } catch (e: NoEventFoundException) {
            return SendMessage
                .builder()
                .text("Event doesn't exist")
        }
    }

    private fun addEvent(userTelegramId: String, args: List<String>): SendMessage.SendMessageBuilder<*, *> {
        if (args.size != 1) {
            val text = INCORRECT_NUMBER_ARGUMENTS
                .plus("/add_event <event>")

            return SendMessage
                .builder()
                .text(text)
        }

        val eventName = args[0]
        try {
            eventService.addEvent(userTelegramId, eventName)
            return SendMessage
                .builder()
                .text("Event is successfully added!")
        } catch (e: NoAdminPermissionException) {
            return SendMessage
                .builder()
                .text("You don't have enough permissions")
        } catch (e: EventAlreadyExistsException) {
            return SendMessage
                .builder()
                .text("Event already exist")
        }
    }

    private fun unknownCommand(): SendMessage.SendMessageBuilder<*, *> {
        return SendMessage
                .builder()
                .text("Unknown command: try to use /help\n")
    }
}
