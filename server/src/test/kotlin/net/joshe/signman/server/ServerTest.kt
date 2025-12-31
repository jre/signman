package net.joshe.signman.server

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.digest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.runTestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock as Clockx
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Instant as Instantx
import kotlinx.serialization.json.Json
import net.joshe.signman.api.ColorType
import net.joshe.signman.api.IndexedColor
import net.joshe.signman.api.QueryResponse
import net.joshe.signman.api.RGB
import net.joshe.signman.api.RGBColor
import net.joshe.signman.api.SignColor
import net.joshe.signman.api.StatusResponse
import net.joshe.signman.api.UpdateRequest
import net.joshe.signman.api.buildSerializersModule
import net.joshe.signman.zeroconf.ServicePublisher
import org.slf4j.simple.SimpleLogger
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class ServerTest {
    @BeforeTest fun quiet() { System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "warn") }

    private val colors8 = listOf(
        IndexedColor(0, RGB(0, 0, 0), "Black"),
        IndexedColor(1, RGB(205,0,0), "Red"),
        IndexedColor(2, RGB(0,205,0), "Green"),
        IndexedColor(3, RGB(205,205,0), "Yellow"),
        IndexedColor(4, RGB(0,0,238), "Blue"),
        IndexedColor(5, RGB(205,0,205), "Magenta"),
        IndexedColor(6, RGB(0,205,205), "Cyan"),
        IndexedColor(7, RGB(229,229,229), "White"))
    private val defFgIdx = colors8[0]
    private val defBgIdx = colors8[7]
    private val defFgRgb = colors8[0].toRgb()
    private val defBgRgb = colors8[7].toRgb()

    private val uuidRgb = Uuid.parse("eb155d50-0f3b-4494-b1fa-fd7a14e9478a")
    private val uuidIdx = Uuid.parse("0e593489-c19a-4386-a5fe-cc658df793da")

    private val configRgb = Config(
        name = "RGB test server",
        auth = Config.AuthConfig(Config.AuthType.FILE, File("/garbage")),
        server = Config.ServerConfig(directory = File("/nonsense")),
        sign = Config.SignConfig(width = 100, height = 100,
            color = Config.RGBColorConfig(foreground = defFgRgb, background = defBgRgb)))

    private val configIdx = Config(
        name = "Indexed test server",
        auth = Config.AuthConfig(Config.AuthType.FILE, File("/garbage")),
        server = Config.ServerConfig(directory = File("/nonsense")),
        sign = Config.SignConfig(width = 100, height = 100, color = Config.IndexedColorConfig(
            foregroundIndex = defFgIdx.index, backgroundIndex = defBgIdx.index, palette = colors8)))

    private val credentials = mapOf("alice" to "apple", "bob" to "banana")
    private val auth = Auth.loadStream(ByteArrayInputStream(
        credentials.map { (u, p) -> "$u:plain:$p" }.joinToString("\n").toByteArray()))
    private val publisher = object : ServicePublisher {
        override fun start() {}
        override suspend fun stop() {}
    }

    private val httpTimeFmt = LocalDateTime.Format {
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
        chars(", ")
        dayOfMonth()
        char(' ')
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        year()
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
        chars(" GMT")
    }

    private fun Instantx.toHttpTime() = toLocalDateTime(TimeZone.UTC).format(httpTimeFmt)

    private suspend fun mkStateRgb(onUpdate: State.(State.Snapshot) -> Unit)
            = State.initialize(fg = defFgRgb, bg = defBgRgb, onUpdate = onUpdate)

    private suspend fun mkStateIdx(onUpdate: State.(State.Snapshot) -> Unit)
            = State.initialize(fg = defFgIdx, bg = defBgIdx, onUpdate = onUpdate)

    private fun IndexedColor.toRgb() = RGBColor(rgb)

    private fun SignColor.convert(config: Config)
            = if (config.sign.color.type == ColorType.RGB && this is IndexedColor) toRgb() else this

    private fun HttpClientConfig<out HttpClientEngineConfig>.configureClient(user: String?) {
        if (user != null)
            install(io.ktor.client.plugins.auth.Auth) {
                digest {
                    algorithmName = "SHA-256"
                    credentials {
                        DigestAuthCredentials(username = user, password = credentials.getValue(user))
                    }
                }
            }
        install(ContentNegotiation) {
            json(Json {
                serializersModule = buildSerializersModule(null)
            })
        }
        install(SSE) { showCommentEvents() }
    }

    private fun ApplicationTestBuilder.mockClient(user: String? = null) = createClient { configureClient(user) }

    private fun localClient(port: Int, user: String? = null) = HttpClient(Java) {
        configureClient(user)
        defaultRequest { url("http://localhost:$port") }
    }

    private fun getStatusResp(config: Config, text: String, fg: SignColor? = null, bg: SignColor? = null): StatusResponse {
        val curFg = (fg ?: config.sign.color.foreground).convert(config)
        val curBg = (bg ?: config.sign.color.background).convert(config)
        val snap = State.Snapshot(text, fg = curFg, bg = curBg)
        return StatusResponse(type = config.sign.color.type, text = text, fg = curFg, bg = curBg,
            defaultFg = config.sign.color.foreground, defaultBg = config.sign.color.background,
            colors = if (config.sign.color.type == ColorType.INDEXED) colors8 else null,
            updateTag = snap.eTag())
    }

    @Test fun testMainRgb() = mainTests(configRgb, uuidRgb, "alice", ::mkStateRgb)
    @Test fun testMainIndexed() = mainTests(configIdx, uuidIdx, "bob", ::mkStateIdx)

    private fun mainTests(config: Config, uuid: Uuid, user: String, mkState: suspend (State.(State.Snapshot) -> Unit) -> State)
    = runTest {
        val scope = this
        val renderer = Renderer(config, null)
        var updated = 0
        var flow: MutableStateFlow<Cacheable>? = null
        val state = mkState { snap ->
            updated++
            scope.launch { flow!!.emit(Cacheable.create(config, snap, renderer)) }
        }
        flow = MutableStateFlow(Cacheable.create(config, state.snapshot, renderer))
        val server = Server(config, state, auth, uuid, publisher, flow.asStateFlow(), coroutineContext)

        runTestApplication(coroutineContext) {
            application { server.setupApplication(this) }
            client = mockClient(user)
            val anonClient = mockClient()

            assertEquals(0, updated)

            var resp = client.get("/imaginary")
            testScheduler.advanceUntilIdle()
            assertEquals(404, resp.status.value)
            assertEquals(0, updated)

            resp = client.get("/")
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(0, updated)
            assertContains(resp.bodyAsText(), config.name)

            resp = client.get("/api/v1/image")
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(0, updated)
            assertEquals(ContentType.Image.PNG, resp.contentType())
            var oldPngBytes = resp.bodyAsBytes()

            resp = client.get("/api/query")
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(0, updated)
            assertEquals(QueryResponse(
                minApi = 1, maxApi = 1, name = config.name, uuid = uuid), resp.body())

            resp = anonClient.get("/api/authenticate")
            testScheduler.advanceUntilIdle()
            assertEquals(401, resp.status.value)
            assertEquals(0, updated)

            resp = client.get("/api/authenticate")
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(0, updated)

            resp = client.get("/api/v1/status")
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(0, updated)
            assertEquals(getStatusResp(config, ""), resp.body())

            resp = anonClient.post("/api/v1/update") {
                contentType(ContentType.Application.Json)
                setBody(UpdateRequest("OK!", fg = colors8[3].convert(config)))
            }
            testScheduler.advanceUntilIdle()
            assertEquals(401, resp.status.value)
            assertEquals(0, updated)

            resp = client.get("/api/v1/image")
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(0, updated)
            assertEquals(ContentType.Image.PNG, resp.contentType())
            var newPngBytes = resp.bodyAsBytes()
            assertContentEquals(oldPngBytes, newPngBytes)

            resp = client.post("/api/v1/update") {
                contentType(ContentType.Application.Json)
                setBody(UpdateRequest("OK!", fg = colors8[3].convert(config)))
            }
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(1, updated)
            assertEquals(State.Snapshot("OK!", fg = colors8[3].convert(config),
                bg = config.sign.color.background), flow.value.state)

            resp = client.get("/api/v1/status")
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(1, updated)
            assertEquals(getStatusResp(config, "OK!", fg = colors8[3]), resp.body())

            resp = client.get("/api/v1/image")
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(1, updated)
            assertEquals(ContentType.Image.PNG, resp.contentType())
            newPngBytes = resp.bodyAsBytes()
            assertFalse(oldPngBytes.contentEquals(newPngBytes))
            oldPngBytes = newPngBytes

            resp = client.post("/api/v1/update") {
                contentType(ContentType.Application.Json)
                setBody(UpdateRequest("Yay?", bg = colors8[6].convert(config)))
            }
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(2, updated)
            assertEquals(State.Snapshot("Yay?", bg = colors8[6].convert(config),
                fg = config.sign.color.foreground), flow.value.state)

            resp = client.get("/api/v1/status")
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(2, updated)
            assertEquals(getStatusResp(
                config, "Yay?", bg = colors8[6]), resp.body())

            resp = client.get("/api/v1/image")
            testScheduler.advanceUntilIdle()
            assertEquals(200, resp.status.value)
            assertEquals(2, updated)
            assertEquals(ContentType.Image.PNG, resp.contentType())
            newPngBytes = resp.bodyAsBytes()
            assertFalse(oldPngBytes.contentEquals(newPngBytes))
        }
    }

    @Test fun testCacheRgb() = cacheTests(configRgb, uuidRgb, ::mkStateRgb)
    @Test fun testCacheIndexed() = cacheTests(configIdx, uuidIdx, ::mkStateIdx)

    private fun cacheTests(config: Config, uuid: Uuid, mkState: suspend (State.(State.Snapshot) -> Unit) -> State)
    = runTest {
        val scope = this
        val renderer = Renderer(config, null)
        var flow: MutableStateFlow<Cacheable>? = null
        val state = mkState { snap ->
            scope.launch { flow!!.emit(Cacheable.create(config, snap, renderer)) }
        }
        flow = MutableStateFlow(Cacheable.create(config, state.snapshot, renderer))
        val server = Server(config, state, auth, uuid, publisher, flow.asStateFlow(), coroutineContext)

        runTestApplication(coroutineContext) {
            application { server.setupApplication(this) }
            client = mockClient()

            flow.value = flow.value.let { c -> Cacheable.create(c.modified - 1.minutes, c.state, c.png, c.html) }

            for ((url, eTag) in listOf(Pair("/", flow.value.htmlETag),
                Pair("/api/v1/image", flow.value.pngETag))) {

                // matching etag -> 304
                var resp = client.get(url) {
                    headers { append(HttpHeaders.IfNoneMatch, eTag) }
                }
                testScheduler.advanceUntilIdle()
                assertEquals(304, resp.status.value)
                assertEquals(0, resp.bodyAsBytes().size)

                // newer time -> 304
                resp = client.get(url) {
                    headers { append(HttpHeaders.IfModifiedSince, Clockx.System.now().toHttpTime()) }
                }
                testScheduler.advanceUntilIdle()
                assertEquals(304, resp.status.value)
                assertEquals(0, resp.bodyAsBytes().size)

                // matching etag and older time -> 304
                resp = client.get(url) {
                    headers {
                        append(HttpHeaders.IfNoneMatch, eTag)
                        append(HttpHeaders.IfModifiedSince, Instantx.DISTANT_PAST.toHttpTime())
                    }
                }
                testScheduler.advanceUntilIdle()
                assertEquals(304, resp.status.value)
                assertEquals(0, resp.bodyAsBytes().size)

                // matching etag and newer time -> 304
                resp = client.get(url) {
                    headers {
                        append(HttpHeaders.IfNoneMatch, eTag)
                        append(HttpHeaders.IfModifiedSince, Clockx.System.now().toHttpTime())
                    }
                }
                testScheduler.advanceUntilIdle()
                assertEquals(304, resp.status.value)
                assertEquals(0, resp.bodyAsBytes().size)

                // mismatched etag -> 200
                resp = client.get(url) {
                    headers { append(HttpHeaders.IfNoneMatch, "nonsense") }
                }
                testScheduler.advanceUntilIdle()
                assertEquals(200, resp.status.value)
                assertTrue(resp.bodyAsBytes().isNotEmpty())

                // older time -> 200
                resp = client.get(url) {
                    headers { append(HttpHeaders.IfModifiedSince, Instantx.DISTANT_PAST.toHttpTime()) }
                }
                testScheduler.advanceUntilIdle()
                assertEquals(200, resp.status.value)
                assertTrue(resp.bodyAsBytes().isNotEmpty())

                // mismatched etag and newer time -> 304
                resp = client.get(url) {
                    headers {
                        append(HttpHeaders.IfNoneMatch, "nonsense")
                        append(HttpHeaders.IfModifiedSince, Clockx.System.now().toHttpTime())
                    }
                }
                testScheduler.advanceUntilIdle()
                assertEquals(304, resp.status.value)
                assertEquals(0, resp.bodyAsBytes().size)

                // mismatched etag and older time -> 200
                resp = client.get(url) {
                    headers {
                        append(HttpHeaders.IfNoneMatch, "nonsense")
                        append(HttpHeaders.IfModifiedSince, Instantx.DISTANT_PAST.toHttpTime())
                    }
                }
                testScheduler.advanceUntilIdle()
                assertEquals(200, resp.status.value)
                assertTrue(resp.bodyAsBytes().isNotEmpty())
            }
        }
    }

    @Test fun testSseRgb() = sseTests(configRgb, uuidRgb, "alice", ::mkStateRgb)
    @Test fun testSseIndexed() = sseTests(configIdx, uuidIdx, "bob", ::mkStateIdx)

    private fun sseTests(config: Config, uuid: Uuid, user: String,
                         mkState: suspend (State.(State.Snapshot) -> Unit) -> State) = runTest {
        val scope = this
        val renderer = Renderer(config, null)
        var flow: MutableStateFlow<Cacheable>? = null
        val state = mkState { snap ->
            scope.launch { //(start = CoroutineStart.UNDISPATCHED) {
                flow!!.emit(Cacheable.create(config, snap, renderer))
            }
        }
        flow = MutableStateFlow(Cacheable.create(config, state.snapshot, renderer))
        val server = Server(config, state, auth, uuid, publisher, flow.asStateFlow(), currentCoroutineContext())

        server.runTesting { port ->
            val client = localClient(port, user)

            val firstSnap = state.snapshot
            client.sse("/api/v1/events", showCommentEvents = false) {
                val events = incoming.produceIn(scope)

                var event = events.receive()
                assertEquals("updated", event.event)
                assertEquals(firstSnap.eTag(), event.data)
                assertTrue("unexpected SSE update event: $event") {
                    event.comments.isNullOrEmpty() && event.id.isNullOrEmpty()
                }

                client.post("/api/v1/update") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateRequest(text = firstSnap.text, fg = firstSnap.fg, bg = firstSnap.bg))
                }
                event = events.receive()
                assertEquals("updated", event.event)
                assertEquals(firstSnap.eTag(), event.data)
                assertTrue("unexpected SSE update event: $event") {
                    event.comments.isNullOrEmpty() && event.id.isNullOrEmpty()
                }

                val thirdSnap = State.Snapshot(text = "server, send me some events",
                    fg = colors8[0].convert(config), bg = colors8[5].convert(config))
                client.post("/api/v1/update") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateRequest(text = "server, send me some events",
                        fg = colors8[0].convert(config), bg = colors8[5].convert(config)))
                }
                event = events.receive()
                assertEquals("updated", event.event)
                assertEquals(thirdSnap.eTag(), event.data)
                assertTrue("unexpected SSE update event: $event") {
                    event.comments.isNullOrEmpty() && event.id.isNullOrEmpty()
                }
            }
        }
    }
}
