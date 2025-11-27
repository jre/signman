package net.joshe.signman.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule

@Serializable(with = RGB.Serializer::class)
data class RGB(val r: Int, val g: Int, val b: Int) {
    init {
        check(r in 0..< 0x100 && g in 0..< 0x100 && b in 0..< 0x100)
    }

    companion object {
        fun fromHexString(str: String): RGB {
            check(str.length == 6)
            val (r, g, b) = str.chunked(2).map { it.toInt(16) }
            return RGB(r, g, b)
        }

        fun fromInt(value: Int) = RGB(r = value / 0x10000 % 0x100, g = value / 0x100 % 0x100, b = value % 0x100)
    }

    fun toHexString() = String.format("%02x%02x%02x", r, g, b)
    fun toInt() = r * 0x10000 + g * 0x100 + b

    private class Serializer : KSerializer<RGB> {
        override val descriptor = PrimitiveSerialDescriptor(kind = PrimitiveKind.STRING,
            serialName = RGB::class.qualifiedName!!)
        override fun deserialize(decoder: Decoder) = fromHexString(decoder.decodeString())
        override fun serialize(encoder: Encoder, value: RGB) = encoder.encodeString(value.toHexString())
    }
}

@Serializable
enum class ColorType {
    @SerialName("rgb") RGB,
    @SerialName("indexed") INDEXED;
}

@Serializable
sealed class SignColor {
    abstract val rgb: RGB
}

@Serializable
@SerialName("rgb")
data class RGBColor(override val rgb: RGB) : SignColor()

@Serializable
@SerialName("indexed")
data class IndexedColor(val index: Int, override val rgb: RGB, val name: String = "") : SignColor()

@Serializable
data class QueryResponse(
    val minApi: Int,
    val maxApi: Int,
    val uuid: String,
    val name: String)

@Serializable
data class StatusResponse(
    val uuid: String,
    val name: String,
    val text: String,
    val bg: SignColor,
    val fg: SignColor,
    val type: ColorType,
    @Serializable(with = IndexedColorListJsonSerializer::class)
    val colors: List<IndexedColor>? = null)

@Serializable
data class UpdateRequest(
    val text: String,
    @Contextual val bg: SignColor,
    @Contextual val fg: SignColor)

fun buildSerializersModule(colors: List<IndexedColor>?) = SerializersModule {
    contextual(SignColor::class, BareSignColorJsonSerializer(colors))
}

internal class IndexedColorListJsonSerializer : JsonTransformingSerializer<List<IndexedColor>>(
    ListSerializer(IndexedColor.serializer())) {
    override fun transformDeserialize(element: JsonElement) = JsonArray(element.jsonArray
        .mapIndexed { idx, subElement ->
            val pair = subElement.jsonArray
            check(pair.size == 2)
            JsonObject(mapOf("index" to JsonPrimitive(idx), "rgb" to pair[0], "name" to pair[1]))
        })

    override fun transformSerialize(element: JsonElement) = JsonArray(element.jsonArray
        .mapIndexed { idx, subElement ->
            val obj = subElement.jsonObject
            check(idx == obj.getValue("index").jsonPrimitive.int)
            JsonArray(listOf(obj.getValue("rgb"), obj.getValue("name")))
        })
}

internal class BareSignColorJsonSerializer(private val colors: List<IndexedColor>?)
    : JsonTransformingSerializer<SignColor>(SignColor.serializer()) {
    override fun transformDeserialize(element: JsonElement) = JsonObject(element.jsonPrimitive.let { elm ->
        if (elm.isString)
            mapOf("type" to JsonPrimitive("rgb"), "rgb" to elm)
        else
            colors!![elm.int].let { color ->
                mapOf("type" to JsonPrimitive("indexed"),
                    "index" to elm,
                    "rgb" to JsonPrimitive(color.rgb.toHexString()),
                    "name" to JsonPrimitive(color.name))
            }
    })

    override fun transformSerialize(element: JsonElement) = element.jsonObject.let { obj ->
        obj.getOrElse("index") { obj.getValue("rgb") }
    }
}
