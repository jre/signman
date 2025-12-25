package net.joshe.signman.server

import kotlinx.coroutines.runBlocking
import net.joshe.signman.api.IndexedColor
import net.joshe.signman.api.RGB
import net.joshe.signman.api.RGBColor
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class CacheableTest {
    private val defText = "writing tests is so exciting"
    private val defBg = RGBColor(RGB(240, 240, 240))
    private val defFg = RGBColor(RGB(16, 16, 16))
    private val defSnap = State.Snapshot(defText, fg = defFg, bg = defBg)
    private val conf = Config(
        server = Config.ServerConfig(directory = File("/nonexistent")),
        sign = Config.SignConfig(width = 100, height = 60,
            color = Config.RGBColorConfig(foreground = defFg, background = defBg)),
        auth = Config.AuthConfig(Config.AuthType.FILE, File("/fake/file")))
    private val renderer = Renderer(conf, null)

    private fun defState(onUpdate: State.(State.Snapshot) -> Unit)
            = State.initialize(defText, fg = defFg, bg = defBg, onUpdate)

    @Test fun testDifferent() = runBlocking {
        var lastSnap: State.Snapshot
        val state = defState { lastSnap = it }
        lastSnap = state.snapshot

        val older = Cacheable.create(conf, lastSnap, renderer)
        assertEquals(defSnap, older.state)

        state.update("testy", fg = defFg, bg = defBg)
        val newer = Cacheable.create(conf, lastSnap, renderer)
        assertNotEquals(older, newer)
        assertNotEquals(older.hashCode(), newer.hashCode())
        assertTrue { older.modified < newer.modified }
        assertNotEquals(older.state, newer.state)
        assertNotEquals(older.stateETag, newer.stateETag)
        assertNotEquals(older.html, newer.html)
        assertNotEquals(older.htmlETag, newer.htmlETag)
        assertFalse { older.png.contentEquals(newer.png) }
        assertNotEquals(older.pngETag, newer.pngETag)
    }

    @Test fun testAlmostIdentical() = runBlocking {
        var lastSnap: State.Snapshot
        val state = defState { lastSnap = it }
        lastSnap = state.snapshot

        val older = Cacheable.create(conf, lastSnap, renderer)
        assertEquals(defSnap, older.state)

        state.update(defText, fg = defFg, bg = defBg)
        val newer = Cacheable.create(conf, lastSnap, renderer)
        assertNotEquals(older, newer)
        assertNotEquals(older.hashCode(), newer.hashCode())
        assertTrue { older.modified < newer.modified }
        assertEquals(older.state, newer.state)
        assertEquals(older.stateETag, newer.stateETag)
        assertEquals(older.html, newer.html)
        assertEquals(older.htmlETag, newer.htmlETag)
        assertContentEquals(older.png, newer.png)
        assertEquals(older.pngETag, newer.pngETag)
    }

    @Test fun testIdentical() = runBlocking {
        val older = Cacheable.create(conf, defSnap, renderer)
        val newer = Cacheable.create(older.modified, defSnap, older.png, older.html)
        assertEquals(older, newer)
        assertEquals(older.hashCode(), newer.hashCode())
        assertEquals(older.modified, newer.modified)
        assertEquals(older.state, newer.state)
        assertEquals(older.stateETag, newer.stateETag)
        assertEquals(older.html, newer.html)
        assertEquals(older.htmlETag, newer.htmlETag)
        assertContentEquals(older.png, newer.png)
        assertEquals(older.pngETag, newer.pngETag)
    }

    @Test fun testChangedText() = runBlocking {
        val newState = defSnap.copy(text = "cool beans")
        val older = Cacheable.create(conf, defSnap, renderer)
        val newer = Cacheable.create(older.modified, newState, older.png, older.html)
        assertNotEquals(older, newer)
        assertNotEquals(older.hashCode(), newer.hashCode())
        assertEquals(older.modified, newer.modified)
        assertNotEquals(older.state, newer.state)
        assertNotEquals(older.stateETag, newer.stateETag)
        assertEquals(older.html, newer.html)
        assertEquals(older.htmlETag, newer.htmlETag)
        assertContentEquals(older.png, newer.png)
        assertEquals(older.pngETag, newer.pngETag)
    }

    @Test fun testChangedBg() = runBlocking {
        val newState = defSnap.copy(bg = defSnap.fg)
        val older = Cacheable.create(conf, defSnap, renderer)
        val newer = Cacheable.create(older.modified, newState, older.png, older.html)
        assertNotEquals(older, newer)
        assertNotEquals(older.hashCode(), newer.hashCode())
        assertEquals(older.modified, newer.modified)
        assertNotEquals(older.state, newer.state)
        assertNotEquals(older.stateETag, newer.stateETag)
        assertEquals(older.html, newer.html)
        assertEquals(older.htmlETag, newer.htmlETag)
        assertContentEquals(older.png, newer.png)
        assertEquals(older.pngETag, newer.pngETag)
    }

    @Test fun testChangedFg() = runBlocking {
        val newState = defSnap.copy(fg = IndexedColor(0, defFg.rgb))
        val older = Cacheable.create(conf, defSnap, renderer)
        val newer = Cacheable.create(older.modified, newState, older.png, older.html)
        assertNotEquals(older, newer)
        assertNotEquals(older.hashCode(), newer.hashCode())
        assertEquals(older.modified, newer.modified)
        assertNotEquals(older.state, newer.state)
        assertNotEquals(older.stateETag, newer.stateETag)
        assertEquals(older.html, newer.html)
        assertEquals(older.htmlETag, newer.htmlETag)
        assertContentEquals(older.png, newer.png)
        assertEquals(older.pngETag, newer.pngETag)
    }

    @Test fun testChangedPng() = runBlocking {
        val newPng = renderer.render(defSnap.copy(text = "Wow!"))
        val older = Cacheable.create(conf, defSnap, renderer)
        val newer = Cacheable.create(older.modified, defSnap, newPng, older.html)
        assertNotEquals(older, newer)
        assertNotEquals(older.hashCode(), newer.hashCode())
        assertEquals(older.modified, newer.modified)
        assertEquals(older.state, newer.state)
        assertEquals(older.stateETag, newer.stateETag)
        assertEquals(older.html, newer.html)
        assertEquals(older.htmlETag, newer.htmlETag)
        assertFalse(older.png.contentEquals(newer.png))
        assertNotEquals(older.pngETag, newer.pngETag)
    }

    @Test fun testChangedHtml() = runBlocking {
        val htmlState = defSnap.copy(text = "help im trapped in a test suite factory")
        val newHtml = Server.getHtml(conf, htmlState)
        val older = Cacheable.create(conf, defSnap, renderer)
        val newer = Cacheable.create(older.modified, defSnap, older.png, newHtml)
        assertNotEquals(older, newer)
        assertNotEquals(older.hashCode(), newer.hashCode())
        assertEquals(older.modified, newer.modified)
        assertEquals(older.state, newer.state)
        assertEquals(older.stateETag, newer.stateETag)
        assertNotEquals(older.html, newer.html)
        assertNotEquals(older.htmlETag, newer.htmlETag)
        assertContentEquals(older.png, newer.png)
        assertEquals(older.pngETag, newer.pngETag)
    }
}
