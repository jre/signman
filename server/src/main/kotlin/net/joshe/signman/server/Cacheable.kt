package net.joshe.signman.server

import org.jetbrains.annotations.TestOnly
import java.util.Objects
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class Cacheable private constructor(val modified: Instant,
                                    val state: State.Snapshot,
                                    val stateETag: String,
                                    val png: ByteArray,
                                    val html: String) {
    val pngETag = ETagger.get(png)
    val htmlETag = ETagger.get(html)

    override fun equals(other: Any?) = this === other || (other is Cacheable && modified == other.modified &&
            stateETag == other.stateETag && pngETag == other.pngETag && htmlETag == other.htmlETag)

    override fun hashCode() = Objects.hash(modified, stateETag, pngETag, htmlETag)

    companion object {
        suspend fun create(config: Config, state: State.Snapshot, renderer: Renderer) = state.eTag().let { stateETag ->
            Cacheable(Clock.System.now(), state, stateETag,
                png = renderer.render(state), html = Server.getHtml(config, state, stateETag))
        }

        @TestOnly
        internal fun create(modified: Instant, state: State.Snapshot, png: ByteArray, html: String)
                = Cacheable(modified, state, state.eTag(), png, html)
    }
}
