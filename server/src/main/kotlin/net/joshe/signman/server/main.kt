package net.joshe.signman.server

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.counted
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.joshe.signman.api.dnssdService
import net.joshe.signman.api.dnssdUuidKey
import net.joshe.signman.zeroconf.ServicePublisher
import net.joshe.signman.zeroconf.avahi.AvahiPublisherBuilder
import org.slf4j.simple.SimpleLogger
import java.io.File
import java.io.OutputStream
import java.lang.Integer.min
import kotlin.math.max
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SignmanServer : SuspendingCliktCommand() {
    init {
        context { terminal = Terminal(ansiLevel = AnsiLevel.NONE) }
    }

    private val configPath by option("-c", "--conf", help="Path to configuration file")
        .file().default(File("/etc/signman.conf"))
    private val quieter by option("-q", "--quiet", help="Lower the output verbosity level").counted()
    private val louder by option("-v", "--verbose", help="Raise the output verbosity level").counted()
    private val noPublish by option("--no-publish", help="Don't publish the server via zeroconf").flag(default = false)

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

    private fun loadConfig() = configPath.inputStream().use(Config::load)

    private fun loadUuid(config: Config): Uuid {
        val file = File(config.server.directory, "uuid.txt")
        return try {
            Uuid.parse(file.readLines()[0])
        } catch (_: Exception) {
            Uuid.random().also { file.writeText(it.toHexDashString()) }
        }
    }

    private suspend fun loadState(config: Config): Pair<State, StateFlow<Cacheable>> {
        val file = File(config.server.directory, "state.json")
        val renderer = Renderer(config, config.driver?.sign?.getInstance(config))
        val scope = CoroutineScope(currentCoroutineContext())
        var updated: MutableStateFlow<Cacheable>? = null

        val fn: State.(State.Snapshot) -> Unit = { snap ->
            writeFile(file) { store(it) }
            updated?.let { updated ->
                scope.launch { updated.emit(Cacheable.create(config, snap, renderer)) }
            }
        }
        val state = try {
            file.inputStream().use { input ->
                State.load(config.sign.color, input, fn)
            }
        } catch (_: Exception) {
            State.initialize(config.sign.color, onUpdate = fn)
        }
        updated = MutableStateFlow(Cacheable.create(config, state.snapshot, renderer))

        return Pair(state, updated.asStateFlow())
    }

    private fun loadAuth(config: Config) = Auth.load(config.auth)

    private fun createPublisher(config: Config, uuid: Uuid)
    = if (!noPublish)
        AvahiPublisherBuilder(Dispatchers.IO, name = config.name, type = dnssdService,
            port = config.server.port, params = mapOf(dnssdUuidKey to uuid.toString())).build()
    else
        object : ServicePublisher {
            override fun start() {}
            override suspend fun stop() {}
        }

    private fun writeFile(file: File, body: (OutputStream) -> Unit) {
        val tmp = File.createTempFile("tmp", null, file.absoluteFile.parentFile)
        tmp.outputStream().use(body)
        tmp.renameTo(file)
    }

    override suspend fun run() {
        setupLogging()
        val config = loadConfig()
        val uuid = loadUuid(config)
        val auth = loadAuth(config)
        val publisher = createPublisher(config, uuid)
        val (state, updates) = loadState(config)
        Server(config, state, auth, uuid, publisher, updates, Dispatchers.IO).run()
    }
}

suspend fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    check(java.awt.GraphicsEnvironment.isHeadless())

    SignmanServer().main(args)
}
