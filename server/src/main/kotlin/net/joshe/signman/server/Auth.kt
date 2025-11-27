package net.joshe.signman.server

import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties

class Auth private constructor(private val users: Map<String,String>) {
    private val ha1RealmsCache = mutableMapOf<String,MutableMap<String,ByteArray>>()

    companion object {
        fun load(conf: Config.AuthConfig): Auth {
            check(conf.type == Config.AuthType.FILE)
            return loadStream(FileInputStream(conf.path))
        }

        internal fun loadStream(stream: InputStream) = Auth(Properties()
            .apply { load(stream) }
            .map { (user, passPair) ->
                val (type, pass) = passPair.toString().split(':', limit = 2)
                check(type == "plain")
                Pair(user.toString(), pass)
            }.toMap())
    }

    private fun ha1Cache(realm: String) = ha1RealmsCache.getOrElse(realm) { mutableMapOf() }

    fun getHA1Digest(user: String, realm: String, digest: MessageDigest) = users[user]?.let { pass ->
        ha1Cache(realm).getOrPut(user) {
            digest.digest("$user:$realm:$pass".toByteArray(Charsets.UTF_8))
        }
    }
}
