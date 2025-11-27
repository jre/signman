package net.joshe.signman.server

import net.joshe.signman.api.ColorType
import net.joshe.signman.api.RGB
import net.joshe.signman.api.RGBColor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class StateText {
    private val conf = Config.SignConfig(width = 200, height = 100, type = ColorType.RGB)
    private val fg = RGBColor(RGB(9, 9, 9))
    private val bg = RGBColor(RGB(0, 0, 255))
    private val otherFg = RGBColor(RGB(16, 16, 16))
    private val otherBg = RGBColor(RGB(0, 255, 0))

    private val jsonText = """{"text":"TEST",""" +
            """"bg":{"type":"rgb","rgb":"${bg.rgb.toHexString()}"},""" +
            """"fg":{"type":"rgb","rgb":"${fg.rgb.toHexString()}"}}"""

    private fun load(f: State.() -> Unit = {})
            = State.load(ByteArrayInputStream(jsonText.toByteArray()), Renderer(conf), f)
    private fun mk(f: (State.() -> Unit)? = {})
            = State("TEST", bg, fg, Renderer(conf), f)

    @Test fun testStateLoadText() = assertEquals(mk().text, load().text)
    @Test fun testStateLoadBg() = assertEquals(mk().bg, load().bg)
    @Test fun testStateLoadFg() = assertEquals(mk().fg, load().fg)
    @Test fun testStatePngETag() = assertEquals(mk().pngETag, load().pngETag)
    @Test fun testStateStore() = assertEquals(jsonText,
        ByteArrayOutputStream().also { mk().store(it) }.toString())

    @Test fun testStateUpdate() {
        var updateCount = 0
        val actual = mk { updateCount++ }
        val default = mk()

        assertEquals(0, updateCount)

        var oldMod = actual.lastModified
        actual.update("changed", otherBg, otherFg)
        assertEquals(1, updateCount)
        assert(oldMod < actual.lastModified) { "$oldMod expected to be < ${actual.lastModified}" }
        assertNotEquals(default.pngETag, actual.pngETag)
        assertEquals("changed", actual.text)
        assertEquals(otherFg, actual.fg)
        assertEquals(otherBg, actual.bg)

        oldMod = actual.lastModified
        actual.update("TEST", bg, fg)
        assertEquals(2, updateCount)
        assert(oldMod < actual.lastModified) { "$oldMod expected to be < ${actual.lastModified}" }
        assertEquals(default.pngETag, actual.pngETag)
        assertEquals(default.text, actual.text)
        assertEquals(default.fg, actual.fg)
        assertEquals(default.bg, actual.bg)
    }

    @Test fun testStateErase() {
        var updateCount = 0
        val actual = load { updateCount++ }
        val oldMod = actual.lastModified
        val oldTag = actual.pngETag

        assertEquals(0, updateCount)
        actual.erase()
        assertEquals(1, updateCount)
        assert(oldMod < actual.lastModified) { "$oldMod expected to be < ${actual.lastModified}" }
        assert(oldTag != actual.pngETag) { "$oldTag expected to be != ${actual.pngETag}" }
        assertEquals("", actual.text)
        assertEquals(RGBColor(RGB(0, 0, 0)), actual.fg)
        assertEquals(RGBColor(RGB(255, 255, 255)), actual.bg)
    }
}
