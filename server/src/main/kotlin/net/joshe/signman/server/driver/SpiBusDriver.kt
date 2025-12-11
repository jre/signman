package net.joshe.signman.server.driver

interface SpiBusDriver : BusDriver {
    suspend fun spiSetup(hz: Int, mode: Int, lsbFirst: Boolean = false, halfDuplex: Boolean = false)
    suspend fun spiTransaction(output: ByteArray? = null, inputLen: Int? = null, split: Boolean = false): ByteArray?
    suspend fun spiRead(count: Int, split: Boolean = false): ByteArray = spiTransaction(inputLen = count, split = split)!!
    suspend fun spiWrite(output: UByte) { spiTransaction(output = byteArrayOf(output.toByte())) }
    suspend fun spiWrite(output: ByteArray, split: Boolean = false) { spiTransaction(output = output, split = split) }
    @ExperimentalUnsignedTypes
    suspend fun spiWrite(output: UByteArray, split: Boolean = false) { spiTransaction(output = output.toByteArray(), split = split) }
}
