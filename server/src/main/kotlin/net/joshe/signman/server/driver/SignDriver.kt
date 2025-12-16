package net.joshe.signman.server.driver

import net.joshe.signman.server.Config
import java.awt.image.BufferedImage

interface SignDriver {
    suspend fun write(img: BufferedImage)

    enum class Rotation {
        D0, D90, D180, D270;

        fun translatePixelCoords(conf: Config.SignConfig, x: Int, y: Int, invert: Boolean = false): Pair<Int,Int> {
            val width = if (invert) conf.height else conf.width
            val height = if (invert) conf.width else conf.height
            return when (this) {
                D0 -> Pair(x, y)
                D90 -> Pair(width - 1 - y, x)
                D180 -> Pair(width - 1 - x, height - 1 - y)
                D270 -> Pair(y, height - 1 - x)
            }
        }

        fun translatePixelIndex(conf: Config.SignConfig, idx: Int, invert: Boolean = false)
        = pixelIndexCoords(conf, idx, invert = invert).let { (x, y) ->
            translatePixelCoords(conf, x = x, y = y, invert = invert).let { (newX, newY) ->
                pixelCoordsIndex(conf, x = newX, y = newY, invert = invert)
            }
        }
    }

    companion object {
        internal fun pixelIndexCoords(conf: Config.SignConfig, idx: Int, invert: Boolean = false)
                = (if (invert) conf.height else conf.width).let { Pair(idx % it, idx / it) }
        internal fun pixelCoordsIndex(conf: Config.SignConfig, x: Int, y: Int, invert: Boolean = false)
                = (if (invert) conf.height else conf.width).let { y * it + x }
    }
}
