package net.joshe.signman.server

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.event.EventConstants
import org.slf4j.event.Level
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.helpers.Reporter
import org.slf4j.simple.SimpleLogger
import org.slf4j.simple.SimpleServiceProvider
import org.slf4j.spi.SLF4JServiceProvider
import java.io.OutputStream
import java.io.PrintStream
import kotlin.math.max

private val levels = listOf(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR)

fun setupLogging(config: Config, verbosity: Int) {
    val level = max(levels.indexOf(Level.INFO) - verbosity, 0).let { idx ->
        if (idx > levels.lastIndex) null else levels[idx]
    }
    System.setProperty(Reporter.SLF4J_INTERNAL_VERBOSITY_KEY, "warn")

    when (config.server) {
        is Config.SystemdServerConfig -> {
            System.setProperty(LoggerFactory.PROVIDER_PROPERTY_KEY, SystemdLogger.Provider::class.java.name)
            SystemdLogger.curLevel = level?.toInt() ?: Int.MAX_VALUE
            System.setErr(PrintStream(object : OutputStream() { override fun write(p: Int) {} }))
            System.setOut(System.err)
        }

        is Config.StandaloneServerConfig -> {
            System.setProperty(LoggerFactory.PROVIDER_PROPERTY_KEY, SimpleServiceProvider::class.java.name)
            System.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "false")
            System.setProperty(SimpleLogger.SHOW_LOG_NAME_KEY, "false")
            System.setProperty(SimpleLogger.SHOW_SHORT_LOG_NAME_KEY, "true")
            System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, level?.toString() ?: "off")
            System.setProperty(SimpleLogger.LEVEL_IN_BRACKETS_KEY, "true")
            System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true")
            System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            if (config.server.log != null)
                System.setProperty(SimpleLogger.LOG_FILE_KEY, config.server.log.absolutePath)
        }
    }

    val log = LoggerFactory.getLogger("UncaughtException")
    Thread.setDefaultUncaughtExceptionHandler { thr, exc ->
        log.error("Exception in thread ${thr.name}", exc)
    }
}

class SystemdLogger(name: String?) : LegacyAbstractLogger() {
    init { this.name = name }
    val shortName = name?.split('.')?.last()

    override fun isTraceEnabled() = EventConstants.TRACE_INT >= curLevel
    override fun isDebugEnabled() = EventConstants.DEBUG_INT >= curLevel
    override fun isInfoEnabled() = EventConstants.INFO_INT >= curLevel
    override fun isWarnEnabled() = EventConstants.WARN_INT >= curLevel
    override fun isErrorEnabled() = EventConstants.ERROR_INT >= curLevel
    override fun getFullyQualifiedCallerName() = null

    override fun handleNormalizedLoggingCall(level: Level?, marker: Marker?,
                                             template: String?, args: Array<out Any?>?, exc: Throwable?) {
        val prefix = when (level) {
            Level.DEBUG -> "<7>"
            Level.INFO -> "<6>"
            Level.WARN -> "<4>"
            Level.ERROR -> "<3>"
            else -> return
        }
        val msg = (shortName?.let { "$it - " } ?: "") +
                (marker?.let { "$it " } ?: "") +
                MessageFormatter.basicArrayFormat(template, args) + "\n" +
                (exc?.stackTraceToString() ?: "")
        val prefixed = msg.trimEnd('\n').lineSequence().map { prefix + it }.joinToString("\n")
        synchronized(output) {
            output.println(prefixed)
        }
    }

    class Factory : ILoggerFactory {
        private val cache = mutableMapOf<String?, Logger>()
        override fun getLogger(name: String?) = synchronized(cache) {
            cache.getOrPut(name) { SystemdLogger(name) }
        }
    }

    class Provider : SLF4JServiceProvider {
        private var loggerFactory: ILoggerFactory? = null
        private val markerFactory = BasicMarkerFactory()
        private val mocAdapter = NOPMDCAdapter()

        override fun getLoggerFactory() = loggerFactory
        override fun getMarkerFactory() = markerFactory
        override fun getMDCAdapter() = mocAdapter
        override fun getRequestedApiVersion() = "2.0.99"

        override fun initialize() {
             loggerFactory = Factory()
        }
    }

    companion object {
        var curLevel: Int = Int.MAX_VALUE
        val output = System.err!!
    }
}
