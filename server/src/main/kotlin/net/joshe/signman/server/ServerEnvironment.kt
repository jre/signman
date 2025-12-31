package net.joshe.signman.server

import info.faljse.SDNotify.SDNotify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.joshe.signman.zeroconf.ServicePublisher
import org.jetbrains.annotations.TestOnly
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel

sealed class ServerEnvironment {
    var publisher: ServicePublisher? = null
    abstract val port: Int

    open fun ready() {
        publisher?.start()
        startingJob?.cancel()
        startingJob = null
    }

    open suspend fun stopping() {
        publisher?.stop()
    }

    companion object {
        fun create(config: Config) = when (config.server) {
            is Config.StandaloneServerConfig -> Standalone(config.server)
            is Config.SystemdServerConfig -> Systemd()
        }

        @TestOnly
        fun createTest(port: Int,
                       readyFn: (() -> Unit)?,
                       stoppingFn: (suspend () -> Unit)?): ServerEnvironment =
            Testing(port, readyFn, stoppingFn)

        private fun inheritedPort() =
            ((System.inheritedChannel() as ServerSocketChannel).localAddress as InetSocketAddress).port

        private var startingJob: Job? = null

        fun starting() {
	    if (System.getenv("NOTIFY_SOCKET") != null)
                startingJob = CoroutineScope(Dispatchers.IO).launch {
                    while (true) {
                        SDNotify.sendRaw("EXTEND_TIMEOUT_USEC=3000000")
                        delay(1000)
                    }
                }
        }
    }

    private class Testing(override val port: Int,
                          private val readyFn: (() -> Unit)?,
                          private val stoppingFn: (suspend () -> Unit)?) : ServerEnvironment() {
        override fun ready() { readyFn?.invoke() }
        override suspend fun stopping() { stoppingFn?.invoke() }
    }

    private class Standalone(config: Config.StandaloneServerConfig) : ServerEnvironment() {
        override val port = config.port
    }

    private class Systemd : ServerEnvironment() {
        init {
	    check(startingJob != null) { "Systemd notify socket not found" }
	}

        override val port = inheritedPort()

        override fun ready() {
            SDNotify.sendNotify()
            super.ready()
        }

        override suspend fun stopping() {
            SDNotify.sendStopping()
            super.stopping()
        }
    }
}
