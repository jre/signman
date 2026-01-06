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
            val fromHex = RGB.fromHexString(colors8Hex[idx])
            assertEquals(colors8[idx].rgb, fromHex)
            assertEquals(colors8Hex[idx], colors8[idx].rgb.toHexString().lowercase())
            val sharpHex = "#" + colors8Hex[idx]
            val fromSharpHex = RGB.fromHexString(sharpHex)
            assertEquals(colors8[idx].rgb, fromSharpHex)
            assertEquals(sharpHex, colors8[idx].rgb.toHexString(true).lowercase())
            val fromInt = RGB.fromInt(colors8Int[idx])
            assertEquals(colors8[idx].rgb, fromInt)
            assertEquals(colors8Int[idx], colors8[idx].rgb.toInt())
        }

        assertThrows<IllegalStateException> { RGB.fromHexString("") }
        assertThrows<IllegalStateException> { RGB.fromHexString("123") }
        assertThrows<IllegalStateException> { RGB.fromHexString("00000") }
        assertThrows<IllegalStateException> { RGB.fromHexString("0000000") }
        assertThrows<NumberFormatException> { RGB.fromHexString("00000g") }
        assertThrows<IllegalStateException> { RGB.fromHexString("000000 ") }
        assertThrows<IllegalStateException> { RGB.fromHexString(" 000000 ") }
        assertThrows<IllegalStateException> { RGB(-1, 0, 0) }
        assertThrows<IllegalStateException> { RGB(256, 0, 0) }
        assertThrows<IllegalStateException> { RGB(0, -1, 0) }
        assertThrows<IllegalStateException> { RGB(0, 256, 0) }
        assertThrows<IllegalStateException> { RGB(0, 0, -1) }
        assertThrows<IllegalStateException> { RGB(0, 0, 256) }
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

    @Test fun testIndexedColorListJsonSerializer() {
        val serializer = IndexedColorListJsonSerializer()
        val goodJson1 = """[["${colors8Hex[0]}","${colors8[0].name}"],["${colors8Hex[1]}","${colors8[1].name}"]]"""
        val goodList1 = listOf(colors8[0], colors8[1])
        val badJson1 = """[["${colors8Hex[3]}"]]"""
        val badJson2 = """[["${colors8Hex[3]}","Gamma","Delta"]]"""

        assertEquals(goodList1, Json.decodeFromString(serializer, goodJson1))
        assertEquals(goodJson1, Json.encodeToString(serializer, goodList1))
        assertThrows<IllegalStateException> { Json.encodeToString(serializer, listOf(colors8[1])) }
        assertThrows<IllegalStateException> { Json.decodeFromString(IndexedColorListJsonSerializer(), badJson1) }
        assertThrows<IllegalStateException> { Json.decodeFromString(IndexedColorListJsonSerializer(), badJson2) }
    }

    @Test fun testBareSignColorJsonSerializer() {
        val serializer = BareSignColorJsonSerializer(colors8)
        val nameSerializer = BareSignColorJsonSerializer(colors8, allowNames = true)
        for (idx in colors8.indices) {
            val idxColor = colors8[idx]
            val idxStr = idxColor.index.toString()
            assertEquals(idxStr, Json.encodeToString(serializer, idxColor))
            assertEquals(idxColor, Json.decodeFromString(serializer, idxStr))
            val nameStr = """"${idxColor.name}""""
            assertThrows<IllegalStateException> { Json.decodeFromString(serializer, nameStr) }
            assertThrows<IllegalStateException> { Json.decodeFromString(serializer, """"nonsense"""") }
            assertEquals(idxColor, Json.decodeFromString(nameSerializer, nameStr))
            assertEquals(idxColor, Json.decodeFromString(nameSerializer, nameStr.uppercase()))
            assertEquals(idxColor, Json.decodeFromString(nameSerializer, nameStr.lowercase()))
            val rgbColor = RGBColor(idxColor.rgb)
            val rgbStr = """"${colors8Hex[idx]}""""
            assertEquals(rgbStr, Json.encodeToString(serializer, rgbColor))
            assertEquals(rgbColor, Json.decodeFromString(serializer, rgbStr))
            assertEquals(rgbColor, Json.decodeFromString(nameSerializer, rgbStr))
        }
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
                """"type":"rgb",""" +
                """"current-bg":{"type":"rgb","rgb":"f8f8f8"},""" +
                """"current-fg":{"type":"rgb","rgb":"ffbf00"},""" +
                """"default-bg":{"type":"rgb","rgb":"ffffff"},""" +
                """"default-fg":{"type":"rgb","rgb":"000000"},""" +
                """"update-tag":"fake"}"""
        val expected = StatusResponse("Wow", type = ColorType.RGB, updateTag = "fake",
            bg = RGBColor(RGB(248, 248, 248)), fg = RGBColor(RGB(255,191,0)),
            defaultBg = RGBColor(RGB(255, 255, 255)), defaultFg = RGBColor(RGB(0,0,0)))
        val parsed: StatusResponse = j.decodeFromString(jsonText)

        assertEquals(expected.text, parsed.text)
        assertEquals(expected.fg, parsed.fg)
        assertEquals(expected.bg, parsed.bg)
        assertEquals(expected.defaultFg, parsed.defaultFg)
        assertEquals(expected.defaultBg, parsed.defaultBg)
        assertEquals(expected.type, parsed.type)
        assertNull(parsed.colors)
        assertEquals(jsonText, j.encodeToString(expected))
    }

    @Test fun testStatusResponseSerializerIndexed() {
        val jsonText = """{"text":"yay!",""" +
                """"type":"indexed",""" +
                """"current-bg":{"type":"indexed","index":1,"rgb":"ffffff","name":"White"},""" +
                """"current-fg":{"type":"indexed","index":0,"rgb":"000000","name":"Black"},""" +
                """"default-bg":{"type":"indexed","index":0,"rgb":"000000","name":"Black"},""" +
                """"default-fg":{"type":"indexed","index":1,"rgb":"ffffff","name":"White"},""" +
                """"colors":[["000000","Black"],["ffffff","White"]],"update-tag":"fake"}"""
        val bw = listOf(IndexedColor(0, RGB(0,0,0), "Black"),
            IndexedColor(1, RGB(255,255,255), "White"))
        val expected = StatusResponse("yay!", ColorType.INDEXED,
            bw[1], bw[0], bw[0], bw[1], bw, "fake")
        val parsed: StatusResponse = j.decodeFromString(jsonText)

        assertEquals(expected.text, parsed.text)
        assertEquals(expected.fg, parsed.fg)
        assertEquals(expected.bg, parsed.bg)
        assertEquals(expected.defaultFg, parsed.defaultFg)
        assertEquals(expected.defaultBg, parsed.defaultBg)
        assertEquals(expected.type, parsed.type)
        assertContentEquals(expected.colors, parsed.colors)
        assertEquals(jsonText, j.encodeToString(expected))
    }

    @Test fun testUpdateRequest() {
        for ((json, req) in listOf(
            Pair(("""{"text":"rad"}"""), UpdateRequest("rad")),
            Pair("""{"text":"Cool beans","bg":7,"fg":4}""",
                UpdateRequest("Cool beans", colors8[7], colors8[4])),
            Pair("""{"text":"hot","bg":"${colors8Hex[7]}","fg":"${colors8Hex[1]}"}""",
                UpdateRequest("hot", RGBColor(colors8[7].rgb), RGBColor(colors8[1].rgb))),
            Pair("""{"text":"bright","fg":"${colors8Hex[7]}"}""", UpdateRequest("bright", fg=RGBColor(colors8[7].rgb))),
            Pair("""{"text":"Moose?","bg":2}""", UpdateRequest("Moose?", bg=colors8[2])))) {
            assertEquals(req, j.decodeFromString(json))
            assertEquals(json, j.encodeToString(req))
        }
    }

    @Test fun testUpdateResponse() {
        val json = """{"update-tag":"fake tag"}"""
        val resp = UpdateResponse("fake tag")
        assertEquals(resp, j.decodeFromString(json))
        assertEquals(json, j.encodeToString(resp))
    }
}
