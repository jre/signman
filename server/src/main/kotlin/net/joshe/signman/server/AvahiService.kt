package net.joshe.signman.server

import net.joshe.signman.api.dnssdService
import net.joshe.signman.api.dnssdUuidKey
import java.io.OutputStream

class AvahiService(conf: Config, uuid: String) {
    private val escapes = mapOf('<' to "&lt;", '>' to "&gt;", '&' to "&amp;", '\'' to "&apos;", '"' to "&quot;")

    val xml = """
        <?xml version="1.0"?>
        <!DOCTYPE service-group SYSTEM "avahi-service.dtd">
        <service-group>
            <name>${escape(conf.name)}</name>
            <service>
                <type>$dnssdService</type>
                <port>${conf.server.port}</port>
                <txt-record>${escape(dnssdUuidKey)}=$uuid</txt-record>
            </service>
        </service-group>
    """.trimIndent()

    private fun escape(str: String) = str.map { escapes.getOrElse(it) { it.toString() } }.joinToString("")

    fun store(stream: OutputStream) = stream.write(xml.toByteArray())
}
