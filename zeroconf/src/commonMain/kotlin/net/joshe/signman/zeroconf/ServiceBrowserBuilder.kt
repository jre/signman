package net.joshe.signman.zeroconf

import kotlin.coroutines.CoroutineContext

abstract class ServiceBrowserBuilder(var context: CoroutineContext,
                                     var type: String) {
    abstract fun build(): ServiceBrowser
}
