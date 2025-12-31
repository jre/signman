package net.joshe.signman.server

import kotlinx.coroutines.runBlocking
import net.joshe.signman.api.RGB
import net.joshe.signman.api.RGBColor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class StateTest {
    private val fg = RGBColor(RGB(9, 9, 9))
    private val bg = RGBColor(RGB(0, 0, 255))
    private val otherFg = RGBColor(RGB(16, 16, 16))
    private val otherBg = RGBColor(RGB(0, 255, 0))
    private val defFg = RGBColor(RGB(1, 1, 1))
    private val defBg = RGBColor(RGB(254, 254, 254))
    private val config: Config.ColorConfig = Config.RGBColorConfig(foreground = defFg, background = defBg)

    private val jsonText = """{"text":"TEST",""" +
            """"bg":{"type":"rgb","rgb":"${bg.rgb.toHexString()}"},""" +
            """"fg":{"type":"rgb","rgb":"${fg.rgb.toHexString()}"}}"""

    private fun load(f: State.(State.Snapshot) -> Unit = {}) = runBlocking {
        State.load(config, ByteArrayInputStream(jsonText.toByteArray()), f)
    }

    private fun mk(f: (State.(State.Snapshot) -> Unit) = {}) = runBlocking {
        State.initialize(config, text = "TEST", fg = fg, bg = bg, onUpdate = f)
    }

    @Test fun testStateLoad() = assertEquals(mk().snapshot, load().snapshot)
    @Test fun testStateStore() = assertEquals(jsonText,
        ByteArrayOutputStream().also { mk().store(it) }.toString())

    @Test fun testStateUpdate(): Unit = runBlocking {
        var updateCount = 0
        var lastSnap: State.Snapshot
        val actual = mk {
            lastSnap = it
            updateCount++
        }
        lastSnap = actual.snapshot

        assertEquals(0, updateCount)

        val up1 = actual.update("changed", otherBg, otherFg)
        assertEquals(1, updateCount)
        assertEquals(State.Snapshot("changed", fg = otherFg, bg = otherBg), up1)
        assertEquals(up1, lastSnap)
        assertEquals(up1, actual.snapshot)

        val up2 = actual.update("TEST", bg, fg)
        assertEquals(2, updateCount)
        assertEquals(State.Snapshot("TEST", fg = fg, bg = bg), up2)
        assertEquals(up2, lastSnap)
        assertEquals(up2, actual.snapshot)
    }

    @Test fun testStateErase(): Unit = runBlocking {
        var updateCount = 0
        var lastSnap: State.Snapshot
        val actual = load {
            lastSnap = it
            updateCount++
        }
        lastSnap = actual.snapshot

        val firstUpdate = actual.snapshot
        assertEquals(0, updateCount)
        val res = actual.erase()
        assertEquals(1, updateCount)
        assertNotEquals(firstUpdate, lastSnap)
        assertEquals(State.Snapshot("", fg = defFg, bg = defBg), res)
        assertEquals(res, lastSnap)
        assertEquals(res, actual.snapshot)
    }

    @Test fun testSnapshotETag() {
        assertEquals("2o2j731", State.Snapshot("test", bg, fg).eTag())
    }
}
