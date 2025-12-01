package net.joshe.signman.client

import io.ktor.client.plugins.auth.AuthProvider
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HeaderValueEncoding
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.fullPath
import io.ktor.util.AttributeKey
import net.joshe.signman.api.getDigestHA1Input
import net.joshe.signman.api.toHttpAuthenticationRealm
import org.slf4j.LoggerFactory
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
abstract class AuthStore {
    private val log = LoggerFactory.getLogger(this::class.java)

    protected abstract fun hasDigester(algorithm: String): Boolean
    protected abstract fun getDigester(algorithm: String): Digester?
    protected abstract fun getHA1(digester: Digester, uuid: Uuid): Pair<String,ByteArray>?
    protected abstract fun putHA1(digester: Digester, uuid: Uuid, user: String, ha1: ByteArray)

    protected interface Digester {
        fun digest(input: ByteArray): ByteArray
    }

    companion object {
        private const val DEFAULT_ALGORITHM = "MD5"
        private val overrideUserPassKey = AttributeKey<Override>("${this::class.qualifiedName}.OverrideUserPass")
        private val overrideHA1InfoKey = AttributeKey<HA1Info>("${this::class.qualifiedName}.OverrideHash")
    }

    fun getAuthProvider(): AuthProvider = AuthStoreDigestProvider(this)

    fun overrideCredentials(builder: HttpRequestBuilder, user: String, pass: String?) {
        log.debug("Overriding credentials with user=$user and ${if (pass == null) "null pass" else "pass"}")
        builder.attributes[overrideUserPassKey] = Override(user = user, pass = pass)
    }

    fun saveCredentials(resp: HttpResponse) {
        val info = resp.request.attributes[overrideHA1InfoKey]
        log.debug("Saving hashed credentials for user=${info.user}")
        putHA1(info.digester, info.uuid, info.user, info.ha1)
    }

    private class Override(val user: String, val pass: String?)
    private class HA1Info(val digester: Digester, val uuid: Uuid, val user: String, val ha1: ByteArray)

    internal class AuthStoreDigestProvider(private val store: AuthStore) : AuthProvider {
        private val log = LoggerFactory.getLogger(this::class.java)

        @Suppress("OverridingDeprecatedMember")
        @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
        override val sendWithoutRequest: Boolean get() = error("Deprecated")

        override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = false

        override fun isApplicable(auth: HttpAuthHeader) = (auth.authScheme == AuthScheme.Digest &&
                auth is HttpAuthHeader.Parameterized &&
                !auth.parameter("realm").isNullOrEmpty() &&
                !auth.parameter("nonce").isNullOrEmpty() &&
                store.hasDigester(auth.parameter("algorithm") ?: DEFAULT_ALGORITHM))

        override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
            log.debug("Maybe adding digest auth header")

            if (authHeader !is HttpAuthHeader.Parameterized?)
                return

            val uuid = authHeader?.parameter("realm")?.let { Uuid.parse(it) }
                ?: request.attributes.getOrNull(Client.serverUuidKey)
            val nonce = authHeader?.parameter("nonce")
            val algorithm = authHeader?.parameter("algorithm") ?: DEFAULT_ALGORITHM
            val digester = store.getDigester(algorithm)
            if (uuid == null || nonce == null || digester == null)
                return

            val ha1Pair = findHA1(request, uuid, digester) ?: return
            val (user, ha1) = ha1Pair
            log.trace("H(A1)=${ha1.toHexString()}")
            val uri = request.url.build().fullPath
            val a2 = "${request.method.value.uppercase()}:$uri"
            log.trace("A2=$a2")
            val ha2 = digester.digest(a2.toByteArray())
            log.trace("H(A2)=${ha2.toHexString()}")
            val respInput = "${ha1.toHexString()}:$nonce:${ha2.toHexString()}"
            log.trace("response input=$respInput")
            val response = digester.digest(respInput.toByteArray())

            val params = mutableMapOf("username" to user,
                "realm" to uuid.toHttpAuthenticationRealm(),
                "nonce" to nonce,
                "uri" to uri,
                "response" to response.toHexString(),
                "algorithm" to algorithm)
            authHeader.parameter("opaque")?.let { params.put("opaque", it) }

            val auth = HttpAuthHeader.Parameterized(
                AuthScheme.Digest, params, HeaderValueEncoding.QUOTED_ALWAYS).render()
            log.debug("adding ${HttpHeaders.Authorization}: $auth")
            request.headers.append(HttpHeaders.Authorization, auth)
        }

        private fun findHA1(request: HttpRequestBuilder, uuid: Uuid, digester: Digester): Pair<String,ByteArray>? {
            val override = request.attributes.getOrNull(overrideUserPassKey)

            if (override == null) {
                log.debug("Using stored credentials")
                return store.getHA1(digester, uuid)
            }

            if (override.pass != null) {
                log.debug("Using overriding user=${override.user} and pass")
                val ha1 = digester.digest(getDigestHA1Input(user = override.user, pass = override.pass, uuid = uuid))
                request.attributes[overrideHA1InfoKey] = HA1Info(digester, uuid, override.user, ha1)
                return Pair(override.user, ha1)
            }

            val ha1Pair = store.getHA1(digester, uuid)
            if (override.user == ha1Pair?.first) {
                log.debug("Using overriding user=${override.user} with stored H(A1)")
                request.attributes[overrideHA1InfoKey] = HA1Info(digester, uuid, ha1Pair.first, ha1Pair.second)
                return ha1Pair
            }

            log.debug("No stored has found for overriding user=${override.user}")
            return null
        }
    }
}
