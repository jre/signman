package net.joshe.signman.client

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
class HostCacheTest {
    private class MockClock(scope: TestScope, private val started: Instant) : Clock, AutoCloseable {
        constructor(scope: TestScope, time: String) : this(scope, Instant.parse(time))

        private val scheduler = scope.testScheduler
        private val prevClock = MockableClock.replace(this)
        private val prevSource = MockableTimeSource.replace(scheduler.timeSource)

        override fun now() = started + scheduler.currentTime.milliseconds
        override fun close() {
            MockableClock.replace(prevClock)
            MockableTimeSource.replace(prevSource)
        }

        fun skip(duration: Duration) = scheduler.advanceTimeBy(duration)
        fun skip(instant: Instant) = scheduler.advanceTimeBy(instant - now())
        fun elapsed() = scheduler.currentTime.milliseconds
    }

    @Test fun testRankedHost() {
        val hi = HostInfo("", "0.0.0.0", 0)
        val first = HostCache.Entry.RankedHost(hi, delay = 1.milliseconds)
        val firstB = HostCache.Entry.RankedHost(hi, delay = 1.milliseconds)
        val second = HostCache.Entry.RankedHost(hi, delay = 2.milliseconds)
        val secondB = HostCache.Entry.RankedHost(hi, delay = 2.milliseconds)
        val third = HostCache.Entry.RankedHost(hi, failures = 1)
        val thirdB = HostCache.Entry.RankedHost(hi, failures = 1)
        val fourth = HostCache.Entry.RankedHost(hi, failures = 2)
        val fourthB = HostCache.Entry.RankedHost(hi, failures = 2)

        assertEquals(first, firstB)
        assertTrue(first < second)
        assertTrue(first < third)
        assertTrue(first < fourth)

        assertEquals(second, secondB)
        assertTrue(second > first)
        assertTrue(second < third)
        assertTrue(second < fourth)

        assertEquals(third, thirdB)
        assertTrue(third > first)
        assertTrue(third > second)
        assertTrue(third < fourth)

        assertEquals(fourth, fourthB)
        assertTrue(fourth > first)
        assertTrue(fourth > second)
        assertTrue(fourth > third)
    }

    @Test fun testSerializeRankedHost() {
        val hi4 = HostInfo("test.local", "192.168.0.137", 1234)
        val json4 = """{"hostname":"test.local","address":"192.168.0.137","port":1234}"""
        assertEquals(json4, Json.encodeToString(hi4))
        assertEquals(hi4, Json.decodeFromString(json4))
        val hi6 = HostInfo("test.local", "fe80::1234:56ff:fe78:9abc", 8000)
        val json6 = """{"hostname":"test.local","address":"fe80::1234:56ff:fe78:9abc","port":8000}"""
        assertEquals(json6, Json.encodeToString(hi6))
        assertEquals(hi6, Json.decodeFromString(json6))
    }

    @OptIn(ExperimentalTime::class)
    @Test fun testEntry() = runTest {
        MockClock(this, "2025-11-27T18:20:40Z").use { clock ->
            val ent = HostCache.Entry()
            val h1 = HostInfo("h1", "192.168.0.1", 0)
            val h2 = HostInfo("h2", "10.0.0.1", 0)
            val h3 = HostInfo("h3", "fe80::1", 0)
            var lastUpdate = clock.now()

            val update: suspend (Int, HostInfo, Duration?) -> Unit = { g, h, d ->
                clock.skip(d ?: 100.milliseconds)
                ent.update(h, g, d)
            }

            assertEquals(emptyList(), ent.getOrdered())

            update(1, h1, 1.milliseconds)
            update(1, h2, 2.milliseconds)
            update(1, h3, 3.milliseconds)
            assertEquals(listOf(h1, h2, h3), ent.getOrdered())
            assertEquals(h1, ent.getBest())
            assertTrue(lastUpdate < ent.updated, "Expected $lastUpdate < ${ent.updated}")
            lastUpdate = ent.updated

            update(2, h3, 3.milliseconds)
            assertEquals(listOf(h3, h1, h2), ent.getOrdered())
            assertEquals(h3, ent.getBest())
            assertTrue(lastUpdate < ent.updated)
            lastUpdate = ent.updated

            update(2, h2, 2.milliseconds)
            assertEquals(listOf(h2, h3, h1), ent.getOrdered())
            assertEquals(h2, ent.getBest())
            assertTrue(lastUpdate < ent.updated)
            lastUpdate = ent.updated

            update(2, h1, 10.milliseconds)
            assertEquals(listOf(h2, h3, h1), ent.getOrdered())
            assertEquals(h2, ent.getBest())
            assertTrue(lastUpdate < ent.updated)
            lastUpdate = ent.updated

            update(2, h2, null)
            assertEquals(listOf(h3, h1, h2), ent.getOrdered())
            assertEquals(h3, ent.getBest())
            assertTrue(lastUpdate < ent.updated)
            lastUpdate = ent.updated

            update(3, h1, null)
            update(3, h2, null)
            assertEquals(listOf(h3, h1, h2), ent.getOrdered())
            assertEquals(h3, ent.getBest())
            assertTrue(lastUpdate < ent.updated)
            lastUpdate = ent.updated

            update(3, h1, null)
            update(4, h1, null)
            assertEquals(listOf(h3, h2, h1), ent.getOrdered())
            assertEquals(h3, ent.getBest())
            assertTrue(lastUpdate < ent.updated)
            lastUpdate = ent.updated

            update(5, h3, 3.milliseconds)
            update(5, h1, 1.seconds)
            assertEquals(listOf(h3, h1, h2), ent.getOrdered())
            assertEquals(h3, ent.getBest())
            assertTrue(lastUpdate < ent.updated)
            lastUpdate = ent.updated

            update(5, h2, 2.milliseconds)
            assertEquals(listOf(h2, h3, h1), ent.getOrdered())
            assertEquals(h2, ent.getBest())
            assertTrue(lastUpdate < ent.updated)
            lastUpdate = ent.updated

            (0..HostCache.Entry.MAX_FAILURES).forEach { _ ->
                update(6, h3, null)
            }
            assertEquals(listOf(h2, h1), ent.getOrdered())
            assertEquals(h2, ent.getBest())
            assertTrue(lastUpdate < ent.updated)
            lastUpdate = ent.updated

            update(7, h3, 1.milliseconds)
            assertEquals(listOf(h3, h2, h1), ent.getOrdered())
            assertEquals(h3, ent.getBest())
            assertTrue(lastUpdate < ent.updated)
        }
    }

    @Test fun testSerializeEntry() = runTest {
        val time1 = "2025-12-20T19:17:45Z"
        val time2 = "2025-12-20T19:17:48Z"
        MockClock(this, time1).use { clock ->
            val hi4 = HostInfo("test.local", "192.168.0.137", 80)
            val hi6 = HostInfo("test.local", "fe80::1234:56ff:fe78:9abc", 80)
            val hibad = HostInfo("test.local", "192.168.0.138", 80)
            val ent = HostCache.Entry()
            val json = """{"name":"Testy","updated":"$time2","addresses":[""" +
                    """{"info":{"hostname":"test.local","address":"192.168.0.138","port":80},"failures":1},""" +
                    """{"info":{"hostname":"test.local","address":"192.168.0.137","port":80},"delay":"PT0.004S"},""" +
                    """{"info":{"hostname":"test.local","address":"fe80::1234:56ff:fe78:9abc","port":80},"delay":"PT0.006S"}]}"""

            ent.name = "Testy"
            ent.update(hibad, 1, null)
            ent.update(hi4, 1, 4.milliseconds)
            clock.skip(Instant.parse(time2))
            ent.update(hi6, 1, 6.milliseconds)

            assertEquals(json, Json.encodeToString(ent))
            val ent2: HostCache.Entry = Json.decodeFromString(json)
            assertEquals(Instant.parse(time2), ent2.updated)
            assertEquals(listOf(hi4, hi6, hibad), ent2.getOrdered())
        }
    }

    private interface CacheTest {
        val scope: TestScope
        val clock: MockClock
        val start: Instant
        val uuidA: Uuid
        val uuidB: Uuid
        val hostA4: HostInfo
        val hostA6: HostInfo
        val hostB4: HostInfo
        val seen: MutableSet<HostInfo>
        val hc: HostCache

        fun <T> tester(vararg input: Triple<HostInfo,Duration,Pair<Uuid,T>?>)
        : suspend (HostInfo) -> Pair<Uuid,T>? = { host ->
            seen.add(host)
            input.first { (h, _, _) -> h === host }.let { (_, dur, ret) ->
                delay(dur)
                ret
            }
        }
    }

    // @BeforeTest fun setupLogging() { System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug") }

    private fun hcTest(body: suspend CacheTest.() -> Unit) = runTest(timeout = 5.seconds) {
        val start = Instant.parse("2025-03-14T13:59:26.536Z")
        MockClock(this, start).use { clock ->
            body(object : CacheTest {
                override val scope = this@runTest
                override val clock = clock
                override val start = start
                override val uuidA = Uuid.parse("550c9762-9596-4d8c-b50c-0716eb7b3d8a")
                override val uuidB = Uuid.parse("f008a465-3616-4e4b-a9f3-219ff77c9bc9")
                override val hostA4 = HostInfo("alpha.local", "172.20.30.40", 80)
                override val hostA6 = HostInfo("alpha.local", "fe80::204:6ff:fe08:a0c", 80)
                override val hostB4 = HostInfo("beta.local", "172.20.30.80", 80)
                override val seen = mutableSetOf<HostInfo>()
                override val hc = HostCache()
            })
        }
    }

    @Test fun testHCEmpty() = hcTest {
        val res = hc.testAddresses<String>(scope, null, emptyList()) { check(false); null }
        assertEquals(null, res)
        assertThrows<NoSuchElementException> {
            hc.getPreferredAddress(scope, uuidA) { Pair(uuidA, "")  }
        }
        assertEquals(0.milliseconds, clock.elapsed())
    }

    @Test fun testHCSingle() = hcTest {
        val res = hc.testAddresses(scope, null, listOf(hostA4), body = tester(
            Triple(hostA4, 40.milliseconds, Pair(uuidA, "a4"))))
        scope.advanceUntilIdle()
        assertEquals("a4", res)
        assertEquals(setOf(hostA4), seen)
        assertEquals(40.milliseconds, clock.elapsed())
        assertEquals(hostA4, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
    }

    @Test fun testHCAll() = hcTest {
        val res = hc.testAddresses(scope, null, listOf(hostA6, hostA4), true, tester(
            Triple(hostA4, 40.milliseconds, Pair(uuidA, "a4")),
            Triple(hostA6, 60.milliseconds, Pair(uuidA, "a6"))))
        scope.advanceUntilIdle()
        assertEquals("a4", res)
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(60.milliseconds, clock.elapsed())
        assertEquals(hostA4, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
    }

    @Test fun testHCEarlySuccess() = hcTest {
        val res = hc.testAddresses(scope, null, listOf(hostA6, hostA4, hostB4), body = tester(
            Triple(hostA4, 40.milliseconds, Pair(uuidA, "a4")),
            Triple(hostA6, 60.milliseconds, Pair(uuidA, "a6")),
            Triple(hostB4, 20.milliseconds, Pair(uuidB, "b4"))))
        scope.advanceUntilIdle()
        assertEquals("a6", res)
        assertEquals(setOf(hostA6), seen)
        assertEquals(60.milliseconds, clock.elapsed())
        assertEquals(hostA6, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
    }

    @Test fun testHCSlowFirst() = hcTest {
        val res = hc.testAddresses(scope, null, listOf(hostA6, hostA4), body = tester(
            Triple(hostA4, 40.milliseconds, Pair(uuidA,"a4")),
            Triple(hostA6, 110.milliseconds, Pair(uuidA,"a6")),
            Triple(hostB4, 20.milliseconds, Pair(uuidB, "b4"))))
        scope.advanceUntilIdle()
        assertEquals("a6", res)
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(140.milliseconds, clock.elapsed())
        assertEquals(hostA4, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
    }

    @Test fun testHCFastError() = hcTest {
        val res = hc.testAddresses(scope, null, listOf(hostA4, hostA6), body = tester(
            Triple(hostA4, 1.milliseconds, null),
            Triple(hostA6, 60.milliseconds, Pair(uuidA,"a6")),
            Triple(hostB4, 20.milliseconds, Pair(uuidB, "b4"))))
        scope.advanceUntilIdle()
        assertEquals("a6", res)
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(160.milliseconds, clock.elapsed())
        assertEquals(hostA6, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
    }

    @Test fun testHCException() = hcTest {
        val res = hc.testAddresses(scope, null, listOf(hostA4, hostA6), true) { host ->
            seen.add(host)
            check(host === hostA4)
            delay(30.milliseconds)
            Pair(uuidA, "a4")
        }
        scope.advanceUntilIdle()
        assertEquals("a4", res)
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(30.milliseconds, clock.elapsed())
        assertEquals(hostA4, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
    }

    @Test fun testHCRetest() = hcTest {
        val res = hc.testAddresses(scope, null, listOf(hostA6, hostA4), body = tester(
            Triple(hostA4, 40.milliseconds, Pair(uuidA,"a4")),
            Triple(hostA6, 110.milliseconds, Pair(uuidA,"a6")),
            Triple(hostB4, 20.milliseconds, Pair(uuidB, "b4"))))
        scope.advanceUntilIdle()
        assertEquals("a6", res)
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(140.milliseconds, clock.elapsed())
        assertEquals(hostA4, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })

        seen.clear()
        clock.skip(1.hours)

        val best = hc.getPreferredAddress(scope, uuidA, tester(
            Triple(hostA4, 271.milliseconds, Pair(uuidA,"a4")),
            Triple(hostA6, 55.milliseconds, Pair(uuidA,"a6"))))
        scope.advanceUntilIdle()
        assertEquals(hostA6, best)
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(1.hours + 411.milliseconds, clock.elapsed())
        assertEquals(hostA6, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
    }

    @Test fun testHCRetestError() = hcTest {
        val res = hc.testAddresses(scope, null, listOf(hostA6, hostA4), body = tester(
            Triple(hostA4, 40.milliseconds, Pair(uuidA,"a4")),
            Triple(hostA6, 110.milliseconds, Pair(uuidA,"a6")),
            Triple(hostB4, 20.milliseconds, Pair(uuidB, "b4"))))
        scope.advanceUntilIdle()
        assertEquals("a6", res)
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(140.milliseconds, clock.elapsed())
        assertEquals(hostA4, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })

        seen.clear()
        clock.skip(1.hours)

        val best = hc.getPreferredAddress(scope, uuidA, tester(
            Triple(hostA4, 10.milliseconds, null),
            Triple(hostA6, 50.milliseconds, Pair(uuidA,"a6")),
            Triple(hostB4, 20.milliseconds, Pair(uuidB, "b4"))))
        scope.advanceUntilIdle()
        assertEquals(hostA6, best)
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(1.hours + 290.milliseconds, clock.elapsed())
        assertEquals(hostA6, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
    }

    @Test fun testHCSteal() = hcTest {
        var res = hc.testAddresses(scope, null, listOf(hostA4, hostA6), true, tester(
            Triple(hostA4, 40.milliseconds, Pair(uuidA,"a4")),
            Triple(hostA6, 60.milliseconds, Pair(uuidA,"a6"))))
        scope.advanceUntilIdle()
        assertEquals("a4", res)
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(60.milliseconds, clock.elapsed())
        assertEquals(hostA4, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })

        seen.clear()

        res = hc.testAddresses(scope, null, listOf(hostB4), body = tester(
            Triple(hostB4, 30.milliseconds, Pair(uuidB, "b4"))))
        scope.advanceUntilIdle()
        assertEquals("b4", res)
        assertEquals(setOf(hostB4), seen)
        assertEquals(90.milliseconds, clock.elapsed())
        assertEquals(hostB4, hc.getPreferredAddress<String>(scope, uuidB) { check(false); null })
        assertEquals(hostA4, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })

        seen.clear()
        clock.skip(1.hours)

        val best = hc.getPreferredAddress(scope, uuidA, tester(
            Triple(hostA4, 30.milliseconds, Pair(uuidB, "b4s")),
            Triple(hostA6, 60.milliseconds, Pair(uuidA, "a6"))))
        scope.advanceUntilIdle()
        assertEquals(hostA6, best)
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(1.hours + 250.milliseconds, clock.elapsed())
        assertEquals(hostA4, hc.getPreferredAddress<String>(scope, uuidB) { check(false); null })
        assertEquals(hostA6, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
    }

    @Test fun testHCAccessors() = hcTest {
        hc.setName(uuidA, "Alpha")
        assertEquals(mapOf(uuidA to "Alpha"), hc.names())
        assertEquals(mapOf(uuidA to emptyList()), hc.hosts())

        hc.testAddresses(scope, null, listOf(hostA4, hostA6), true, tester(
            Triple(hostA4, 40.milliseconds, Pair(uuidA,"a4")),
            Triple(hostA6, 60.milliseconds, Pair(uuidA,"a6"))))
        hc.testAddresses(scope, uuidB, listOf(hostB4), true, tester<String>())
        scope.advanceUntilIdle()
        assertEquals(mapOf(uuidA to "Alpha", uuidB to null), hc.names())
        assertEquals(mapOf(uuidA to listOf(hostA4, hostA6), uuidB to listOf(hostB4)), hc.hosts())

        clock.skip(1.hours)

        hc.setName(uuidB, "Beta")
        hc.getPreferredAddress(scope, uuidA, tester(
            Triple(hostA4, 150.milliseconds, Pair(uuidA, "a4")),
            Triple(hostA6, 60.milliseconds, Pair(uuidA, "a6"))))
        scope.advanceUntilIdle()
        assertEquals(mapOf(uuidA to "Alpha", uuidB to "Beta"), hc.names())
        assertEquals(mapOf(uuidA to listOf(hostA6, hostA4), uuidB to listOf(hostB4)), hc.hosts())

        hc.forget(uuidB)
        assertEquals(mapOf(uuidA to "Alpha"), hc.names())
        assertEquals(mapOf(uuidA to listOf(hostA6, hostA4)), hc.hosts())
        assertEquals(hostA6, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
        assertThrows<NoSuchElementException> {
            hc.getPreferredAddress(scope, uuidB) { Pair(uuidB, "")  }
        }
    }

    @Test fun testHCLingering() = hcTest {
        val mutex = Mutex()
        val res = hc.testAddresses(scope, uuidA, listOf(hostA4, hostA6), true) { host ->
            seen.add(host)
            if (host === hostA4)
                Pair(uuidA, "a4")
            else mutex.withLock {
                delay(3.hours)
                Pair(uuidA, "a6")
            }
        }
        assertEquals("a4", res)
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(mapOf(uuidA to listOf(hostA4)), hc.hosts())
        assertEquals(hostA4, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
        assertEquals(0.milliseconds, clock.elapsed())

        seen.clear()
        clock.skip(1.hours)

        val best = hc.getPreferredAddress(scope, uuidA) { host ->
            mutex.withLock {
                seen.add(host)
                if (host === hostA4) {
                    delay(50.milliseconds)
                    Pair(uuidA, "a4")
                } else {
                    delay(20.milliseconds)
                    Pair(uuidA, "a6")
                }
            }
        }
        scope.advanceUntilIdle()
        assertEquals(hostA4, best)
        assertEquals(setOf(hostA4), seen)
        assertEquals(mapOf(uuidA to listOf(hostA4, hostA6)), hc.hosts())
        assertEquals(hostA4, hc.getPreferredAddress<String>(scope, uuidA) { check(false); null })
        assertEquals(3.hours + 50.milliseconds, clock.elapsed())
    }

    @Test fun testOnUpdate() = hcTest {
        var updates = 0
        hc.onUpdate = { updates++ }

        hc.testAddresses(scope, null, listOf(hostA4, hostA6), true, tester(
            Triple(hostA4, 40.milliseconds, Pair(uuidA,"a4")),
            Triple(hostA6, 60.milliseconds, Pair(uuidA,"a6"))))
        scope.advanceUntilIdle()
        assertEquals(1, updates)

        hc.setName(uuidA, "Alpha")
        assertEquals(2, updates)

        seen.clear()
        clock.skip(1.hours)

        hc.getPreferredAddress(scope, uuidA) { host ->
            seen.add(host)
            delay(500.milliseconds)
            Pair(uuidA, "")
        }
        scope.advanceUntilIdle()
        assertEquals(setOf(hostA4, hostA6), seen)
        assertEquals(3, updates)

        hc.forget(uuidA)
        assertEquals(4, updates)
    }

    @Test fun testSerializeHostCache() = runTest(timeout = 5.seconds) {
        MockClock(this, "2025-07-14T13:51:12Z").use {
            val hi = HostInfo("test.local", "10.1.2.3", 8000)
            val hc = HostCache()
            val uuid = Uuid.parse("331a72be-9383-42e9-a677-9a130e4e6c57")
            val json = """{"servers":{"$uuid":{"name":"Testy","updated":"2025-07-14T13:51:12.007Z","addresses":[""" +
                    """{"info":{"hostname":"test.local","address":"10.1.2.3","port":8000},"delay":"PT0.007S"}]}}}"""
            hc.testAddresses(this@runTest, null, listOf(hi)) {
                delay(7.milliseconds)
                Pair(uuid, "")
            }
            hc.setName(uuid, "Testy")
            assertEquals(json, Json.encodeToString(hc))
        }
    }
}
