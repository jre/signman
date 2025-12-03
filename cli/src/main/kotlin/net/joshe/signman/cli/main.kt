@file:OptIn(ExperimentalUuidApi::class)

package net.joshe.signman.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.counted
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import io.ktor.client.engine.java.Java
import io.ktor.http.parseUrl
import net.joshe.signman.api.ColorType
import net.joshe.signman.api.IndexedColor
import net.joshe.signman.api.RGB
import net.joshe.signman.api.RGBColor
import net.joshe.signman.api.StatusResponse
import net.joshe.signman.api.UpdateRequest
import net.joshe.signman.client.AuthStore
import net.joshe.signman.client.Client
import net.joshe.signman.client.JvmResolver
import net.joshe.signman.client.HostCache
import net.joshe.signman.client.getUserStateDir
import net.joshe.signman.client.writeFile
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.slf4j.simple.SimpleLogger
import java.io.FileNotFoundException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val clientKey = "client"
private const val uuidKey = "uuid"
private const val credentialsKey = "credentials"

private fun StatusResponse.printTextColorized() {
    val terminal = Terminal()
    val color =TextColors.rgb(fg.rgb.toHexString()) on TextColors.rgb(bg.rgb.toHexString())
    terminal.println((TextStyles.bold + (color))(text))
}

private class SignmanCli : SuspendingCliktCommand() {
    init {
        context { terminal = Terminal(ansiLevel = AnsiLevel.NONE) }
    }

    override val invokeWithoutSubcommand = true
    private val url by option("-u", "--url", help="Server URL").required()
    private val quieter by option("-q", "--quiet", help="Lower the output verbosity level").counted()
    private val louder by option("-v", "--verbose", help="Raise the output verbosity level").counted()

    private val stateDir = getUserStateDir("signman")

    private fun setupLogging() {
        val levels = listOf("trace", "debug", "info", "warn", "error", "off")
        val level = min(max(levels.indexOf("info") + quieter - louder, 0), levels.lastIndex)
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, levels[level])
        System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true")
        System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        System.setProperty(SimpleLogger.LEVEL_IN_BRACKETS_KEY, "true")
        System.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "false")
        System.setProperty(SimpleLogger.SHOW_LOG_NAME_KEY, "false")
        System.setProperty(SimpleLogger.SHOW_SHORT_LOG_NAME_KEY, "true")
    }

    private fun loadAuthStore(): AuthStore {
        val file = File(stateDir, "credentials")
        val auth = JsonFileAuthStore { writeFile(file, "rw-------", it) }
        try {
            file.inputStream().use { auth.load(it) }
        } catch (_: FileNotFoundException) {}
        return auth
    }

    override suspend fun run() {
        setupLogging()
        val url = parseUrl(url)!!
        val auth = loadAuthStore()
        val client = Client.create(Java, auth, JvmResolver(), HostCache())
        val response = client.checkUrl(url)
            ?: throw PrintMessage("Failed to query server at $url", statusCode = 1, printError = true)
        currentContext.findOrSetObject(clientKey) { client }
        currentContext.findOrSetObject(uuidKey) { response.uuid }
        currentContext.findOrSetObject(credentialsKey) { Credentials(url) }
        if (currentContext.invokedSubcommand == null) {
            client.status(response.uuid).printTextColorized()
        }
    }
}

class Status : SuspendingCliktCommand() {
    private val client: Client by requireObject(clientKey)
    private val uuid: Uuid by requireObject(uuidKey)

    override suspend fun run() {
        client.status(uuid).printTextColorized()
    }
}

class Login : SuspendingCliktCommand() {
    private val client: Client by requireObject(clientKey)
    private val uuid: Uuid by requireObject(uuidKey)
    private val credentials: Credentials by requireObject(credentialsKey)
    private val username by argument().optional()

    override suspend fun run() {
        if (Authenticator().login(client, uuid, credentials = credentials, user = username))
            echo("Success!")
        else
            throw PrintMessage("Failed to log in to server", statusCode = 1, printError = true)
    }
}

class Update : SuspendingCliktCommand() {
    private val client: Client by requireObject(clientKey)
    private val uuid: Uuid by requireObject(uuidKey)
    private val credentials: Credentials by requireObject(credentialsKey)
    private val bg by option("-b" ,"--background", help="Background color")
    private val fg by option("-f" ,"--foreground", help="Foreground color")
    private val text by argument()

    private fun colorFromIndex(colors: List<IndexedColor>, indexStr: String) = try {
        val idx = indexStr.toInt()
        colors.getOrNull(idx) ?: throw BadParameterValue("Invalid color index: $idx")
    } catch (_: NumberFormatException) { null }

    private fun colorFromName(colors: List<IndexedColor>, name: String) = name.lowercase().let { nameKey ->
        colors.find { nameKey == it.name.lowercase() }
    }

    private fun colorFromRgb(hexStr: String) = try {
        RGBColor(RGB.fromHexString(hexStr))
    } catch (_: Exception) { null }

    private fun parseColor(info: StatusResponse, str: String?) = str?.let { str ->
        if (info.type == ColorType.INDEXED)
            colorFromIndex(info.colors!!, str)
                ?: colorFromName(info.colors!!, str)
                ?: throw BadParameterValue("Invalid color name: $str")
        else
            colorFromRgb(str)
                ?: throw BadParameterValue("Invalid RGB color hexadecimal: $str")
    }

    override suspend fun run() {
        if (!Authenticator().login(client, uuid, credentials = credentials)) {
            echo("Failed to log in")
            return
        }
        val info = client.status(uuid)
        val fgColor = parseColor(info, fg)
        val bgColor = parseColor(info, bg)
        client.update(uuid, UpdateRequest(text = text, fg = fgColor, bg = bgColor))
        client.status(uuid).printTextColorized()
    }
}

suspend fun main(args: Array<String>) {
    SignmanCli().subcommands(Login(), Status(), Update()).main(args)
}
