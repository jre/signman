package net.joshe.signman.server.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.joshe.signman.server.Config
import net.joshe.signman.server.DeviceException
import net.joshe.signman.server.driver.GpioPinDirection.IN
import net.joshe.signman.server.driver.GpioPinDirection.OUT
import net.joshe.signman.server.driver.GpioPinState.ACTIVE
import net.joshe.signman.server.driver.GpioPinState.INACTIVE

// https://www.good-display.com/companyfile/1418.html

class JD79667Driver(private val conf: Config.JD79667DriverConfig, private val gpio: GpioBusDriver, private val spi: SpiBusDriver
) : IndexedSignDriver() {
    companion object {
        // Datasheet says minimum clock cycle is 100ns (10MHz) for write and 150ns (6.6_MHz) for read
        private const val SPI_HZ = 6000000
    }

    private val mutex = Mutex()
    private var initialized = false

    enum class Cmd(val cmd: UByte, val param: UByte? = null) {
        POF(0x02U, param = 0x00U),   // Power On
        PON(0x04U),                  // Power Off
        DSLP(0x07U, param = 0xa5U),  // Deep Sleep
        DTM(0x10U),                  // Data transmission start
        DRF(0x12U, param = 0x00U),   // Display refresh
        REV(0x70U);                  // Read Hardware Revision
    }

    private val validRevision = byteArrayOf(0x03, 0x02, 0x01)

    private suspend fun reset() {
        // Datasheet doesn't specify reset timing, this is just a guess
        gpio.gpioSet(conf.rstPin, ACTIVE)
        delay(timeMillis = 1)
        gpio.gpioSet(conf.rstPin, INACTIVE)
    }

    private suspend fun busyWait() {
        while (gpio.gpioGet(conf.busyPin) == ACTIVE)
            delay(timeMillis = 1)
    }

    private suspend fun transmitCmd(cmd: Cmd) {
        gpio.gpioSet(conf.isDataPin, INACTIVE)
        spi.spiWrite(cmd.cmd)
        if (cmd.param != null) {
            gpio.gpioSet(conf.isDataPin, ACTIVE)
            spi.spiWrite(cmd.param)
        }
    }

    private suspend fun transmitBytes(cmd: Cmd, bytes: ByteArray) {
        transmitCmd(cmd)
        gpio.gpioSet(conf.isDataPin, ACTIVE)
        spi.spiWrite(bytes)
    }

    private suspend fun checkRevision() {
        transmitCmd(Cmd.REV)
        val rev = spi.spiRead(3)
        if (!rev.contentEquals(validRevision))
            throw DeviceException("Failed to read JD79667 chip revision: expected ${
                validRevision.toHexString()} but found ${rev.toHexString()}")
    }

    override suspend fun writePixels(pixels: ByteArray): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!initialized) {
                gpio.gpioSetup(mapOf(
                    conf.isDataPin to GpioPinConfig(direction = OUT, initial = INACTIVE),
                    conf.rstPin to GpioPinConfig(direction = OUT, initial = ACTIVE, activeLow = true),
                    conf.busyPin to GpioPinConfig(direction = IN, initial = ACTIVE, activeLow = true)))
                spi.spiSetup(mode = 0, lsbFirst = false, hz = SPI_HZ, halfDuplex = true)
            }

            // Based on the "Typical operating sequence" on page 33 of
            // https://cdn-shop.adafruit.com/product-files/6414/P6414_C22271-001_datasheet_ZJY180384-0352AJH-E5______.pdf
            reset()
            busyWait()

            if (!initialized) {
                checkRevision()
                initialized = true
            }

            transmitBytes(Cmd.DTM, pixels)
            transmitCmd(Cmd.PON)
            busyWait()
            transmitCmd(Cmd.DRF)
            busyWait()
            transmitCmd(Cmd.POF)
            busyWait()
            transmitCmd(Cmd.DSLP)
        }
    }
}
