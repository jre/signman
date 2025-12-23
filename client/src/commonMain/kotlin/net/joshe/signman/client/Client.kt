package net.joshe.signman.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.host
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.joshe.signman.api.QueryResponse
import net.joshe.signman.api.StatusResponse
import net.joshe.signman.api.UpdateRequest
import net.joshe.signman.api.buildSerializersModule
import net.joshe.signman.api.dnssdUuidKey
import net.joshe.signman.zeroconf.Service
import java.lang.Exception
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class Client internal constructor(private val client: HttpClient,
                                  private val authStore: AuthStore,
                                  private val resolver: Resolver,
                                  private val servers: HostCache) {
    companion object {
        internal val serverUuidKey = AttributeKey<Uuid>("net.joshe.signman.Client.ServerUuid")

        fun <T : HttpClientEngineConfig> create(
            factory: HttpClientEngineFactory<T>, authStore: AuthStore, resolver: Resolver, serverCache: HostCache
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
        }, authStore, resolver, serverCache)
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    suspend fun checkUrl(url: Url): QueryResponse? = resolver.resolve(url.host)
        .map { HostInfo(url, address = it) }
        .let { connections ->
            servers.testAddresses(scope, null, connections, true, ::testConnection)
        }

    suspend fun checkServices(services: Set<Service>)
    = services.groupBy { service ->
        try { Uuid.parse(service.params.getValue(dnssdUuidKey)) } catch (_: Exception) { null }
    }.filterKeys { it != null }.map { (uuid, svc) ->
        servers.testAddresses(scope, uuid, svc.map(::HostInfo), true, ::testConnection)
    }.filterNotNull()

    suspend fun authenticate(uuid: Uuid) { get(uuid, "/api/authenticate") }

    suspend fun login(uuid: Uuid, user: String, pass: String?) {
        get(uuid, "/api/authenticate") {
            authStore.overrideCredentials(this, user = user, pass = pass)
        }.also { authStore.saveCredentials(it) }
    }

    suspend fun status(uuid: Uuid): StatusResponse = get(uuid, "/api/v1/status").body()

    suspend fun update(uuid: Uuid, req: UpdateRequest) { post(uuid, "/api/v1/update", req) }

    private suspend fun testConnection(info: HostInfo): Pair<Uuid,QueryResponse>
    = request(null, HttpMethod.Get, "/api/query", connectionInfo = info)
        .body<QueryResponse>()
        .let { resp ->
            scope.launch { servers.setName(resp.uuid, resp.name) }
            Pair(resp.uuid, resp)
        }

    private suspend fun get(uuid: Uuid, endpoint: String, block: (HttpRequestBuilder.() -> Unit)? = null)
            = request(uuid, HttpMethod.Get, endpoint, block = block)

    private suspend inline fun <reified T> post(uuid: Uuid, endpoint: String, body: T)
            = request(uuid, HttpMethod.Post, endpoint, ContentType.Application.Json, body = body)

    private suspend fun request(uuid: Uuid?,
                                method: HttpMethod,
                                endpoint: String,
                                type: ContentType? = null,
                                body: Any? = null,
                                connectionInfo: HostInfo? = null,
                                block: (HttpRequestBuilder.() -> Unit)? = null): HttpResponse {
        val info = if (uuid == null) {
            check(connectionInfo != null)
            connectionInfo
        } else {
            check(connectionInfo == null)
            servers.getPreferredAddress(scope, uuid, ::testConnection)
        }

        return client.request(info.toUrl(endpoint)) {
            this.method = method
            host = info.hostname
            if (type != null)
                contentType(type)
            if (body != null)
                setBody(body)
            block?.invoke(this)
        }
    }
}
