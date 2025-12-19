package net.joshe.signman.zeroconf.avahi

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.freedesktop.dbus.DBusMatchRule
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus.NameOwnerChanged
import org.freedesktop.dbus.interfaces.DBusSigHandler
import java.util.ArrayList
import java.util.List as JavaList

internal object DBus {
    private val connMutex = Mutex()
    private var conn: DBusConnection? = null

    suspend fun connect() = connMutex.withLock {
        if (conn == null)
            conn = DBusConnectionBuilder.forSystemBus().build()
        conn!!
    }

    suspend fun watchName(name: String, handler: DBusSigHandler<NameOwnerChanged>): AutoCloseable
    // XXX use DBusMatchRuleBuilder() when dbus-java 5.2 is released to filter on arg0 = name
    = connect().addSigHandler(NameOwnerChanged::class.java) { change ->
        if (change.name == name)
            handler.handle(change)
    }

    fun parseTxt(txt: JavaList<out JavaList<out Byte>>) = txt.associate { byteList ->
        val bytes = byteList.toByteArray()
        when (val idx = bytes.indexOf('='.code.toByte())) {
            -1 -> Pair(bytes.decodeToString(), "")
            bytes.size - 1 -> Pair(bytes.decodeToString(0, idx), "")
            else -> Pair(bytes.decodeToString(0, idx),
                bytes.decodeToString(idx + 1, bytes.size))
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun serializeTxt(params: Map<String,String>) = ArrayList<JavaList<Byte>>().also { outer ->
        params.keys.sorted().map { k ->
            val v = params.getValue(k)
            check('=' !in k && k.length + v.length <= 254 )
            ArrayList<Byte>().also { "$k=$v".encodeToByteArray().toCollection(it) } as JavaList<Byte>
        }.toCollection(outer)
    } as JavaList<JavaList<Byte>>
}
