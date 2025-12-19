package net.joshe.signman.zeroconf.avahi

import net.joshe.signman.zeroconf.ServicePublisher
import net.joshe.signman.zeroconf.ServicePublisherBuilder
import kotlin.coroutines.CoroutineContext

class AvahiPublisherBuilder(context: CoroutineContext, name: String, type: String, port: Int, params: Map<String, String>)
    : ServicePublisherBuilder(context = context, name = name, type = type, port = port, params = params) {
    override fun build(): ServicePublisher
            = AvahiPublisher(context = context, name = name, type = type, port = port, params = params)
}
