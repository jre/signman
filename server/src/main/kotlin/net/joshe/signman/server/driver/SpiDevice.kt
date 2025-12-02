package net.joshe.signman.server.driver

import net.joshe.signman.server.Config

interface SpiDevice {
    companion object {
        fun get(config: Config.DriverConfig): SpiDevice
                = throw MissingSpiImplementation("No SPI implementation found")
    }

    suspend fun io(input: ByteArray, outLength: Int): ByteArray
    suspend fun read(count: Int): ByteArray
    suspend fun write(input: UByte)
    suspend fun write(input: ByteArray)

    class MissingSpiImplementation(message: String) : Exception(message)
}
