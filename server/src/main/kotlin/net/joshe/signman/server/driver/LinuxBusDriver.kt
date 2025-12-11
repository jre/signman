package net.joshe.signman.server.driver

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.joshe.signman.server.Config
import net.joshe.signman.server.NativeLoader
import kotlin.experimental.and
import kotlin.experimental.or

class LinuxBusDriver(config: Config) : GpioBusDriver, SpiBusDriver {
    init {
        NativeLoader.load("signman-native-linux")
    }

    private val gpioConf = config.driver!!.gpio as Config.LinuxGpioBusDriverConfig
    private val gpioMutex = Mutex()
    private var gpioChip: Long? = null

    override suspend fun gpioSetup(pinConfig: Map<GpioPin, GpioPinConfig>) = gpioMutex.withLock {
        if (gpioChip != null)
            return

        Runtime.getRuntime().addShutdownHook(Thread {
            gpioChip?.let { chip ->
                gpioChip = null
                try {
                    NativeLinux.gpio_close(chip)
                } catch (_: Exception) {}
            }
        })

        val lines = pinConfig.keys.sorted().toIntArray()
        gpioChip = NativeLinux.gpio_open(
            gpioConf.device.absolutePath, CONSUMER, lines,
            lines.map { pinConfig.getValue(it).toNative() }.toByteArray())
    }

    override suspend fun gpioGet(pins: List<GpioPin>): List<GpioPinState>
            = NativeLinux.gpio_get(gpioChip!!, pins.toIntArray()).map(::fromNative)

    override suspend fun gpioSet(state: Map<GpioPin, GpioPinState>) {
        val lines = state.keys.sorted().toIntArray()
        NativeLinux.gpio_set(gpioChip!!, lines, lines.map { state.getValue(it).toNative() }.toByteArray())
    }

    private val spiConf = config.driver!!.spi as Config.LinuxSpiBusDriverConfig
    private val spiMutex = Mutex()
    private var spiFd: Int? = null
    private var spiHalfDuplex = false

    override suspend fun spiSetup(hz: Int, mode: Int, lsbFirst: Boolean, halfDuplex: Boolean) = spiMutex.withLock {
        if (spiFd != null)
            return

        Runtime.getRuntime().addShutdownHook(Thread {
            spiFd?.let { fd ->
                spiFd = null
                try {
                    NativeLinux.spi_close(fd)
                } catch (_: Exception) {}
            }
        })

        check(mode <= NativeLinux.SPI_CFG_MODE_MASK)

        spiHalfDuplex = halfDuplex
        spiFd = NativeLinux.spi_open(spiConf.device.absolutePath, hz, mode
            .or(if (lsbFirst) NativeLinux.SPI_CFG_LSB_FIRST else 0)
            .or(if (halfDuplex) NativeLinux.SPI_CFG_HALF_DUPLEX else 0))
    }

    override suspend fun spiTransaction(output: ByteArray?, inputLen: Int?, split: Boolean): ByteArray? {
        check(!spiHalfDuplex || (output?.size ?: 0) == 0 || (inputLen ?: 0) == 0)
        return NativeLinux.spi_io(spiFd!!, output, inputLen ?: 0, split)
    }

    companion object {
        private const val CONSUMER = "signman-server"

        private fun GpioPinState.toNative(): Byte
        = if (this == GpioPinState.ACTIVE) NativeLinux.PIN_STATE_ACTIVE else NativeLinux.PIN_STATE_INACTIVE

        private fun GpioPinConfig.toNative(): Byte = initial.toNative()
            .or(if (direction == GpioPinDirection.OUT) NativeLinux.PIN_DIR_OUT else NativeLinux.PIN_DIR_IN)
            .or(if (activeLow) NativeLinux.PIN_ACTIVE_LOW else NativeLinux.PIN_ACTIVE_HIGH)

        private fun fromNative(b: Byte): GpioPinState
        = if (b.and(NativeLinux.PIN_STATE_MASK) == NativeLinux.PIN_STATE_ACTIVE)
            GpioPinState.ACTIVE else GpioPinState.INACTIVE
    }
}
