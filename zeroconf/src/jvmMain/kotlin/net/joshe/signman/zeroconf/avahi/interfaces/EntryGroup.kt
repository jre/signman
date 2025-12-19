@file:Suppress("FunctionName")

package net.joshe.signman.zeroconf.avahi.interfaces

import net.joshe.signman.zeroconf.avahi.AVAHI_BUS
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.UInt32

@DBusInterfaceName("$AVAHI_BUS.EntryGroup")
internal interface EntryGroup : DBusInterface {
    fun Free()
    fun Commit()

    fun GetState(): Int
    class StateChanged(path: String, val state: Int, val error: String) : DBusSignal(path, state, error)

    fun AddService(iface: Int,
                   protocol: Int,
                   flags: UInt32,
                   name: String,
                   type: String,
                   domain: String,
                   host: String,
                   port: UInt16,
                   txt: java.util.List<java.util.List<Byte>>)
}
