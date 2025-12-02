package net.joshe.signman.server

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.CachingOptions
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.LastModifiedVersion
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.digest
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.cachingheaders.caching
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.util.toGMTDate
import io.ktor.util.AttributeKey
import io.ktor.util.escapeHTML
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.joshe.signman.api.QueryResponse
import net.joshe.signman.api.StatusResponse
import net.joshe.signman.api.UpdateRequest
import net.joshe.signman.api.buildSerializersModule
import net.joshe.signman.api.toHttpAuthenticationRealm
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class Server(private val config: Config, private val state: State, private val auth: Auth, private val uuid: Uuid) {
    private val updater = MutableStateFlow<UpdateRequest?>(null)

    private val startedInst = Instant.now()
    private val neverCacheable = CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private))
    private val maybeCacheable = CachingOptions(CacheControl.NoCache(CacheControl.Visibility.Public))
    private val cacheableKey = AttributeKey<Pair<Instant,String?>>("MaybeCacheableLastModifiedInstant")

    suspend fun run() {
        val updaterJob = CoroutineScope(currentCoroutineContext()).launch {
            updater.collect(::updateCollector)
        }

        embeddedServer(Netty, port = config.server.port) {
            install(Authentication) {
                digest("auth-digest") {
                    algorithmName = auth.digestAlgorithm
                    realm = uuid.toHttpAuthenticationRealm()
                    digestProvider(auth::digestProvider)
                }
            }

            install(CachingHeaders) {
                options { call, _ ->
                    if (cacheableKey !in call.attributes) neverCacheable else null
                }
            }

            install(ConditionalHeaders) {
                version { call, _ ->
                    call.attributes.getOrNull(cacheableKey)?.let { (inst, eTag) ->
                        listOf(LastModifiedVersion(inst.toGMTDate()),
                            EntityTagVersion(eTag ?: instantETag(inst)))
                    } ?: emptyList()
                }
            }

            install(ContentNegotiation) {
                json(Json {
                    serializersModule = buildSerializersModule(
                        (config.sign.color as? Config.IndexedColorConfig)?.palette)
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(CallLogging)
            install(DefaultHeaders)

            routing {
                get("/") { endpointHomepage(call) }
                get("/api/query") { endpointQuery(call) }
                authenticate("auth-digest") {
                    get("/api/authenticate") { call.respond(HttpStatusCode.OK) }
                }
                route("/api/v1") {
                    get("/image") { endpointPng(call) }
                    get("/status") { endpointStatus(call) }
                    authenticate("auth-digest") {
                        post("/update") { endpointUpdate(call) }
                    }
                }
            }
        }.start(wait = true)

        updaterJob.cancel()
    }

    private fun instantETag(inst: Instant) = inst.epochSecond.toString(32)

    private fun cacheable(call: RoutingCall, modified: Instant? = null, eTag: String? = null) {
        call.caching = maybeCacheable
        call.attributes[cacheableKey] = Pair(modified ?: state.lastModified, eTag)
    }

    private suspend fun endpointHomepage(call: RoutingCall) {
        val homepage = synchronized(state) {
            cacheable(call)
            """<!DOCTYPE html>
<html>
    <head>
        <title>${config.name.escapeHTML()}</title>
        <style>
            html, body {
                width: 100%;
                height: 100%;
                margin: 0;
            }
            .centering {
                display: flex;
                justify-content: center;
                background-color: #${state.bg.rgb.toHexString()};
            }
            .centering img {
                display: block;
                margin: auto;
            }
        </style>
    </head>
    <body class="centering">
       <img src="/api/v1/image" width=${config.sign.width} height=${config.sign.height} alt="${state.text.escapeHTML()}">
    </body>
</html>
"""
        }
        call.respondText(homepage, ContentType.Text.Html)
    }

    private suspend fun endpointPng(call: RoutingCall) {
        val png = synchronized(state) {
            cacheable(call, eTag = state.pngETag)
            state.png
        }
        call.respondBytes(png, ContentType.Image.PNG)
    }

    private suspend fun endpointQuery(call: RoutingCall) {
        cacheable(call, modified = startedInst)
        call.respond(QueryResponse(uuid = uuid, name = config.name, minApi = 1, maxApi = 1))
    }

    private suspend fun endpointStatus(call: RoutingCall) {
        val resp = synchronized(state) {
            cacheable(call)
            StatusResponse(
                text = state.text,
                fg = state.fg,
                bg = state.bg,
                type = config.sign.color.type,
                defaultFg = config.sign.color.foreground,
                defaultBg = config.sign.color.background,
                colors = (config.sign.color as? Config.IndexedColorConfig)?.palette)
        }
        call.respond(resp)
    }

    private suspend fun endpointUpdate(call: RoutingCall) {
        updater.value = call.receive()
        call.respond(HttpStatusCode.OK)
    }

    private suspend fun updateCollector(req: UpdateRequest?) {
        if (req != null)
            state.update(text = req.text,
                fg = req.fg ?: config.sign.color.foreground,
                bg = req.bg ?: config.sign.color.background)
    }
}
