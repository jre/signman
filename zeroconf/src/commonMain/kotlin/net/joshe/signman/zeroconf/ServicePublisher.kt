package net.joshe.signman.zeroconf

interface ServicePublisher {
    fun start()
    suspend fun stop()
}
