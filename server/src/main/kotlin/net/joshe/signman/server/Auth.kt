package net.joshe.signman.server

import net.joshe.signman.api.getDigestHA1Input
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class Auth private constructor(private val users: Map<String,String>) {
    private val ha1RealmsCache = mutableMapOf<Uuid,MutableMap<String,ByteArray>>()

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

    private fun ha1Cache(uuid: Uuid) = ha1RealmsCache.getOrElse(uuid) { mutableMapOf() }

    fun getHA1Digest(user: String, uuid: Uuid, digest: MessageDigest) = users[user]?.let { pass ->
        ha1Cache(uuid).getOrPut(user) { digest.digest(getDigestHA1Input(user, pass, uuid)) }
    }
}
