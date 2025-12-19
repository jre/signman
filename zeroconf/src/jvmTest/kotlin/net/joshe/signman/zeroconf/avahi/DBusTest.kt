package net.joshe.signman.zeroconf.avahi

import org.junit.jupiter.api.assertThrows
import java.util.Vector
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import java.util.List as JavaList

class DBusTest {
    private fun txtTest(expected: Map<String,String>, vararg txt: ByteArray, encode: Boolean = true) {
        @Suppress("UNCHECKED_CAST")
        val lists = Vector<JavaList<Byte>>().also { outer ->
            txt.mapTo(outer) { bytes ->
                Vector<Byte>().also { bytes.toCollection(it) } as JavaList<Byte>
            }
        } as JavaList<JavaList<Byte>>

        assertEquals(expected, DBus.parseTxt(lists))
        if (encode)
            assertContentEquals(lists, DBus.serializeTxt(expected))
    }

    @Test fun testTxt1() = txtTest(mapOf("foo" to "bar"),
        byteArrayOf(102, 111, 111, 61, 98, 97, 114))

    @Test fun testTxt3() = txtTest(mapOf("a" to "b", "y" to "z", "m" to "n"),
        byteArrayOf(97, 61, 98), byteArrayOf(109, 61, 110), byteArrayOf(121, 61, 122))

    @Test fun testTxtEquals() = txtTest(mapOf("thing" to "foo=bar"),
        byteArrayOf(116, 104, 105, 110, 103, 61, 102, 111, 111, 61, 98, 97, 114))

    @Test fun testTxtNone() = txtTest(mapOf())
    @Test fun testTxtEmpty0() = txtTest(mapOf("" to ""), byteArrayOf(), encode = false)
    @Test fun testTxtEmpty1() = txtTest(mapOf("" to ""), byteArrayOf(61))

    @Test fun testTxtWeird() = txtTest(mapOf("" to "val", "key" to ""),
        byteArrayOf(61, 118, 97, 108), byteArrayOf(107, 101, 121, 61))

    @Test fun testTxtUTF8() = txtTest(mapOf("mood" to "\u0ca0_\u0ca0"),
        byteArrayOf(109, 111, 111, 100, 61, -32, -78, -96, 95, -32, -78, -96))

    @Test fun testTxtBadUTF8() = txtTest(mapOf("test" to "foo\ufffd\ufffdbar"),
        byteArrayOf(116, 101, 115, 116, 61, 102, 111, 111, -127, -128, 98, 97, 114), encode = false)

    @Test fun testBadKey() {
        assertThrows<IllegalStateException> { DBus.serializeTxt(mapOf("bad=key" to "value")) }
    }

    @Test fun testTxtLong() = txtTest(mapOf(
        "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" to
        "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                "1234567890123456789012345678901234567890123456789012345"),
        byteArrayOf(49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 61,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53))

    @Test fun testTooLong() {
        assertThrows<IllegalStateException> {
            DBus.serializeTxt(mapOf(
                "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" to
                "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                        "12345678901234567890123456789012345678901234567890123456"))
        }
    }
}
