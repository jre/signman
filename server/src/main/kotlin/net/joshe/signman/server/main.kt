package net.joshe.signman.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.counted
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import org.slf4j.simple.SimpleLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.Integer.min
import kotlin.math.max
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SignmanServer : CliktCommand() {
    private val configPath by option("-c", "--conf", help="Path to configuration file")
        .file().default(File("/etc/signman.conf"))
    private val quieter by option("-q", "--quiet", help="Lower the output verbosity level").counted()
    private val louder by option("-v", "--verbose", help="Raise the output verbosity level").counted()
    private val dumpAvahiService by option(help="Output an Avahi service file and exit").flag()

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

    private fun loadConfig() = Config.load(FileInputStream(configPath))

    private fun loadUuid(config: Config): Uuid {
        val file = File(config.server.directory, "uuid.txt")
        return try {
            Uuid.parse(file.readLines()[0])
        } catch (_: Exception) {
            Uuid.random().also { file.writeText(it.toHexDashString()) }
        }
    }

    private fun loadState(config: Config): State {
        val file = File(config.server.directory, "state.json")
        val renderer = Renderer(config.sign)
        return try {
            State.load(FileInputStream(file), renderer) { writeFile(file) { store(it) } }
        } catch (_: Exception) {
            State.initialize(renderer) { writeFile(file) { store(it) } }
        }
    }

    private fun loadAuth(config: Config) = Auth.load(config.auth)

    private fun writeFile(file: File, body: (OutputStream) -> Unit) {
        val tmp = File.createTempFile("tmp", null, file.absoluteFile.parentFile)
        body(FileOutputStream(tmp))
        tmp.renameTo(file)
    }

    override fun run() {
        setupLogging()
        val config = loadConfig()
        val uuid = loadUuid(config)
        if (dumpAvahiService) {
            AvahiService(config, uuid).store(System.out)
            return
        }
        val state = loadState(config)
        val auth = loadAuth(config)
        Server(config, state, auth, uuid).run()
    }
}

fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    check(java.awt.GraphicsEnvironment.isHeadless())

    SignmanServer().main(args)
}
