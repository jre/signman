package net.joshe.signman.zeroconf

import kotlin.coroutines.CoroutineContext

abstract class ServicePublisherBuilder(var context: CoroutineContext,
                                       var name: String,
                                       var type: String,
                                       var port: Int,
                                       var params: Map<String,String>) {
    abstract fun build(): ServicePublisher
}
