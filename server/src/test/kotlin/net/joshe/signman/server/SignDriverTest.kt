package net.joshe.signman.server

import net.joshe.signman.api.RGB
import net.joshe.signman.api.RGBColor
import net.joshe.signman.server.driver.SignDriver.Companion.pixelCoordsIndex
import net.joshe.signman.server.driver.SignDriver.Companion.pixelIndexCoords
import net.joshe.signman.server.driver.SignDriver.Rotation
import net.joshe.signman.server.driver.SignDriver.Rotation.D0
import net.joshe.signman.server.driver.SignDriver.Rotation.D90
import net.joshe.signman.server.driver.SignDriver.Rotation.D180
import net.joshe.signman.server.driver.SignDriver.Rotation.D270
import kotlin.test.Test
import kotlin.test.assertEquals

class SignDriverTest {
    val conf = Config.SignConfig(width = 20, height = 10, color = Config.RGBColorConfig(
        foreground = RGBColor(RGB(0, 0, 0)),
        background = RGBColor(RGB(255, 255, 255))))

    @Test fun testPixelPosition() {
        assertEquals(23, pixelCoordsIndex(conf, 3, 1))
        assertEquals(187, pixelCoordsIndex(conf, 7, 9, invert = false))
        assertEquals(32, pixelCoordsIndex(conf, 2, 3, invert = true))
        assertEquals(Pair(7, 9), pixelIndexCoords(conf, 187))
        assertEquals(Pair(3, 1), pixelIndexCoords(conf, 23, invert = false))
        assertEquals(Pair(2, 3), pixelIndexCoords(conf, 32, invert = true))
    }

    private fun testRotate(rot: Rotation, x: Int, y: Int, expectX: Int, expectY: Int, expectIdx: Int, invert: Boolean) {
        val idx = pixelCoordsIndex(conf, x, y, invert = invert)
        val (newX, newY) = rot.translatePixelCoords(conf, x, y, invert = invert)
        val newIdx = rot.translatePixelIndex(conf, idx, invert = invert)
        assertEquals(Triple(expectX, expectY, expectIdx),
            Triple(newX, newY, newIdx))
    }

    @Test fun testRot0() = testRotate(D0, 1, 5, 1, 5, 101, invert = false)
    @Test fun testRot90() = testRotate(D90, 2, 6, 13, 2, 53, invert = false)
    @Test fun testRot180() = testRotate(D180, 3, 7, 16, 2, 56, invert = false)
    @Test fun testRot270() = testRotate(D270, 4, 8, 8, 5, 108, invert = false)

    @Test fun testRot0Inv() = testRotate(D0, 1, 5, 1, 5, 51, invert = true)
    @Test fun testRot90Inv() = testRotate(D90, 2, 6, 3, 2, 23, invert = true)
    @Test fun testRot180Inv() = testRotate(D180, 3, 7, 6, 12, 126, invert = true)
    @Test fun testRot270Inv() = testRotate(D270, 4, 8, 8, 15, 158, invert = true)
}
