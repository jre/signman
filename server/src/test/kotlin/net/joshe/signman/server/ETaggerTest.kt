package net.joshe.signman.server

import kotlin.test.Test
import kotlin.test.assertEquals

class ETaggerTest {
    @Test fun testIntCompanion() {
        assertEquals("26do5k3", ETagger.get(0x12345678))
    }

    @Test fun testBytesCompanion() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31)
        assertEquals("28icvka", ETagger.get(bytes))
    }

    @Test fun testStringCompanion() {
        assertEquals("35v8e96", ETagger.get("123456789"))
    }

    @Test fun testInt() {
        assertEquals("37rbvv9", ETagger().apply { update(0x76543210) }.tag)
    }

    @Test fun testBytes() {
        val bytes = byteArrayOf(31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
        assertEquals("2db1rri", ETagger().apply { update(bytes) }.tag)
    }

    @Test fun testString() {
        assertEquals("10kv8pp", ETagger().apply { update("The quick brown fox jumps over the lazy dog") }.tag)
    }

    @Test fun testComposite() {
        assertEquals("ftf8k0", ETagger().apply {
            update(42)
            update(byteArrayOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71))
            update("this sure is some sort of test data")
        }.tag)
    }

    @Test fun testReset() {
        val et = ETagger()
        assertEquals("4ri9ir", et.apply { update(42) }.tag)
        assertEquals("3qgi5o1", et.apply { update(42) }.tag)
        et.reset()
        assertEquals("4ri9ir", et.apply { update(42) }.tag)
    }
}
