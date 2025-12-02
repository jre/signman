package net.joshe.signman.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.joshe.signman.api.RGB
import net.joshe.signman.api.RGBColor
import net.joshe.signman.api.SignColor
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.CRC32

@Serializable
class State private constructor(
    @SerialName("text") private var _text: String,
    @SerialName("bg") private var _bg: SignColor,
    @SerialName("fg") private var _fg: SignColor,
    @Transient private var renderer: Renderer? = null,
    @Transient private var onUpdate: (State.() -> Unit)? = null) {
    val text: String get() = _text
    val bg: SignColor get() = _bg
    val fg: SignColor get() = _fg
    @Transient lateinit var png: ByteArray private set
    @Transient lateinit var pngETag: String private set
    @Transient var lastModified: Instant = Instant.now()
        private set

    companion object {
        private val defaultFg = RGBColor(RGB(0, 0, 0))
        private val defaultBg = RGBColor(RGB(255, 255, 255))

        suspend fun initialize(renderer: Renderer, text: String = "",
                               fg: SignColor = defaultFg, bg: SignColor = defaultBg,
                               onUpdate: State.() -> Unit
        ) = State(_text = text, _bg = bg, _fg = fg, renderer = renderer, onUpdate = onUpdate).apply {
            wasUpdated(false)
        }

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun load(stream: InputStream, renderer: Renderer, onUpdate: State.() -> Unit) = Json.decodeFromStream<State>(stream)
            .apply {
                this.renderer = renderer
                this.onUpdate = onUpdate
                wasUpdated(false)
            }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun store(stream: OutputStream) = Json.encodeToStream(this, stream)

    private suspend fun wasUpdated(callHook: Boolean = true) {
        renderer?.let { ren ->
            ren.render(text = _text, bg = _bg, fg = _fg)
            png = ren.convertPng()
            pngETag = CRC32().apply { update(png) }.value.toString(32)
        }
        lastModified = Instant.now()
        if (callHook)
            onUpdate?.invoke(this)
    }

    suspend fun erase() {
        _text = ""
        _bg = defaultBg
        _fg = defaultFg
        wasUpdated()
    }

    suspend fun update(text: String, bg: SignColor, fg: SignColor) {
        _text = text
        _bg = bg
        _fg = fg
        wasUpdated()
    }
}
