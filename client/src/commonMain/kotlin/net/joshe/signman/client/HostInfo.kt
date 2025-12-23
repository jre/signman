package net.joshe.signman.client

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.joshe.signman.zeroconf.Service

@Serializable
data class HostInfo(
    val hostname: String,
    @SerialName("address")
    val addressSerializationKludge: String,
    val port: Int,
    @Transient
    val address: IP = IP(addressSerializationKludge)) {
    constructor(url: Url, address: IP) : this(hostname = url.host, address = address, port = url.port,
        addressSerializationKludge = address.toString())

    constructor(hostname: String, address: IP, port: Int) : this(hostname = hostname, address = address, port = port,
        addressSerializationKludge = address.toString())

    constructor(service: Service) : this(hostname = service.hostname, address = service.address, port = service.port)

    fun toUrl(path: String) = URLBuilder(protocol = URLProtocol.HTTP, host = address.toString(), port = port)
        .appendPathSegments(path).build()

    override fun toString() = "$hostname($address):$port"
}
