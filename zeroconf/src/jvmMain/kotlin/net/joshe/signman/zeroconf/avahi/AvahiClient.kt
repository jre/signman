package net.joshe.signman.zeroconf.avahi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.joshe.signman.zeroconf.avahi.interfaces.Server
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBus.NameOwnerChanged
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.slf4j.Logger
import kotlin.coroutines.CoroutineContext

internal sealed class AvahiClient(context: CoroutineContext) {
    protected val scope = CoroutineScope(SupervisorJob() + context)
    protected val mutex = Mutex()
    protected abstract val log: Logger
    protected var dbus: DBusState? = null
    protected var avahi: ServerState? = null

    private val nameOwnerChanged = DBusSigHandler<NameOwnerChanged> { change ->
        scope.launch {
            mutex.withLock {
                avahi?.close()
                if (change.newOwner != null)
                    avahiConnect()
            }
        }
    }

    private val serverStateChanged = DBusSigHandler<Server.StateChanged> { change ->
        val state = AvahiServerState.fromInt(change.state)
        log.debug("avahi server state changed: {}({}) {}", state, change.state, change.error)
        scope.launch {
            mutex.withLock {
                handleServerState(state)
            }
        }
    }

    protected suspend fun dbusConnect() {
        check(mutex.isLocked)
        check(dbus == null)
        val con: DBusConnection
        val handler: AutoCloseable

        try {
            con = DBus.connect()
            handler = DBus.watchName(AVAHI_BUS, nameOwnerChanged)
        } catch (e: DBusException) {
            log.error("failed to connect NameOwnerChanged signal", e)
            return
        }
        log.debug("connected to dbus")

        dbus = object : DBusState {
            override val con = con
            override val handler = handler
            override fun close() {
                avahi?.close()
                handler.close()
                dbus = null
            }
        }

        avahiConnect()
    }

    private fun avahiConnect() {
        check(mutex.isLocked)
        check(avahi == null)
        val dbus = dbus!!.con
        val obj: Server
        var handler: AutoCloseable? = null
        val state: AvahiServerState?

        log.debug("connecting to avahi")
        try {
            obj = dbus.getRemoteObject(AVAHI_BUS, AVAHI_SERVER_PATH, Server::class.java)
            handler = dbus.addSigHandler(Server.StateChanged::class.java, obj, serverStateChanged)
            state = AvahiServerState.fromInt(obj.GetState())
        } catch (e: DBusException) {
            log.error("failed to connect to avahi", e)
            try { handler?.close() } catch (_: DBusException) {}
            return
        }

        avahi = object : ServerState {
            override val obj = obj
            override val handler = handler!!
            override fun close() {
                check(mutex.isLocked)
                onTeardown()
                log.debug("disconnecting from avahi")
                try { handler.close() } catch (_: DBusException) {}
                avahi = null
            }
        }

        log.debug("current avahi server state is {}", state)
        handleServerState(state)
    }

    private fun handleServerState(state: AvahiServerState?) {
        if (state == AvahiServerState.AVAHI_SERVER_REGISTERING ||
            state == AvahiServerState.AVAHI_SERVER_RUNNING)
            onSetup()
        else
            onTeardown()
    }

    abstract fun onSetup()
    abstract fun onTeardown()

    protected interface DBusState : AutoCloseable {
        val con: DBusConnection
        val handler: AutoCloseable
    }

    protected interface ServerState : AutoCloseable {
        val obj: Server
        val handler: AutoCloseable
    }
}
