package net.joshe.signman.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.joshe.signman.api.IndexedColor
import net.joshe.signman.api.RGB
import net.joshe.signman.api.RGBColor
import net.joshe.signman.api.SignColor
import java.io.InputStream
import java.io.OutputStream

class State private constructor(
    private var snap: Snapshot,
    private val onUpdate: (State.(Snapshot) -> Unit)? = null) {
    companion object {
        private val default = Snapshot("",
            fg = RGBColor(RGB(0, 0, 0)),
            bg = RGBColor(RGB(255, 255, 255)))

        fun initialize(text: String = "", fg: SignColor = default.fg, bg: SignColor = default.bg,
                       onUpdate: State.(Snapshot) -> Unit)
                = State(Snapshot(text, bg = bg, fg = fg), onUpdate = onUpdate)

        @OptIn(ExperimentalSerializationApi::class)
        fun load(stream: InputStream, onUpdate: State.(Snapshot) -> Unit)
                = State(Json.decodeFromStream<Snapshot>(stream), onUpdate)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun store(stream: OutputStream) = Json.encodeToStream(snap, stream)

    val snapshot: Snapshot get() = snap

    fun erase(): Snapshot {
        snap = default
        onUpdate?.invoke(this, default)
        return default
    }

    fun update(text: String, bg: SignColor, fg: SignColor): Snapshot {
        val newSnap = Snapshot(text, fg = fg, bg = bg)
        snap = newSnap
        onUpdate?.invoke(this, newSnap)
        return newSnap
    }

    @Serializable
    data class Snapshot(val text: String, val bg: SignColor, val fg: SignColor) {
        private fun ETagger.updateColor(color: SignColor) = when (color) {
            is RGBColor -> update(color.rgb.toInt())
            is IndexedColor -> update(color.index)
        }

        fun eTag() = ETagger().apply {
            update(text)
            updateColor(bg)
            updateColor(fg)
        }.tag
    }
}
