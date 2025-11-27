package net.joshe.signman.server

import io.ktor.utils.io.core.toByteArray
import net.joshe.signman.api.ColorType
import net.joshe.signman.api.IndexedColor
import net.joshe.signman.api.RGB
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class ConfigTest {
    private val in1 = """# Simple config parsing test input
server.directory = \t /home/signman/state  \t
auth.type  = file
 auth.path= /home/signman/passwd
sign.width =   640\t
sign.height =480  
sign.type =  \t rgb
"""

    private val out1 = """auth.path=/home/signman/passwd
auth.type=file
name=Signman Server
server.directory=/home/signman/state
server.port=80
sign.font=Serif
sign.height=480
sign.type=rgb
sign.width=640"""

    private val in2 = """# indexed color parsing test input
name = Config Test
server.port = 8080
server.directory = /var/signman
auth.type = file
auth.path = /etc/signman.passwd
sign.width = 340
sign.height = 180
sign.font = Sans
sign.type = indexed
sign.colors.0 = 000000 Black
sign.colors.1 =  ffffff   White  
sign.colors.2 = \tffff00 \t Yellow \t
sign.colors.3 = \t ff0000\t  Red,  I guess?\t 
driver.name = placeholder
driver.device = /dev/fake"""

    private val out2 = """auth.path=/etc/signman.passwd
auth.type=file
driver.device=/dev/fake
driver.name=placeholder
name=Config Test
server.directory=/var/signman
server.port=8080
sign.colors.0=000000 Black
sign.colors.1=ffffff White
sign.colors.2=ffff00 Yellow
sign.colors.3=ff0000 Red,  I guess?
sign.font=Sans
sign.height=180
sign.type=indexed
sign.width=340"""

    private val colors = listOf(
        IndexedColor(index = 0, name = "Black", rgb = RGB(0,0,0)),
        IndexedColor(index = 1, name = "White", rgb = RGB(255,255,255)),
        IndexedColor(index = 2, name = "Yellow", rgb = RGB(255,255,0)),
        IndexedColor(index = 3, name = "Red,  I guess?", rgb = RGB(255,0,0)))

    private fun p(str: String) = Config.load(ByteArrayInputStream(str.toByteArray()))

    private fun output(config: Config) = ByteArrayOutputStream().also { config.store(it) }.toString()
        .lines().filterNot { it.startsWith('#') || it.isEmpty() }.sorted().joinToString("\n")

    @Test fun testParse1Name() { assertEquals("Signman Server", p(in1).name)}
    @Test fun testParse1Port() { assertEquals(80, p(in1).server.port) }
    @Test fun testParse1Dir() { assertEquals(File("/home/signman/state"), p(in1).server.directory) }
    @Test fun testParse1Width() { assertEquals(640, p(in1).sign.width) }
    @Test fun testParse1Height() { assertEquals(480, p(in1).sign.height) }
    @Test fun testParse1Scheme() { assertEquals(ColorType.RGB, p(in1).sign.type) }
    @Test fun testParse1Colors() { assertNull(p(in1).sign.colors) }
    @Test fun testParse1Font() { assertEquals("Serif", p(in1).sign.font) }
    @Test fun testParse1AuthType() { assertEquals(Config.AuthType.FILE, p(in1).auth.type) }
    @Test fun testParse1AuthPath() { assertEquals(File("/home/signman/passwd"), p(in1).auth.path) }
    @Test fun testOutput1() { assertEquals(out1, output(p(in1))) }

    @Test fun testParse2Name() { assertEquals("Config Test", p(in2).name)}
    @Test fun testParse2Port() { assertEquals(8080, p(in2).server.port) }
    @Test fun testParse2Dir() { assertEquals(File("/var/signman"), p(in2).server.directory) }
    @Test fun testParse2Width() { assertEquals(340, p(in2).sign.width) }
    @Test fun testParse2Height() { assertEquals(180, p(in2).sign.height) }
    @Test fun testParse2Scheme() { assertEquals(ColorType.INDEXED, p(in2).sign.type) }
    @Test fun testParse2Colors() { assertEquals(colors.size, p(in2).sign.colors?.size) }
    @Test fun testParse2Black() { assertEquals(colors[0], p(in2).sign.colors?.get(0)) }
    @Test fun testParse2White() { assertEquals(colors[1], p(in2).sign.colors?.get(1)) }
    @Test fun testParse2Yellow() { assertEquals(colors[2], p(in2).sign.colors?.get(2)) }
    @Test fun testParse2Red() { assertEquals(colors[3], p(in2).sign.colors?.get(3)) }
    @Test fun testParse2Font() { assertEquals("Sans", p(in2).sign.font) }
    @Test fun testParse2AuthType() { assertEquals(Config.AuthType.FILE, p(in2).auth.type) }
    @Test fun testParse2AuthPath() { assertEquals(File("/etc/signman.passwd"), p(in2).auth.path) }
    @Test fun testParse2ADriver() { assertEquals(Config.Driver.PLACEHOLDER, p(in2).driver?.name) }
    @Test fun testParse2ADevice() { assertEquals(File("/dev/fake"), p(in2).driver?.device) }
    @Test fun testOutput2() { assertEquals(out2, output(p(in2))) }
}
