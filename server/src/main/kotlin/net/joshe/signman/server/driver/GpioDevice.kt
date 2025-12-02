package net.joshe.signman.server.driver

import net.joshe.signman.server.Config

interface GpioDevice {
    companion object {
        fun get(config: Config.DriverConfig): GpioDevice
                = throw MissingGpioImplementation("No GPIO implementation found")
    }

    suspend fun getPin(pin: Int): PinState
    suspend fun setPin(pin: Int, state: PinState)

    enum class PinState { LOW, HIGH }

    class MissingGpioImplementation(message: String) : Exception(message)
}
