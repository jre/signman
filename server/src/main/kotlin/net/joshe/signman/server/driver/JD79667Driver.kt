package net.joshe.signman.server.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.joshe.signman.server.Config
import net.joshe.signman.server.driver.GpioDevice.PinState.HIGH
import net.joshe.signman.server.driver.GpioDevice.PinState.LOW
import org.slf4j.LoggerFactory

// https://www.good-display.com/companyfile/1418.html

class JD79667Driver(conf: Config) : IndexedSignDriver() {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val mutex = Mutex()
    private val spi = SpiDevice.get(conf.driver!!)
    private val gpio = GpioDevice.get(conf.driver!!)
    private val busyLowPin = conf.driver!!.gpio!!.busyPin!!
    private val resetLowPin = conf.driver!!.gpio!!.rstPin!!
    private val cmdLowPin = conf.driver!!.gpio!!.rstPin!!
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

    /*
    commands on page 11 of https://cdn-shop.adafruit.com/product-files/6414/P6414_C22271-001_datasheet_ZJY180384-0352AJH-E5______.pdf
    device flow:
       VDD on
       HW Reset
       Check Busy_n (loop)
       Initial code
       DTM1 (R10h)
       Power ON (R04h)
       Check Busy_n (loop)
       Refresh (R12h==00)
       Check Busy_n (loop)
       Power OFF (R02h)
       Check Busy_n (loop)
       DSLP (R07h==A5h)
    */

    private suspend fun reset() {
        // Datasheet doesn't specify reset timing, this is just a guess
        gpio.setPin(resetLowPin, LOW)
        delay(timeMillis = 1)
        gpio.setPin(resetLowPin, HIGH)
    }

    private suspend fun busyWait() {
        while (gpio.getPin(busyLowPin) == LOW)
            delay(timeMillis = 1)
    }

    private suspend fun transmitCmd(cmd: Cmd) {
        gpio.setPin(cmdLowPin, LOW)
        spi.write(cmd.cmd)
        if (cmd.param != null) {
            gpio.setPin(cmdLowPin, HIGH)
            spi.write(cmd.param)
        }
    }

    private suspend fun transmitBytes(cmd: Cmd, bytes: ByteArray) {
        transmitCmd(cmd)
        gpio.setPin(cmdLowPin, HIGH)
        spi.write(bytes)
    }

    private suspend fun checkRevision(): Boolean {
        transmitCmd(Cmd.REV)
        val rev = spi.read(3)
        if (rev.contentEquals(validRevision))
            return true
        log.error("Failed to read chip revision: expected ${validRevision.toHexString()} but found ${rev.toHexString()}")
        return false
    }

    override suspend fun writePixels(pixels: ByteArray): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            reset()
            busyWait()

            if (!initialized) {
                if (checkRevision())
                    initialized = true
                else
                    return@withLock
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
