package net.joshe.signman.client

import io.ktor.http.URLBuilder
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class HostInfoTest {
    @Test fun testConstructors4() {
        val name = "server.local"
        val ipStr = "192.168.0.1"
        val ip = IP(ipStr)
        val port = 8000
        val base = HostInfo(name, ipStr, port, ip)

        assertEquals(base, HostInfo(name, address = ip, port = port))
        assertEquals(base, HostInfo(name, addressSerializationKludge = ipStr, port = port))
        assertEquals(base, HostInfo(URLBuilder(host = name, port = port).build(), ip))
        assertEquals(base, HostInfo(name, ipStr, port))
    }

    @Test fun testConstructors6() {
        val name = "server.local"
        val ipStr = "fe80::201:2ff:fe03:405"
        val ip = IP(ipStr)
        val port = 8000
        val base = HostInfo(name, ipStr, port, ip)

        assertEquals(base, HostInfo(name, address = ip, port = port))
        assertEquals(base, HostInfo(name, addressSerializationKludge = ipStr, port = port))
        assertEquals(base, HostInfo(URLBuilder(host = name, port = port).build(), ip))
        assertEquals(base, HostInfo(name, ipStr, port))
    }

    @Test fun testSerialize4() {
        val ip = "192.168.0.1"
        val json = """{"hostname":"myhost","address":"$ip","port":80}"""
        val info = HostInfo(hostname = "myhost", addressSerializationKludge = ip, port = 80)

        assertEquals(json, Json.encodeToString(info))
        assertEquals(info, Json.decodeFromString(json))
    }

    @Test fun testSerialize6() {
        val ip = "fe80::1234:56ff:fe78:9abc"
        val json = """{"hostname":"myhost","address":"$ip","port":8080}"""
        val info = HostInfo(hostname = "myhost", addressSerializationKludge = ip, port = 8080)

        assertEquals(json, Json.encodeToString(info))
        assertEquals(info, Json.decodeFromString(json))
    }
}
