package net.joshe.signman.server

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.CachingOptions
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.LastModifiedVersion
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ServerReady
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
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import io.ktor.server.util.toGMTDate
import io.ktor.sse.ServerSentEvent
import io.ktor.util.AttributeKey
import io.ktor.util.escapeHTML
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import net.joshe.signman.api.QueryResponse
import net.joshe.signman.api.StatusResponse
import net.joshe.signman.api.UpdateRequest
import net.joshe.signman.api.buildSerializersModule
import net.joshe.signman.api.toHttpAuthenticationRealm
import net.joshe.signman.zeroconf.ServicePublisher
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class Server(private val config: Config,
             private val state: State,
             private val auth: Auth,
             private val uuid: Uuid,
             private val publisher: ServicePublisher,
             private val updates: StateFlow<Cacheable>,
             coroutineContext: CoroutineContext) {
    private val myCoroutineContext = coroutineContext
    private val neverCacheable = CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private))
    private val maybeCacheable = CachingOptions(CacheControl.NoCache(CacheControl.Visibility.Public))
    private val cacheableKey = AttributeKey<Pair<Instant,String>>("MaybeCacheableLastModifiedInstant")
    private val updateMutex = Mutex()

    fun run() = embeddedServer(Netty, port = config.server.port) {
        monitor.subscribe(ServerReady) {
            Runtime.getRuntime().addShutdownHook(Thread {
                runBlocking { publisher.stop() }
            })
            publisher.start()
        }
        setupInternal(this)
    }.start(wait = true)

    @TestOnly
    internal suspend fun runTesting(block: suspend (Int) -> Unit) {
        val server = embeddedServer(Netty, port = 0) { setupInternal(this) }
        server.start()
        block(server.engine.resolvedConnectors().first().port)
        server.stop()
    }

    @TestOnly
    internal fun setupApplication(application: Application) = setupInternal(application)

    private fun setupInternal(application: Application) {
        application.apply {
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
                        listOf(LastModifiedVersion(inst.toJavaInstant().toGMTDate()),
                            EntityTagVersion(eTag))
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
            install(SSE)

            routing {
                get("/") { endpointHomepage(call) }
                get("/api/query") { endpointQuery(call) }
                authenticate("auth-digest") {
                    get("/api/authenticate") { call.respond(HttpStatusCode.OK) }
                }
                route("/api/v1") {
                    sse("/events") { endpointEvents(this) }
                    get("/image") { endpointPng(call) }
                    get("/status") { endpointStatus(call) }
                    authenticate("auth-digest") {
                        post("/update") { endpointUpdate(call) }
                    }
                }
            }
        }
    }

    private fun addCaching(call: RoutingCall, cache: Cacheable, eTag: String) {
        call.caching = maybeCacheable
        call.attributes[cacheableKey] = Pair(cache.modified, eTag)
    }

    private suspend fun endpointHomepage(call: RoutingCall) {
        val cache = updates.value
        addCaching(call, cache, cache.htmlETag)
        call.respondText(cache.html, ContentType.Text.Html)
    }

    private suspend fun endpointPng(call: RoutingCall) {
        val cache = updates.value
        addCaching(call, cache, cache.pngETag)
        call.respondBytes(cache.png, ContentType.Image.PNG)
    }

    private suspend fun endpointQuery(call: RoutingCall) {
        call.respond(QueryResponse(uuid = uuid, name = config.name, minApi = 1, maxApi = 1))
    }

    private suspend fun endpointStatus(call: RoutingCall) {
        val cache = updates.value
        call.respond(StatusResponse(
            text = cache.state.text,
            fg = cache.state.fg,
            bg = cache.state.bg,
            type = config.sign.color.type,
            defaultFg = config.sign.color.foreground,
            defaultBg = config.sign.color.background,
            colors = (config.sign.color as? Config.IndexedColorConfig)?.palette,
            updateTag = cache.stateETag))
    }

    private suspend fun endpointUpdate(call: RoutingCall) {
        var updated: State.Snapshot? = null
        val req = call.receive<UpdateRequest>()
        updateMutex.withLock {
            updates.cancellableCollect(myCoroutineContext) { cache ->
                if (updated == null)
                    updated = state.update(text = req.text,
                        fg = req.fg ?: config.sign.color.foreground,
                        bg = req.bg ?: config.sign.color.background)
                if (updated == cache.state)
                    currentCoroutineContext().cancel()
            }
        }
        call.respond(HttpStatusCode.OK)
    }

    private suspend fun endpointEvents(session: ServerSSESession) {
        session.heartbeat {
            event = ServerSentEvent(comments = "heartbeat")
            period = 30.seconds
        }
        updates.collect { cache ->
            session.send(cache.stateETag, event = UPDATE_EVENT)
        }
    }

    companion object {
        private const val UPDATE_EVENT = "updated"

        fun getHtml(config: Config, state: State.Snapshot, stateETag: String) = """<!DOCTYPE html>
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
        <script>
            var source = new EventSource("/api/v1/events");
            source.addEventListener("$UPDATE_EVENT", function (event) {
                if (event.data != "$stateETag") {
                    source.close();
                    location.reload();
                }
            });
        </script>
    </head>
    <body class="centering">
       <img src="/api/v1/image" width=${config.sign.width} height=${config.sign.height} alt="${state.text.escapeHTML()}">
    </body>
</html>
"""
    }
}
