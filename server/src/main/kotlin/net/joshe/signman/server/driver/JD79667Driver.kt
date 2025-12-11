@file:OptIn(ExperimentalUnsignedTypes::class)

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
import kotlin.experimental.and

// JD79667 Driver IC Datasheet
// https://www.good-display.com/companyfile/1418.html

// GDEY029F51H_Specification:
// https://www.good-display.com/companyfile/1411.html

class JD79667Driver(configuration: Config, private val gpio: GpioBusDriver, private val spi: SpiBusDriver
) : IndexedSignDriver() {
    companion object {
        // Datasheet says minimum clock cycle is 100ns (10MHz) for write and 150ns (6.6_MHz) for read
        private const val SPI_HZ = 6000000
        private const val MAX_WIDTH = 384
        private const val MAX_HEIGHT = 200
        private val rotation = SignDriver.Rotation.D90
        private val validRevision = byteArrayOf(0x05, 0x02, 0x01)
    }

    private val conf = configuration.driver?.sign as Config.JD79667DriverConfig
    private val signConf = configuration.sign
    private val mutex = Mutex()
    private var initialized = false

    enum class Cmd(val cmd: UByte, vararg val param: UByte) {
        // The undocumented commands here were copied from the GooDisplay GDEY029F51H example code
        PSR(0x00U, 0x0fU, 0x29U),                              // Panel setting register
        PWR(0x01U, 0x07U, 0U),                                 // Power setting register
        POF(0x02U, 0x00U),                                     // Power on
        POFS(0x03U, 0x10U, 0x54U, 0x44U),                      // Power off sequence
        PON(0x04U),                                                       // Power Off
        BTST(0x06U, 5U, 0U, 0x3fU, 0xaU, 0x25U, 0x12U, 0x1aU), // Booster soft start
        DSLP(0x07U, 0xa5U),                                    // Deep Sleep
        DTM(0x10U),                                                       // Data transmission start
        DRF(0x12U, 0x00U),                                     // Display refresh
        PLL(0x30U, 0x08U),                                     // PLL control register
        UK4D(0x4dU, 0x78U),
        CDI(0x50U, 0x37U),                                     // VCOM and data interval
        TCON(0x60U, 2U, 2U),
        TRES(0x61U),                                                      // Resolution setting
        REV(0x70U),                                                       // Read Hardware Revision
        UKA5(0xa5U),
        UKB4(0xb4U, 0xd0U),
        UKB5(0xb5U, 0x03U),
        PWS(0xe3U, 0x22U),                                     // Power saving
        UKE7(0xe7U, 0x1cU),
        UKE9(0xe9U, 0x01U),
        ;
    }

    init {
        check(signConf.width <= MAX_WIDTH && signConf.height <= MAX_HEIGHT)
    }

    private suspend fun reset() {
        // Datasheet doesn't specify reset timing, this is from the GooDisplay GDEY029F51H example code
        gpio.gpioSet(conf.rstPin, INACTIVE)
        delay(timeMillis = 20)
        gpio.gpioSet(conf.rstPin, ACTIVE)
        delay(timeMillis = 40)
        gpio.gpioSet(conf.rstPin, INACTIVE)
        delay(timeMillis = 50)
        busyWait()
    }

    private suspend fun busyWait() {
        while (gpio.gpioGet(conf.busyPin) == ACTIVE)
            delay(timeMillis = 1)
    }

    private suspend fun transmitCmd(cmd: Cmd, wait: Boolean) {
        gpio.gpioSet(conf.isDataPin, INACTIVE)
        spi.spiWrite(cmd.cmd)
        if (cmd.param.isNotEmpty()) {
            gpio.gpioSet(conf.isDataPin, ACTIVE)
            spi.spiWrite(cmd.param)
        }
        if (wait)
            busyWait()
    }

    private suspend fun transmitBytes(cmd: Cmd, bytes: UByteArray, wait: Boolean) {
        transmitCmd(cmd, wait = false)
        gpio.gpioSet(conf.isDataPin, ACTIVE)
        spi.spiWrite(bytes, split = true)
        if (wait)
            busyWait()
    }

    private suspend fun checkRevision() {
        transmitCmd(Cmd.REV, wait = false)
        gpio.gpioSet(conf.isDataPin, ACTIVE)
        val rev = spi.spiRead(3)
        if (!rev.contentEquals(validRevision))
            throw DeviceException("Failed to read JD79667 chip revision: expected ${
                validRevision.toHexString()} but found ${rev.toHexString()}")
    }

    private suspend fun initCode() {
        val res = ubyteArrayOf(
            signConf.height.ushr(8).and(0xff).toUByte(), signConf.height.and(0xff).toUByte(),
            signConf.width.ushr(8).and(0xff).toUByte(), signConf.width.and(0xff).toUByte())

        transmitCmd(Cmd.UK4D, wait = true)
        transmitCmd(Cmd.PSR, wait = true)
        transmitCmd(Cmd.PWR, wait = true)
        transmitCmd(Cmd.POFS, wait = true)
        transmitCmd(Cmd.BTST, wait = true)
        transmitCmd(Cmd.CDI, wait = true)
        transmitCmd(Cmd.TCON, wait = true)
        transmitBytes(Cmd.TRES, res, wait = true)
        transmitCmd(Cmd.UKE7, wait = true)
        transmitCmd(Cmd.PWS, wait = true)
        transmitCmd(Cmd.UKB4, wait = true)
        transmitCmd(Cmd.UKB5, wait = true)
        transmitCmd(Cmd.UKE9, wait = true)
        transmitCmd(Cmd.PLL, wait = true)
        transmitCmd(Cmd.PON, wait = true)
    }

    private fun rotIdx(idx: Int)= SignDriver.pixelIndexCoords(signConf, idx, invert = true).let { (x, y) ->
        val (srcX, srcY) = rotation.translatePixelCoords(signConf, x, y)
        SignDriver.pixelCoordsIndex(signConf, srcX, srcY)
    }

    private fun packPixels(pixels: ByteArray) = 0.until(pixels.size).step(4).map { i ->
        pixels[rotIdx(i + 3)].and(3).toUInt()
            .or(pixels[rotIdx(i + 2)].and(3).toUInt().shl(2))
            .or(pixels[rotIdx(i + 1)].and(3).toUInt().shl(4))
            .or(pixels[rotIdx(i)].and(3).toUInt().shl(6))
            .toUByte()
    }.toUByteArray()

    override suspend fun writePixels(pixels: ByteArray): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!initialized) {
                gpio.gpioSetup(mapOf(
                    conf.isDataPin to GpioPinConfig(direction = OUT, initial = INACTIVE),
                    conf.rstPin to GpioPinConfig(direction = OUT, initial = ACTIVE, activeLow = true),
                    conf.busyPin to GpioPinConfig(direction = IN, initial = ACTIVE, activeLow = true)))
                spi.spiSetup(mode = 0, lsbFirst = false, hz = SPI_HZ, halfDuplex = true)
            }

            reset()

            if (!initialized) {
                checkRevision()
                initialized = true
            }

            initCode()
            transmitBytes(Cmd.DTM, packPixels(pixels), wait = false)
            transmitCmd(Cmd.DRF, wait = true)

            transmitCmd(Cmd.POF, wait = true)
            delay(timeMillis = 100)
            transmitCmd(Cmd.DSLP, wait = false)
            transmitCmd(Cmd.UKA5, wait = false)
        }
    }
}
