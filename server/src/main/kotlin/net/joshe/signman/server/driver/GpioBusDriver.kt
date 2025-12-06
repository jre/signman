package net.joshe.signman.server.driver

typealias GpioPin = Int
enum class GpioPinState { INACTIVE, ACTIVE }
enum class GpioPinDirection { IN, OUT }
data class GpioPinConfig(
    val direction: GpioPinDirection,
    val initial: GpioPinState,
    val activeLow: Boolean = false)

interface GpioBusDriver : BusDriver {
    suspend fun gpioSetup(pinConfig: Map<GpioPin, GpioPinConfig>)
    suspend fun gpioGet(pins: List<GpioPin>): List<GpioPinState>
    suspend fun gpioSet(state: Map<GpioPin, GpioPinState>)
    suspend fun gpioGet(pin: GpioPin): GpioPinState = gpioGet(listOf(pin)).first()
    suspend fun gpioSet(pin: GpioPin, state: GpioPinState) = gpioSet(mapOf(pin to state))
}
