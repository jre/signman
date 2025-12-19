package net.joshe.signman.zeroconf.avahi

import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.joshe.signman.zeroconf.DOMAIN_LOCAL
import net.joshe.signman.zeroconf.Service
import net.joshe.signman.zeroconf.ServiceBrowser as BaseServiceBrowser
import net.joshe.signman.zeroconf.avahi.interfaces.ServiceBrowser
import net.joshe.signman.zeroconf.serviceTypeValid
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.types.UInt32
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

internal class AvahiBrowser(context: CoroutineContext, private val type: String)
    : BaseServiceBrowser, AvahiClient(context) {
    init { check(serviceTypeValid(type)) }

    override val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val _state = MutableStateFlow<Set<Service>?>(null)
    override val state = _state.asStateFlow()
    private var browser: ServiceBrowserState? = null

    private val itemNew = DBusSigHandler<ServiceBrowser.ItemNew> { item ->
        log.info("ItemNew interface={} protocol={} sname={} stype={} domain={} rflags={}",
            item.iface, item.protocol, item.sname, item.stype, item.domain, item.rflags)
        scope.launch {
            mutex.withLock {
                // XXX handle error here?
                browser!!.services[item] = avahi!!.obj.ResolveService(
                    iface = item.iface,
                    protocol = item.protocol,
                    name = item.sname,
                    type = item.stype,
                    domain = item.domain,
                    aprotocol = AVAHI_PROTO_UNSPEC,
                    flags = UInt32(0)
                ).toService()
                emit()
            }
        }
    }

    private val itemRemove = DBusSigHandler<ServiceBrowser.ItemRemove> { item ->
        log.info("ItemRemove interface={} protocol={} sname={} stype={} domain={} rflags={}",
            item.iface, item.protocol, item.sname, item.stype, item.domain, item.rflags)
        scope.launch {
            mutex.withLock {
                val browser = browser!!
                browser.services.filterKeys { it == item }.forEach { browser.services.remove(it.key) }
                emit()
            }
        }
    }

    private val failure = DBusSigHandler<ServiceBrowser.Failure> { err ->
        // XXX
        log.error(err.error)
    }

    private val allForNow = DBusSigHandler<ServiceBrowser.AllForNow> {
        log.info("all for now")
        scope.launch {
            mutex.withLock {
                browser!!.ready = true
                emit()
            }
        }
    }

    override fun start() {
        scope.launch {
            mutex.withLock {
                dbusConnect()
            }
        }
    }

    override suspend fun stop() {
        withContext(scope.coroutineContext) {
            mutex.withLock {
                log.debug("stopping avahi browse for {}", type)
                dbus?.close()
                scope.coroutineContext.cancelChildren()
                log.debug("stopped avahi browse for {}", type)
            }
        }
    }

    override fun onSetup() {
        check(mutex.isLocked)
        val dbus = dbus!!.con
        val server = avahi!!.obj
        var obj: ServiceBrowser? = null
        var addHandler: AutoCloseable? = null
        var removeHandler: AutoCloseable? = null
        var failHandler: AutoCloseable? = null
        var doneHandler: AutoCloseable? = null

        log.info("starting browse for {}", type)
        try {
            val path = server.ServiceBrowserNew(
                iface = AVAHI_IF_UNSPEC,
                protocol = AVAHI_PROTO_UNSPEC,
                type = type,
                domain = DOMAIN_LOCAL,
                flags = UInt32(0)
            )
            obj = dbus.getExportedObject(AVAHI_BUS, path.path, ServiceBrowser::class.java)
            addHandler = dbus.addSigHandler(ServiceBrowser.ItemNew::class.java, obj, itemNew)
            removeHandler = dbus.addSigHandler(ServiceBrowser.ItemRemove::class.java, obj, itemRemove)
            failHandler = dbus.addSigHandler(ServiceBrowser.Failure::class.java, obj, failure)
            doneHandler = dbus.addSigHandler(ServiceBrowser.AllForNow::class.java, obj, allForNow)
            obj.Start()
        } catch (e: DBusException) {
            log.error("failed to browse for service {}", type, e)
            try { addHandler?.close() } catch (_: DBusException) {}
            try { removeHandler?.close() } catch (_: DBusException) {}
            try { failHandler?.close() } catch (_: DBusException) {}
            try { doneHandler?.close() } catch (_: DBusException) {}
            try { obj?.Free() } catch (_: DBusException) {}
            return
        }

        browser = object : ServiceBrowserState {
            override val obj = obj!!
            override val addHandler = addHandler!!
            override val removeHandler = removeHandler!!
            override val failHandler = failHandler!!
            override val doneHandler = doneHandler!!
            override val services = mutableMapOf<ServiceBrowser.ItemNew,Service>()
            override var ready = false
            override fun close() {
                try { this.addHandler.close() } catch (_: DBusException) {}
                try { this.removeHandler.close() } catch (_: DBusException) {}
                try { this.failHandler.close() } catch (_: DBusException) {}
                try { this.doneHandler.close() } catch (_: DBusException) {}
                try { this.obj.Free() } catch (_: DBusException) {}
                _state.value = emptySet()
                browser = null
            }
        }

        log.info("started browse for {}", type)
    }

    override fun onTeardown() {
        browser?.close()
    }

    private suspend fun emit() {
        check(mutex.isLocked)
        val browser = browser!!
        if (browser.ready)
            _state.emit(browser.services.values.toSet())
    }

    private interface ServiceBrowserState : AutoCloseable {
        val obj: ServiceBrowser
        val addHandler: AutoCloseable
        val removeHandler: AutoCloseable
        val failHandler: AutoCloseable
        val doneHandler: AutoCloseable
        val services: MutableMap<ServiceBrowser.ItemNew,Service>
        var ready: Boolean
    }
}
