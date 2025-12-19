package net.joshe.signman.zeroconf.avahi.interfaces

import at.asitplus.cidre.IpAddress
import java.util.List
import net.joshe.signman.zeroconf.Service
import net.joshe.signman.zeroconf.avahi.DBus.parseTxt
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.UInt32

@Suppress("UNUSED")
class ResolveServiceTuple(@Position(0) val iface: Int,
                          @Position(1) val protocol: Int,
                          @Position(2) val name: String,
                          @Position(3) val type: String,
                          @Position(4) val domain: String,
                          @Position(5) val host: String,
                          @Position(6) val aprotocol: Int,
                          @Position(7) val address: String,
                          @Position(8) val port: UInt16,
                          @Position(9) val txt: List<List<Byte>>,
                          @Position(10) val flags: UInt32)
    : Tuple() {
    val params = parseTxt(txt)

    fun toService() = Service(
        name = name,
        hostname = host,
        address = IpAddress(address),
        port = port.toInt(),
        params = params)
}
