package net.joshe.signman.server

import net.joshe.signman.api.dnssdService
import net.joshe.signman.api.dnssdUuidKey
import java.io.OutputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AvahiService(conf: Config, uuid: Uuid) {
    private val escapes = mapOf('<' to "&lt;", '>' to "&gt;", '&' to "&amp;", '\'' to "&apos;", '"' to "&quot;")

    private val xml = """
        <?xml version="1.0"?>
        <!DOCTYPE service-group SYSTEM "avahi-service.dtd">
        <service-group>
            <name>${escape(conf.name)}</name>
            <service>
                <type>$dnssdService</type>
                <port>${conf.server.port}</port>
                <txt-record>${escape(dnssdUuidKey)}=${uuid.toHexDashString()}</txt-record>
            </service>
        </service-group>
    """.trimIndent()

    private fun escape(str: String) = str.map { escapes.getOrElse(it) { it.toString() } }.joinToString("")

    fun store(stream: OutputStream) = stream.write(xml.toByteArray())
}
