@file:Suppress("FunctionName")

package net.joshe.signman.zeroconf.avahi.interfaces

import net.joshe.signman.zeroconf.avahi.AVAHI_BUS
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import java.util.Objects

@DBusInterfaceName("$AVAHI_BUS.ServiceBrowser")
internal interface ServiceBrowser : DBusInterface {
    fun Free()
    fun Start()

    class ItemNew(path: String, val iface: Int, val protocol: Int, val sname: String, val stype: String, val domain: String, val rflags: UInt32)
        : DBusSignal(path, iface, protocol, sname, stype, domain, rflags) {
        override fun equals(other: Any?) = (this === other) ||
                (other is ItemNew && (iface == other.iface && protocol == other.protocol && sname == other.sname && stype == other.stype && domain == other.domain)) ||
                (other is ItemRemove && (iface == other.iface && protocol == other.protocol && sname == other.sname && stype == other.stype && domain == other.domain))

        override fun hashCode() = Objects.hash(iface, protocol, sname, stype, domain, rflags)
    }

    class ItemRemove(path: String, val iface: Int, val protocol: Int, val sname: String, val stype: String, val domain: String, val rflags: UInt32)
        : DBusSignal(path, iface, protocol, sname, stype, domain, rflags) {
        override fun equals(other: Any?) = (this === other) ||
                (other is ItemNew && (iface == other.iface && protocol == other.protocol && sname == other.sname && stype == other.stype && domain == other.domain)) ||
                (other is ItemRemove && (iface == other.iface && protocol == other.protocol && sname == other.sname && stype == other.stype && domain == other.domain))

        override fun hashCode() = Objects.hash(iface, protocol, sname, stype, domain)
    }

    class Failure(path: String, val error: String) : DBusSignal(path, error)
    class AllForNow(path: String): DBusSignal(path)
}
