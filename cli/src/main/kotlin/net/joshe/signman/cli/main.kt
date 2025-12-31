@file:OptIn(ExperimentalUuidApi::class)

package net.joshe.signman.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.counted
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import io.ktor.client.engine.java.Java
import io.ktor.http.Url
import io.ktor.http.parseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.joshe.signman.api.ColorType
import net.joshe.signman.api.IndexedColor
import net.joshe.signman.api.QueryResponse
import net.joshe.signman.api.RGB
import net.joshe.signman.api.RGBColor
import net.joshe.signman.api.StatusResponse
import net.joshe.signman.api.UpdateRequest
import net.joshe.signman.api.dnssdService
import net.joshe.signman.client.AuthStore
import net.joshe.signman.client.Client
import net.joshe.signman.client.JvmResolver
import net.joshe.signman.client.HostCache
import net.joshe.signman.client.getUserStateDir
import net.joshe.signman.client.writeFile
import net.joshe.signman.zeroconf.avahi.AvahiBrowserBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.slf4j.simple.SimpleLogger
import java.io.FileNotFoundException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private abstract class BaseCommand : SuspendingCliktCommand() {
    init {
        context { terminal = Terminal(ansiLevel = AnsiLevel.NONE) }
    }

    override val helpTags = SignmanCli.cmdAliases[commandName]?.let { aliases ->
        mapOf("aliases" to aliases.joinToString(", "))
    } ?: emptyMap()

    private val quieter by option("-q", "--quiet", help="Lower the output verbosity level").counted()
    private val louder by option("-v", "--verbose", help="Raise the output verbosity level").counted()
    protected val verbosity: Int get() = louder - quieter

    private val stateDir = getUserStateDir("signman")

    protected lateinit var log: Logger
    protected lateinit var hostCache: HostCache
    protected lateinit var client: Client

    protected fun StatusResponse.printTextColorized() {
        val terminal = Terminal()
        val color =TextColors.rgb(fg.rgb.toHexString()) on TextColors.rgb(bg.rgb.toHexString())
        terminal.println((TextStyles.bold + (color))(text))
    }

    private fun setupLogging() {
        val levels = listOf("trace", "debug", "info", "warn", "error", "off")
        val level = min(max(levels.indexOf("warn") - verbosity, 0), levels.lastIndex)
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

    @OptIn(ExperimentalSerializationApi::class)
    private fun loadHostCache(): HostCache {
        val file = File(stateDir, "hosts")
        val hc = if (file.exists())
            try {
                file.inputStream().use { Json.decodeFromStream(it) }
            } catch (e: Exception) {
                log.error("failed to read host cache {}", file, e)
                HostCache()
            }
        else
            HostCache()
        hc.onUpdate = {
            writeFile(file) { output ->
                Json.encodeToStream(hc, output)
            }
        }
        return hc
    }

    protected fun serverString(query: QueryResponse) = serverString(query.uuid, query.name)

    protected fun serverString(entry: Map.Entry<Uuid,String?>) = serverString(entry.key, entry.value)

    protected suspend fun serverString(uuid: Uuid) = serverString(uuid, hostCache.names()[uuid])

    protected fun serverString(uuid: Uuid, name: String?) = when {
        verbosity <= 0 -> name ?: uuid.toString()
        name == null -> uuid.toString()
        else -> "$uuid: $name"
    }

    protected fun output(message: String) {
        if (verbosity >= 0)
            echo(message)
    }

    override suspend fun run() {
        setupLogging()
        log = LoggerFactory.getLogger(this::class.java)!!
        hostCache = loadHostCache()
        client = Client.create(Java, loadAuthStore(), JvmResolver(), hostCache)
        runSub()
    }

    abstract suspend fun runSub()
}

private abstract class ApiCommand : BaseCommand() {
    private sealed class Selector {
        data class Url(val url: io.ktor.http.Url): Selector()
        data class Name(val name: String): Selector()
    }

    private val selector: Selector? by mutuallyExclusiveOptions<Selector>(
        option("-u", "--url", help="Server URL")
            .convert { Selector.Url(parseUrl(it)!!) },
        option("-n", "--name", help="Name of previously-seen server")
            .convert { Selector.Name(it) },
    ).single()

    protected fun getUrl() = (selector as? Selector.Url)?.url

    private suspend fun getUuid() = selector.let { selector ->
        when (selector) {
            is Selector.Url -> queryUrl(selector.url)
            is Selector.Name -> queryName(selector.name)
            null -> queryDefault()
        }
    }

    private suspend fun queryUrl(url: Url) = client.checkUrl(url)?.uuid
        ?: throw PrintMessage("Failed to query server at $url", statusCode = 1, printError = true)

    private suspend fun queryName(query: String): Uuid = hostCache.names().let { names ->
        try {
            val uuid = Uuid.parse(query)
            if (uuid in names)
                return uuid
        } catch (_: Exception) {}

        val lowName = query.lowercase()
        for (fn in listOf<(String?) -> Boolean>(
            { query == it }, { lowName == it }, { it != null && query in it }, { it != null && lowName in it })) {
            val matches = names.filterValues(fn)
            if (matches.size == 1)
                return matches.keys.first()
            else if (matches.size > 1)
                throw PrintMessage("Multiple servers match for \"$query\": " +
                        "\"${matches.keys.sorted().joinToString("\", \"")}\"")
        }
        throw PrintMessage("No servers match \"$query\"")
    }

    private suspend fun queryDefault() = hostCache.names().let { names ->
        if (names.size == 1)
            names.keys.first()
        else
            throw PrintMessage("Multiple servers: " +
                    "\"${names.keys.sorted().joinToString("\", \"")}\"")
    }

    override suspend fun runSub() = runSub(getUuid())

    abstract suspend fun runSub(uuid: Uuid)
}

private abstract class AuthenticatedCommand : ApiCommand() {
    protected suspend fun login(client: Client, uuid: Uuid, user: String? = null)
    = Authenticator().login(client, uuid, credentials = getUrl()?.let(::Credentials), user = user)
}

private class Browse : BaseCommand() {
    override fun help(context: Context) = "Find and save servers on the local network"

    override suspend fun runSub() {
        val browser = AvahiBrowserBuilder(Dispatchers.IO, dnssdService).build()
        browser.start()
        coroutineScope {
            launch {
                browser.state.collect { services ->
                    if (!services.isNullOrEmpty()) {
                        val found = client.checkServices(services)
                        if (!found.isEmpty()) {
                            output("Saving ${found.size} server(s):")
                            found.forEach { echo(serverString(it)) }
                            currentCoroutineContext().job.cancel()
                            return@collect
                        }
                    }
                    if (services != null)
                        echo("No servers found. Waiting...")
                }
            }
        }
    }
}

private class Saved : BaseCommand() {
    override fun help(context: Context) = "Show saved server list"

    override suspend fun runSub() {
        val names = hostCache.names()
        if (names.isEmpty())
            output("No saved servers")
        else
            names.forEach { echo(serverString(it)) }
    }
}

private class Forget : ApiCommand() {
    override fun help(context: Context) = "Forget a saved server"

    override suspend fun runSub(uuid: Uuid) {
        hostCache.forget(uuid)
        output("Forgot ${serverString(uuid)}")
    }
}

private class Status : ApiCommand() {
    override fun help(context: Context) = "Forget a saved server"

    override suspend fun runSub(uuid: Uuid) {
        output("${serverString(uuid)}:")
        client.status(uuid).printTextColorized()
    }
}

private class Login : AuthenticatedCommand() {
    override fun help(context: Context) = "Log in to a server"

    private val username by argument().optional()

    override suspend fun runSub(uuid: Uuid) {
        val label = serverString(uuid)
        output("Logging in to $label")
        if (login(client, uuid, username))
            output("Success!")
        else
            throw PrintMessage("Failed to log in to $label", statusCode = 1, printError = true)
    }
}

private class Clear : AuthenticatedCommand() {
    override fun help(context: Context) = "Clear a server sign display"

    override suspend fun runSub(uuid: Uuid) {
        val label = serverString(uuid)
        output("Clearing $label...")
        client.clear(uuid)
        output("Success!")
    }
}

private class Update : AuthenticatedCommand() {
    override fun help(context: Context) = "Update a server sign display"

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

    override suspend fun runSub(uuid: Uuid) {
        if (!login(client, uuid))
            throw PrintMessage("Failed to log in to ${serverString(uuid)}", statusCode = 1, printError = true)
        output("Updating ${serverString(uuid)}...")
        val info = client.status(uuid)
        val fgColor = parseColor(info, fg)
        val bgColor = parseColor(info, bg)
        client.update(uuid, UpdateRequest(text = text, fg = fgColor, bg = bgColor))
        client.status(uuid).printTextColorized()
    }
}

private class SignmanCli : SuspendingCliktCommand() {
    override fun help(context: Context) = "Command-line client for Signman servers"

    override val invokeWithoutSubcommand = true

    override fun aliases() = cmdAliases.flatMap { (cmd, aliases) ->
        aliases.map { Pair(it, listOf(cmd)) }
    }.toMap()

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null)
            registeredSubcommands().first { it.commandName == "status" }.main(emptyList())
    }

    companion object {
        val cmdAliases = mapOf(
            "browse" to listOf("b"),
            "clear" to listOf("c"),
            "login" to listOf("lo"),
            "saved" to listOf("ls"),
            "status" to listOf("s", "st"),
            "update" to listOf("u", "up"))
    }
}

suspend fun main(args: Array<String>) {
    SignmanCli()
        .subcommands(Browse(), Forget(), Saved(), Login(), Status(), Clear(), Update())
        .main(args)
}
