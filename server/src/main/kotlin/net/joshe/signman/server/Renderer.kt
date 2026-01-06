package net.joshe.signman.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.joshe.signman.server.driver.SignDriver
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.io.ByteArrayOutputStream
import java.lang.Integer.min
import javax.imageio.ImageIO

class Renderer(configuration: Config, private val driver: SignDriver?) {
    private val mutex = Mutex()
    private val conf = configuration.sign
    private val margin = min(conf.width, conf.height) / 10 // XXX
    private var lastFontSize = 12

    val img = when (conf) {
        is Config.RGBSignConfig -> BufferedImage(conf.width, conf.height, BufferedImage.TYPE_INT_RGB)
        is Config.IndexedSignConfig -> conf.palette.let { colors ->
            val bits = 1.rangeTo(16).first { 1.shl(it - 1) >= colors.size }
            val r = colors.map { it.rgb.r.toByte() }.toByteArray()
            val g = colors.map { it.rgb.g.toByte() }.toByteArray()
            val b = colors.map { it.rgb.b.toByte() }.toByteArray()
            BufferedImage(conf.width, conf.height, BufferedImage.TYPE_BYTE_INDEXED,
                IndexColorModel(bits, colors.size, r, g, b))
        }
    }

    suspend fun render(state: State.Snapshot) = mutex.withLock {
        val g = img.createGraphics()
        val metrics = findFont(g, state.text, conf.font, lastFontSize)
        lastFontSize = metrics.font.size
        val x = (conf.width.toFloat() - metrics.width) / 2
        val y = (conf.height.toFloat() - metrics.height) / 2 + metrics.baselineY

        g.background = Color(state.bg.rgb.r, state.bg.rgb.g, state.bg.rgb.b)
        g.clearRect(0, 0, conf.width, conf.height)
        g.color = Color(state.fg.rgb.r, state.fg.rgb.g, state.fg.rgb.b)
        g.font = metrics.font
        g.drawString(state.text, x, y)

        driver?.write(img)

        ByteArrayOutputStream().also { stream ->
            ImageIO.write(img, "png", stream)
        }.toByteArray()!!
    }

    private fun findFont(g: Graphics2D, text: String, fontName: String, fontSize: Int): Metrics {
        val targetWidth = (conf.width - margin).toFloat()
        val targetHeight = (conf.height - margin).toFloat()
        var met = Metrics.get(g, text, fontName, fontSize)

        while (met.width > targetWidth || met.height > targetHeight)
            met = Metrics.get(g, text, fontName, met.font.size - 1)

        if (met.width < targetWidth && met.height < targetHeight) {
            var prev = met
            while (met.width < targetWidth && met.height < targetHeight) {
                prev = met
                met = Metrics.get(g, text, fontName, met.font.size + 1)
            }
            met = prev
        }

        return met
    }

    private data class Metrics(val font: Font, val width: Float, val height: Float, val baselineY: Float) {
        companion object {
            fun get(g: Graphics2D, text: String, fontName: String, fontSize: Int): Metrics {
                val font = Font(fontName, Font.PLAIN, fontSize)
                val fm = g.getFontMetrics(font)
                val lm = fm.getLineMetrics(text, g)
                val bounds = fm.getStringBounds(text, g)
                val width = bounds.width.toFloat()
                val height = bounds.height.toFloat()
                val yOff = lm.ascent
                return Metrics(font = font, width = width, height = height, baselineY = yOff)
            }
        }
    }
}
