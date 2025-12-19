package net.joshe.signman.zeroconf.avahi

import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.joshe.signman.zeroconf.DOMAIN_LOCAL
import net.joshe.signman.zeroconf.ServicePublisher
import net.joshe.signman.zeroconf.avahi.interfaces.EntryGroup
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.UInt32
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

internal class AvahiPublisher(context: CoroutineContext,
                              private val name: String,
                              private val type: String,
                              private val port: Int,
                              private val params: Map<String, String>,
) : ServicePublisher, AvahiClient(context) {
    override val log: Logger = LoggerFactory.getLogger(this::class.java)
    private var entry: EntryGroupState? = null

    private val groupStateChanged = DBusSigHandler<EntryGroup.StateChanged> { change ->
        val state = AvahiEntryGroupState.fromInt(change.state)
        log.debug("entry group changed: {}({}) {}", state, change.state, change.error)
        scope.launch {
            mutex.withLock { handleEntryGroupState(state) }
        }
    }

    override fun start() {
        scope.launch {
            mutex.withLock {
                log.debug("starting avahi publisher for {}", type)
                dbusConnect()
            }
        }
    }

    override suspend fun stop() {
        withContext(scope.coroutineContext) {
            mutex.withLock {
                log.debug("stopping avahi publisher for {}", type)
                dbus?.close()
                scope.coroutineContext.cancelChildren()
                log.debug("stopped avahi publisher for {}", type)
            }
        }
    }

    override fun onSetup() {
        check(mutex.isLocked)
        if (entry != null)
            return
        val dbus = dbus!!.con
        val avahi = avahi!!.obj
        var obj: EntryGroup? = null
        val handler: AutoCloseable
        val state: AvahiEntryGroupState?

        log.debug("publishing service")
        try {
            obj = dbus.getExportedObject(AVAHI_BUS, avahi.EntryGroupNew().path, EntryGroup::class.java)
            handler = dbus.addSigHandler(EntryGroup.StateChanged::class.java, obj, groupStateChanged)
        } catch (e: DBusException) {
            log.error("failed to publish service", e)
            try { obj?.Free() } catch (_: DBusException) {}
            return
        }

        entry = object : EntryGroupState {
            override val obj = obj!!
            override val handler = handler
            override fun close() {
                try { this.handler.close() } catch (_: DBusException) {}
                try { this.obj.Free() } catch (_: DBusException) {}
                entry = null
            }
        }

        try {
            obj.AddService(
                iface = AVAHI_IF_UNSPEC,
                protocol = AVAHI_PROTO_UNSPEC,
                flags = UInt32(0),
                name = name,
                type = type,
                domain = DOMAIN_LOCAL,
                host = "",
                port = UInt16(port),
                txt = DBus.serializeTxt(params))
            obj.Commit()
            state = AvahiEntryGroupState.fromInt(obj.GetState())
        } catch (e: DBusException) {
            log.error("failed to publish service", e)
            entry?.close()
            return
        }

        log.debug("current published entry state is {}", state)
        handleEntryGroupState(state)
    }

    private fun handleEntryGroupState(state: AvahiEntryGroupState?) {
        check(mutex.isLocked)
        if (state != AvahiEntryGroupState.AVAHI_ENTRY_GROUP_UNCOMMITED &&
            state != AvahiEntryGroupState.AVAHI_ENTRY_GROUP_REGISTERING &&
            state != AvahiEntryGroupState.AVAHI_ENTRY_GROUP_ESTABLISHED)
            entry?.close()
    }

    override fun onTeardown() {
        entry?.close()
    }

    private interface EntryGroupState : AutoCloseable {
        val obj: EntryGroup
        val handler: AutoCloseable
    }
}
