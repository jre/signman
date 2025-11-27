package net.joshe.signman.server

import net.joshe.signman.api.ColorType
import net.joshe.signman.api.SignColor
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.io.ByteArrayOutputStream
import java.lang.Integer.min
import javax.imageio.ImageIO

class Renderer(private val conf: Config.SignConfig) {
    private val margin = min(conf.width, conf.height) / 10 // XXX
    private var lastFontSize = 12

    val img = when (conf.type) {
        ColorType.RGB -> BufferedImage(conf.width, conf.height, BufferedImage.TYPE_INT_RGB)
        ColorType.INDEXED -> conf.colors!!.let { colors ->
            val bits = 1.rangeTo(16).first { 1.shl(it - 1) >= colors.size }
            val r = colors.map { it.rgb.r.toByte() }.toByteArray()
            val g = colors.map { it.rgb.g.toByte() }.toByteArray()
            val b = colors.map { it.rgb.b.toByte() }.toByteArray()
            BufferedImage(conf.width, conf.height, BufferedImage.TYPE_BYTE_INDEXED,
                IndexColorModel(bits, colors.size, r, g, b))
        }
    }

    fun render(text: String, fg: SignColor, bg: SignColor) {
        val g = img.createGraphics()
        val metrics = findFont(g, text, conf.font, lastFontSize)
        lastFontSize = metrics.font.size
        val x = (conf.width.toFloat() - metrics.width) / 2
        val y = (conf.height.toFloat() - metrics.height) / 2 + metrics.baselineY

        g.background = Color(bg.rgb.r, bg.rgb.g, bg.rgb.b)
        g.clearRect(0, 0, conf.width, conf.height)
        g.color = Color(fg.rgb.r, fg.rgb.g, fg.rgb.b)
        g.font = metrics.font
        g.drawString(text, x, y)
    }

    fun convertPng() = ByteArrayOutputStream().also { out ->
        ImageIO.write(img, "png", out)
    }.toByteArray()!!

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
