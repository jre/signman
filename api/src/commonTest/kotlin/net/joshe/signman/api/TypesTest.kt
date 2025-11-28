package net.joshe.signman.api

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class TypesTest {
    private val colors8 = listOf(
        IndexedColor(0, RGB(0,0,0), "Black"),
        IndexedColor(1, RGB(205,0,0), "Red"),
        IndexedColor(2, RGB(0,205,0), "Green"),
        IndexedColor(3, RGB(205,205,0), "Yellow"),
        IndexedColor(4, RGB(0,0,238), "Blue"),
        IndexedColor(5, RGB(205,0,205), "Magenta"),
        IndexedColor(6, RGB(0,205,205), "Cyan"),
        IndexedColor(7, RGB(229,229,229), "White"))
    private val colors8Hex = listOf("000000", "cd0000", "00cd00", "cdcd00", "0000ee", "cd00cd", "00cdcd", "e5e5e5")
    private val colors8Int = listOf(0x000000, 0xcd0000, 0x00cd00, 0xcdcd00, 0x0000ee, 0xcd00cd, 0x00cdcd, 0xe5e5e5)
    private val j = Json { serializersModule = buildSerializersModule(colors8) }
    private val uuid = "00010203-0405-0607-0809-0a0b0c0d0e0f"

    @Test fun testRGBSerializer() {
        for (idx in colors8.indices) {
            val fromStr = RGB.fromHexString(colors8Hex[idx])
            assertEquals(colors8[idx].rgb, fromStr)
            assertEquals(colors8Hex[idx], colors8[idx].rgb.toHexString().lowercase())
            val fromInt = RGB.fromInt(colors8Int[idx])
            assertEquals(colors8[idx].rgb, fromInt)
            assertEquals(colors8Int[idx], colors8[idx].rgb.toInt())
        }

        assertThrows<Exception> { RGB.fromHexString("") }
        assertThrows<Exception> { RGB.fromHexString("123") }
        assertThrows<Exception> { RGB.fromHexString("00000") }
        assertThrows<Exception> { RGB.fromHexString("0000000") }
        assertThrows<Exception> { RGB.fromHexString("00000g") }
        assertThrows<Exception> { RGB.fromHexString("000000 ") }
        assertThrows<Exception> { RGB.fromHexString(" 000000 ") }
    }

    @Test fun testColorTypeSerializer() {
        assertEquals("\"rgb\"", j.encodeToString(ColorType.RGB))
        assertEquals("\"indexed\"", j.encodeToString(ColorType.INDEXED))
        assertEquals(ColorType.RGB, j.decodeFromString("\"rgb\""))
        assertEquals(ColorType.INDEXED, j.decodeFromString("\"indexed\""))
    }

    @Test fun testSignColorSerializer() {
        val orangeJson = """{"type":"rgb","rgb":"ff7f00"}"""
        val orange: SignColor = RGBColor(RGB(255, 127, 0))
        val purpleJson = """{"type":"indexed","index":5,"rgb":"ff00ff","name":"Purple"}"""
        val purple: SignColor = IndexedColor(5, RGB(255, 0, 255), "Purple")

        assertEquals(orange, j.decodeFromString(orangeJson))
        assertEquals(purple, j.decodeFromString(purpleJson))
        assertEquals(orangeJson, j.encodeToString(orange))
        assertEquals(purpleJson, j.encodeToString(purple))
    }

    @Test fun testQueryResponseSerializer() {
        val jsonText = """{"minApi":1,"maxApi":2,"uuid":"$uuid","name":"Bob"}"""
        val expected = QueryResponse(1, 2, Uuid.parse(uuid), "Bob")
        val parsed: QueryResponse = j.decodeFromString(jsonText)

        assertEquals(expected.minApi, parsed.minApi)
        assertEquals(expected.maxApi, parsed.maxApi)
        assertEquals(expected.uuid, parsed.uuid)
        assertEquals(expected.name, parsed.name)
        assertEquals(jsonText, j.encodeToString(expected))
    }

    @Test fun testStatusResponseSerializerRGB() {
        val jsonText = """{"text":"Wow",""" +
                """"bg":{"type":"rgb","rgb":"ffffff"},""" +
                """"fg":{"type":"rgb","rgb":"ffbf00"},""" +
                """"type":"rgb"}"""
        val expected = StatusResponse("Wow", type = ColorType.RGB,
            bg = RGBColor(RGB(255, 255, 255)), fg = RGBColor(RGB(255,191,0)))
        val parsed: StatusResponse = j.decodeFromString(jsonText)

        assertEquals(expected.text, parsed.text)
        assertEquals(expected.fg, parsed.fg)
        assertEquals(expected.bg, parsed.bg)
        assertEquals(expected.type, parsed.type)
        assertNull(parsed.colors)
        assertEquals(jsonText, j.encodeToString(expected))
    }

    @Test fun testStatusResponseSerializerIndexed() {
        val jsonText = """{"text":"yay!",""" +
                """"bg":{"type":"indexed","index":1,"rgb":"ffffff","name":"White"},""" +
                """"fg":{"type":"indexed","index":0,"rgb":"000000","name":"Black"},""" +
                """"type":"indexed",""" +
                """"colors":[["000000","Black"],["ffffff","White"]]}"""
        val bw = listOf(IndexedColor(0, RGB(0,0,0), "Black"),
            IndexedColor(1, RGB(255,255,255), "White"))
        val expected = StatusResponse("yay!",bw[1], bw[0], ColorType.INDEXED, bw)
        val parsed: StatusResponse = j.decodeFromString(jsonText)

        assertEquals(expected.text, parsed.text)
        assertEquals(expected.fg, parsed.fg)
        assertEquals(expected.bg, parsed.bg)
        assertEquals(expected.type, parsed.type)
        assertContentEquals(expected.colors, parsed.colors)
        assertEquals(jsonText, j.encodeToString(expected))
    }

    @Test fun testUpdateRequestIndexed() {
        val reqJson = """{"text":"Cool beans","bg":7,"fg":4}"""
        val req = UpdateRequest("Cool beans", colors8[7], colors8[4])

        assertEquals(req, j.decodeFromString(reqJson))
        assertEquals(reqJson, j.encodeToString(req))
    }

    @Test fun testUpdateRequestRGB() {
        val reqJson = """{"text":"hot","bg":"${colors8Hex[7]}","fg":"${colors8Hex[1]}"}"""
        val req = UpdateRequest("hot", RGBColor(colors8[7].rgb), RGBColor(colors8[1].rgb))

        assertEquals(req, j.decodeFromString(reqJson))
        assertEquals(reqJson, j.encodeToString(req))
    }
}
