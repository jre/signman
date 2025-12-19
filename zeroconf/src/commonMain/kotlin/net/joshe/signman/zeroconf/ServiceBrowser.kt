package net.joshe.signman.zeroconf

import kotlinx.coroutines.flow.StateFlow

interface ServiceBrowser {
    val state: StateFlow<Set<Service>?>
    fun start()
    suspend fun stop()
}
