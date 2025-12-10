package net.joshe.signman.server

import io.ktor.client.call.body
import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.digest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
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
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ServerTest {
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

    private suspend fun mkStateRgb(onUpdate: State.() -> Unit) = State.initialize(
        renderer = Renderer(configRgb), fg = defFgRgb, bg = defBgRgb, onUpdate = onUpdate)

    private suspend fun mkStateIdx(onUpdate: State.() -> Unit) = State.initialize(
        renderer = Renderer(configIdx), fg = defFgIdx, bg = defBgIdx, onUpdate = onUpdate)

    private fun IndexedColor.toRgb() = RGBColor(rgb)

    private fun SignColor.convert(config: Config)
            = if (config.sign.color.type == ColorType.RGB && this is IndexedColor) toRgb() else this

    private fun ApplicationTestBuilder.mkClient(user: String? = null) = createClient {
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
    }

    private fun getStatusResp(config: Config, text: String, fg: SignColor? = null, bg: SignColor? = null) = StatusResponse(
        type = config.sign.color.type, text = text,
        fg = (fg ?: config.sign.color.foreground).convert(config),
        bg = (bg ?: config.sign.color.background).convert(config),
        defaultFg = config.sign.color.foreground, defaultBg = config.sign.color.background,
        colors = if (config.sign.color.type == ColorType.INDEXED) colors8 else null)

    @Test fun testMainRgb() = mainTests(configRgb, uuidRgb, "alice", ::mkStateRgb)
    @Test fun testMainIndexed() = mainTests(configIdx, uuidIdx, "bob", ::mkStateIdx)

    private fun mainTests(config: Config, uuid: Uuid, user: String, mkState: suspend (State.() -> Unit) -> State) = runTest {
        var updated = 0
        Server(config, mkState { updated++ }, auth, uuid).runCustom { module ->
            testApplication {
                application(module)
                client = mkClient(user)
                val anonClient = mkClient()

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
    }
}
