package net.joshe.signman.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.decodeFromStringMap
import kotlinx.serialization.properties.encodeToStringMap
import net.joshe.signman.api.ColorType
import net.joshe.signman.api.IndexedColor
import net.joshe.signman.api.RGB
import net.joshe.signman.api.RGBColor
import net.joshe.signman.api.SignColor
import net.joshe.signman.api.indexedColorTypeKey
import net.joshe.signman.api.rgbColorTypeKey
import java.awt.Font
import java.io.File
import java.io.InputStream
import java.io.OutputStream


@Serializable
data class Config(val server: ServerConfig, val sign: SignConfig, val auth: AuthConfig,
                  val driver: DriverConfig? = null, val name: String = "Signman Server") {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun load(stream: InputStream) = Properties.decodeFromStringMap<Config>(java.util.Properties().apply {
            load(stream) }.map { (k, v) ->
            Pair(k as String, v.toString().trim(' ', '\t')) }.toMap())
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun store(stream: OutputStream, comment: String? = null) {
        java.util.Properties().apply {
            putAll(  Properties.encodeToStringMap(this@Config))
            store(stream, comment)
        }
    }

    @Serializable
    data class ServerConfig(
        val port: Int = 80,
        @Serializable(with = FileAsStringSerializer::class)
        val directory: File)

    @Serializable
    data class SignConfig(val font: String = Font.SERIF, val width: Int, val height: Int, val color: ColorConfig)

    @Serializable
    sealed class ColorConfig {
        abstract val foreground: SignColor
        abstract val background: SignColor
        val type: ColorType get() = when (this) {
            is RGBColorConfig -> ColorType.RGB
            is IndexedColorConfig -> ColorType.INDEXED
        }
    }

    @Serializable
    @SerialName(rgbColorTypeKey)
    data class RGBColorConfig(
        @Serializable(with = BareRGBColorSerializer::class)
        override val foreground: RGBColor,
        @Serializable(with = BareRGBColorSerializer::class)
        override val background: RGBColor) : ColorConfig()

    @Serializable
    @SerialName(indexedColorTypeKey)
    data class IndexedColorConfig(
        @SerialName("foreground") val foregroundIndex: Int,
        @SerialName("background") val backgroundIndex: Int,
        @Serializable(with = IndexedColorPairListSerializer::class)
        val palette: List<IndexedColor>) : ColorConfig() {
        @Transient override val foreground = palette[foregroundIndex]
        @Transient override val background = palette[backgroundIndex]
    }

    @Serializable
    data class AuthConfig(
        val type: AuthType,
        @Serializable(with = FileAsStringSerializer::class)
        val path: File)

    @Serializable
    data class DriverConfig(
        val name: Driver,
        @Serializable(with = FileAsStringSerializer::class)
        val device: File)

    @Serializable
    enum class AuthType { @SerialName("file") FILE; }

    enum class Driver { @SerialName("placeholder") PLACEHOLDER; }

    private class FileAsStringSerializer : KSerializer<File> {
        override val descriptor = PrimitiveSerialDescriptor(kind = PrimitiveKind.STRING,
            serialName = this::class.qualifiedName!!)
        override fun deserialize(decoder: Decoder) = File(decoder.decodeString())
        override fun serialize(encoder: Encoder, value: File) = encoder.encodeString(value.absolutePath)
    }

    private class IndexedColorPairSerializer : KSerializer<Pair<RGB,String>> {
        override val descriptor = PrimitiveSerialDescriptor(kind = PrimitiveKind.STRING,
            serialName = "Pair<${RGB::class.qualifiedName!!},String>")

        override fun deserialize(decoder: Decoder) = decoder.decodeString()
            .split(' ', '\t', limit = 2).let { (hex, name) ->
                Pair(RGB.fromHexString(hex), name.trim(' ', '\t'))
            }

        override fun serialize(encoder: Encoder, value: Pair<RGB,String>) = encoder.encodeString(
            "${value.first.toHexString()} ${value.second}")
    }

    private class IndexedColorPairListSerializer : KSerializer<List<IndexedColor>> {
        private val delegate = ListSerializer(IndexedColorPairSerializer())
        override val descriptor = delegate.descriptor

        override fun deserialize(decoder: Decoder) = decoder.decodeSerializableValue(delegate)
            .mapIndexed { idx, (rgb, name) ->
                IndexedColor(index = idx, rgb = rgb, name = name)
            }

        override fun serialize(encoder: Encoder, value: List<IndexedColor>) {
            for (idx in value.indices)
                check(idx == value[idx].index)
            encoder.encodeSerializableValue(delegate, value.map { Pair(it.rgb, it.name) })
        }
    }

    private class BareRGBColorSerializer : KSerializer<RGBColor> {
        override val descriptor = PrimitiveSerialDescriptor(kind = PrimitiveKind.STRING,
            serialName = "${RGBColor::class.qualifiedName}.Bare")
        override fun deserialize(decoder: Decoder) = RGBColor(RGB.fromHexString(decoder.decodeString()))
        override fun serialize(encoder: Encoder, value: RGBColor) = encoder.encodeString(value.rgb.toHexString())
    }
}
