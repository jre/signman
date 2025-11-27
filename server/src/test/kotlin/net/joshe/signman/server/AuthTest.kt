package net.joshe.signman.server

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class AuthTest {
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    private val uuid1 = "00112233-4455-6677-8899-aabbccddeeff"
    private val uuid2 = "ffeeddcc-bbaa-9988-7766-554433221100"

    private val passwdText = """
        # test password data
        alice:plain:apple
        bob:plain:banana
        charlie:plain:clementine
        denise:plain:durian
        """.trimIndent()

    private val ha1Map = mapOf(
        "alice" to "b94a436b518a549ffa9ec04913c3d94e535882c1df4de54626fbcefbf5152612", // uuid1
        "bob" to "1134d4f378808eca036997d4a952e16cf04e32af5114b9ed35771d746ee25172", // uuid1
        "charlie" to "0b114928b6ad9ffe51d3bf932e2e7f55e91be70dac8040460f82ff1e91b9882f", // uuid2
        "denise" to "5f9f7046841e5849b759d2ebf6dbdde1ee6cc99bed8c5821fd6744942a6d8f41", // uuid2
    )

    private fun get(auth: Auth, user: String, uuid: String) = auth.getHA1Digest(user, uuid, digest)?.toHexString()

    @Test fun testHA1Digest() {
        val auth = Auth.loadStream(ByteArrayInputStream(passwdText.toByteArray()))

        assertEquals(get(auth, "alice", uuid1), ha1Map["alice"])
        assertEquals(get(auth, "bob", uuid1), ha1Map["bob"])
        assertEquals(get(auth, "charlie", uuid2), ha1Map["charlie"])
        assertEquals(get(auth, "denise", uuid2), ha1Map["denise"])
        assertNull(get(auth, "nobody", uuid1))
    }
}
