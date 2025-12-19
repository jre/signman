package net.joshe.signman.zeroconf.avahi

import net.joshe.signman.zeroconf.ServiceBrowser
import net.joshe.signman.zeroconf.ServiceBrowserBuilder
import kotlin.coroutines.CoroutineContext

class AvahiBrowserBuilder(context: CoroutineContext, type: String) : ServiceBrowserBuilder(context, type) {
    override fun build(): ServiceBrowser = AvahiBrowser(context, type)
}
