@file:Suppress("FunctionName")

package net.joshe.signman.zeroconf.avahi.interfaces

import net.joshe.signman.zeroconf.avahi.AVAHI_BUS
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32

@DBusInterfaceName("$AVAHI_BUS.Server")
internal interface Server : DBusInterface {
    fun GetState(): Int

    class StateChanged(path: String, val state: Int, val error: String): DBusSignal(path, state, error)

    fun ResolveService(iface: Int, protocol: Int, name: String, type: String, domain: String, aprotocol: Int, flags: UInt32): ResolveServiceTuple

    fun EntryGroupNew(): DBusPath

    fun ServiceBrowserNew(iface: Int, protocol: Int, type: String, domain: String, flags: UInt32): DBusPath
}
