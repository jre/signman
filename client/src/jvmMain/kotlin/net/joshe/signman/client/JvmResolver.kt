package net.joshe.signman.client

import at.asitplus.cidre.invoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

class JvmResolver : Resolver {
    override suspend fun resolve(hostname: String) = withContext(Dispatchers.IO) {
        try {
            InetAddress.getAllByName(hostname).map { IP(it) }
        } catch (_: UnknownHostException) {
            emptyList()
        }
    }
}
