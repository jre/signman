package net.joshe.signman.server.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.joshe.signman.server.Config
import net.joshe.signman.server.Config.LinuxGpioBusDriverConfig
import net.joshe.signman.server.Config.LinuxSpiBusDriverConfig
import net.joshe.signman.server.ConfigurationException

interface BusDriver {
    companion object {
        private var instance: BusDriver? = null

        suspend fun get(config: Config): BusDriver {
            if (instance != null)
                return instance!!

            val dc = config.driver
            withContext(Dispatchers.IO) {
                instance = when {
                    dc == null -> DummyBusDriver()
                    dc.spi is Config.DummySpiBusDriverConfig && dc.gpio is Config.DummyGpioBusDriverConfig -> DummyBusDriver()
                    dc.spi is LinuxSpiBusDriverConfig && dc.gpio is LinuxGpioBusDriverConfig -> LinuxBusDriver(config)
                    dc.spi is LinuxSpiBusDriverConfig || dc.gpio is LinuxGpioBusDriverConfig
                         -> throw(ConfigurationException("linux spi driver must be used with linux gpio driver"))
                    else -> throw(ConfigurationException("unknown bus driver configuration"))
                }
            }
            return instance!!
        }
    }
}
