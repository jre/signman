package net.joshe.signman.server

import java.util.zip.CRC32

class ETagger {
    private val crc = CRC32()

    fun reset() { crc.reset()}

    fun update(data: Int) { crc.update(data) }
    fun update(data: ByteArray) { crc.update(data) }
    fun update(data: String) { crc.update(data.encodeToByteArray()) }

    val tag: String get() = crc.eTag()

    companion object {
        private fun CRC32.eTag() = value.toString(32)

        fun get(data: Int) = CRC32().apply { update(data) }.eTag()
        fun get(data: ByteArray) = CRC32().apply{ update(data) }.eTag()
        fun get(data: String) = CRC32().apply{ update(data.encodeToByteArray()) }.eTag()
    }
}
