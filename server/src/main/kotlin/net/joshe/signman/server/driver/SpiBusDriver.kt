package net.joshe.signman.server.driver

interface SpiBusDriver : BusDriver {
    suspend fun spiSetup(hz: Int, mode: Int, lsbFirst: Boolean = false, halfDuplex: Boolean = false)
    suspend fun spiTransaction(output: ByteArray? = null, inputLen: Int? = null): ByteArray?
    suspend fun spiRead(count: Int): ByteArray = spiTransaction(inputLen = count)!!
    suspend fun spiWrite(output: UByte) { spiTransaction(output = byteArrayOf(output.toByte())) }
    suspend fun spiWrite(output: ByteArray) { spiTransaction(output = output) }
}
