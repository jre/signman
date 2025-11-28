package net.joshe.signman.api

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AuthTest {
    @Test fun testRealm() {
        assertEquals("f55f12cf-239c-3e45-8a89-81f9a2a04fab",
            Uuid.parse("F55F12CF239C3E458A8981F9A2A04FAB").toHttpAuthenticationRealm())
    }

    @Test fun testHA1() {
        assertContentEquals(
            byteArrayOf(
                0x66, 0x6f, 0x6f, 0x3a, // foo:
                0x30, 0x36, 0x65, 0x32, 0x33, 0x34, 0x36, 0x65, 0x2d, //06e2346e-
                0x38, 0x32, 0x38, 0x37, 0x2d, // 8287-
                0x32, 0x65, 0x63, 0x35, 0x2d, // 2ec5-
                0x37, 0x66, 0x38, 0x31, 0x2d, // 7f81-
                0x37, 0x62, 0x30, 0x62, 0x30, 0x39, 0x36, 0x65, 0x30, 0x65, 0x32, 0x31, 0x3a, // 7b0b096e0e21:
                0x62, 0x61, 0x72), // bar
            getDigestHA1Input("foo", pass = "bar",
                uuid = Uuid.parse("06e2346e-8287-2ec5-7f81-7b0b096e0e21")))
    }
}
