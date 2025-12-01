package net.joshe.signman.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendEncodedPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import net.joshe.signman.api.QueryResponse
import net.joshe.signman.api.StatusResponse
import net.joshe.signman.api.UpdateRequest
import net.joshe.signman.api.buildSerializersModule
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class Client internal constructor(private val client: HttpClient, private val authStore: AuthStore) {
    companion object {
        internal val serverUuidKey = AttributeKey<Uuid>("net.joshe.signman.Client.ServerUuid")

        fun <T : HttpClientEngineConfig> create(factory: HttpClientEngineFactory<T>, authStore: AuthStore
        ) = Client(HttpClient(factory) {
            engine {
                pipelining = true
                expectSuccess = true
            }

            install(Auth) { providers += authStore.getAuthProvider() }

            install(ContentNegotiation) {
                json(Json {
                    serializersModule = buildSerializersModule(null)
                    ignoreUnknownKeys = true
                })
            }
        }, authStore)
    }

    private val servers = mutableMapOf<Url,QueryResponse>()

    suspend fun checkApi(host: Url) = servers.getOrPut(host) {
        client.get(host.endpoint("/api/query")).body<QueryResponse>().also { check(it.minApi == 1) }
    }

    suspend fun authenticate(host: Url) { get(host, "/api/authenticate") }

    suspend fun login(host: Url, user: String, pass: String?) {
        checkApi(host)
        client.get(host.endpoint("/api/authenticate")) {
            authStore.overrideCredentials(this, user = user, pass = pass)
        }.also { authStore.saveCredentials(it) }
    }

    suspend fun status(host: Url): StatusResponse = get(host, "/api/v1/status").body()

    suspend fun update(host: Url, req: UpdateRequest) { post(host, "/api/v1/update", req) }

    private suspend fun get(host: Url, endpoint: String): HttpResponse {
        checkApi(host)
        return client.get(host.endpoint(endpoint))
    }

    private suspend inline fun <reified T> post(host: Url, endpoint: String, body: T): HttpResponse {
        checkApi(host)
        return client.post(host.endpoint(endpoint)) {
            contentType(ContentType.Application.Json)
            setBody<T>(body)
        }
    }

    private fun Url.endpoint(endpoint: String) = URLBuilder(protocol = protocol, host = host, port = port).let { builder ->
        builder.appendEncodedPathSegments(segments)
        builder.appendEncodedPathSegments(endpoint)
    }.build()
}
