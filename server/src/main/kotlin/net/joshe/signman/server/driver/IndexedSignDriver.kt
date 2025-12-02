package net.joshe.signman.server.driver

import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.DataBufferByte
import java.awt.image.IndexColorModel

sealed class IndexedSignDriver() : SignDriver {
    override suspend fun write(img: BufferedImage) {
        val model = img.colorModel
        check(model is IndexColorModel)
        val buf = img.data.dataBuffer
        check(buf.dataType == DataBuffer.TYPE_BYTE && buf is DataBufferByte)
        writePixels(buf.data)
    }

    abstract suspend fun writePixels(pixels: ByteArray)
}
