package net.joshe.signman.server

import net.joshe.signman.api.ColorType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AvahiServiceTest {
    private fun mk(name: String, port: Int, uuid: Uuid) = AvahiService(uuid = uuid, conf = Config(
        name = name,
        server = Config.ServerConfig(port = port, directory = File("/")),
        sign = Config.SignConfig(width = 0, height = 0, type = ColorType.RGB),
        auth = Config.AuthConfig(Config.AuthType.FILE, File("/"))))

    @Test fun testXMLSimple() {
        val uuid = "01234567-89ab-cdef-0123-456789abcdef"
        val expect = """<?xml version="1.0"?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
    <name>Testy McTesterson</name>
    <service>
        <type>_signman-joshe._tcp</type>
        <port>1234</port>
        <txt-record>uuid=$uuid</txt-record>
    </service>
</service-group>"""
        assertEquals(expect, mk("Testy McTesterson", 1234,
            Uuid.parse(uuid)).xml)
    }

    @Test fun testXMLEscape() {
        val uuid = "fedcba98-7654-3210-fedc-ba9876543210"
        val expect = """<?xml version="1.0"?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
    <name>`~!@#$%^&amp;*()_+{}|:&quot;&lt;&gt;?-=[]\;&apos;,./</name>
    <service>
        <type>_signman-joshe._tcp</type>
        <port>1</port>
        <txt-record>uuid=$uuid</txt-record>
    </service>
</service-group>"""
        assertEquals(expect, mk("`~!@#$%^&*()_+{}|:\"<>?-=[]\\;',./", 1,
            Uuid.parse(uuid)).xml)
    }
}
