package net.joshe.signman.server.driver

import java.awt.image.BufferedImage

interface SignDriver {
    suspend fun write(img: BufferedImage)
}
