package net.joshe.signman.server

import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class AuthTest {
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

    private fun get(auth: Auth, user: String, uuid: String) = auth.digestProvider(user, uuid)?.toHexString()

    @Test fun testHA1Digest() {
        val auth = Auth.loadStream(ByteArrayInputStream(passwdText.toByteArray()))

        assertEquals(ha1Map["alice"], get(auth, "alice", uuid1))
        assertEquals(ha1Map["bob"], get(auth, "bob", uuid1))
        assertEquals(ha1Map["charlie"], get(auth, "charlie", uuid2))
        assertEquals(ha1Map["denise"], get(auth, "denise", uuid2))
        assertNull(get(auth, "nobody", uuid1))
    }

    @Test fun testBad() {
        assertThrows<IllegalStateException> { Auth.loadStream(ByteArrayInputStream("foo:garbage:bar".toByteArray())) }
    }
}
