package net.joshe.signman.client

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.measureTimedValue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
@Serializable
class HostCache(@Transient var onUpdate: ((HostCache) -> Unit)? = null) {
    companion object {
        private val expireTime = 1.hours
        private const val TEST_INTERVAL_MILLIS = 100L
    }

    @Transient private val log = LoggerFactory.getLogger(this::class.java)
    @Transient private val mutex = Mutex()
    @Transient private var testGeneration: Int = 0
    private val servers = mutableMapOf<Uuid, Entry>()

    private fun getHost(uuid: Uuid) = servers.getOrPut(uuid) { Entry() }

    suspend fun names() = mutex.withLock {
        servers.mapValues { it.value.name }
    }

    suspend fun setName(uuid: Uuid, name: String) {
        mutex.withLock { getHost(uuid).name = name }
        onUpdate?.invoke(this)
    }

    suspend fun hosts() = mutex.withLock {
        servers.mapValues { it.value.getOrdered() }
    }

    suspend fun forget(uuid: Uuid) {
        mutex.withLock { servers.remove(uuid) }
        onUpdate?.invoke(this)
    }

    suspend fun <T> getPreferredAddress(scope: CoroutineScope, uuid: Uuid,
                                        body: suspend (HostInfo) -> Pair<Uuid,T>?) = mutex.withLock {
        servers.getValue(uuid).let { server ->
            val now = MockableClock.now()
            if (server.updated + expireTime <= now) {
                log.debug("re-testing stale cache for {}: {} + {} > {}", uuid, server.updated, expireTime, now)
                testAddressesLocked(scope, uuid, server.getOrdered(), false, body)
            }
            server.getBest()
        }
    }

    suspend fun <T> testAddresses(scope: CoroutineScope,
                                  uuid: Uuid?,
                                  addresses: List<HostInfo>,
                                  tryAll: Boolean = false,
                                  body: suspend (HostInfo) -> Pair<Uuid, T>?) = mutex.withLock {
        testAddressesLocked(scope, uuid, addresses, tryAll, body)
    }

    suspend fun <T> testAddressesLocked(scope: CoroutineScope,
                                  uuid: Uuid?,
                                  addresses: List<HostInfo>,
                                  tryAll: Boolean = false,
                                  body: suspend (HostInfo) -> Pair<Uuid, T>?): T? {
        log.debug("testing {} addresses: {}", addresses.size, addresses)

        if (addresses.isEmpty())
            return null

        val gen = testGeneration
        testGeneration++

        val firstSuccess = MutableSharedFlow<T?>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)
        val testerParentJob = SupervisorJob(scope.coroutineContext[Job])
        val testerScope = CoroutineScope(testerParentJob + scope.coroutineContext)
        val testerChildJobs = mutableListOf<Job>()

        val spawnerJob = scope.launch(CoroutineName("spawner for $uuid")) {
            val spawnerJob = coroutineContext.job
            log.trace("started spawner")
            for (info in addresses) {
                testerChildJobs.add(testerScope.launch(CoroutineName("tester for $uuid with $info")) {
                    testSingleAddress(gen, uuid, info, body)?.let { res ->
                        firstSuccess.emit(res)
                        spawnerJob.cancel()
                    }
                })
                // XXX it would be nice to immediately skip to the next coroutine if the previous ones have all failed
                if (!tryAll)
                    delay(TEST_INTERVAL_MILLIS)
            }
            log.trace("spawner completed normally")
        }

        scope.launch(CoroutineName("collector for $uuid")) {
            log.trace("started collector")
            spawnerJob.join()
            log.trace("joined spawner")
            testerChildJobs.joinAll()
            log.trace("joined children")
            testerParentJob.complete()
            firstSuccess.emit(null)
            log.trace("finished collecting")
            onUpdate?.invoke(this@HostCache)
        }

        log.trace("waiting for result")
        val first = firstSuccess.first()
        log.trace("result: {}", first)
        return first
    }

    private suspend fun <T> testSingleAddress(generation: Int,
                                              expectUuid: Uuid?,
                                              info: HostInfo,
                                              body: suspend (HostInfo) -> Pair<Uuid, T>?): T? {
        log.info("testing uuid {} with {}", expectUuid, info)
        val (respPair, elapsed) = MockableTimeSource.measureTimedValue {
            try { body(info) }
            catch (e: Exception) {
                log.error("exception while testing {}", info, e)
                null
            }
        }

        if (respPair == null) {
            if (expectUuid != null)
                getHost(expectUuid).update(info, generation, null)
            log.info("failure while testing {} with {} after {}", expectUuid, info, elapsed)
            return null
        }

        val (foundUuid, retVal) = respPair
        getHost(foundUuid).update(info, generation, elapsed)
        if (expectUuid != null && expectUuid != foundUuid) {
            getHost(expectUuid).update(info, generation, null)
            log.info("expected {} with {} but found {} after {}", expectUuid, info, foundUuid, elapsed)
            return null
        }
        log.info("success for {} with {} after {}", foundUuid, info, elapsed)
        return retVal
    }

    @Serializable
    internal class Entry() {
        companion object {
            const val MAX_FAILURES = 100
        }

        @Transient private val mutex = Mutex()
        var name: String? = null
        var updated = Instant.DISTANT_PAST
            private set
        private val addresses = mutableListOf<RankedHost>()
        @Transient private var cachedBest: RankedHost? = null

        suspend fun getBest(): HostInfo = mutex.withLock {
            if (cachedBest == null) {
                var best: RankedHost? = null
                for (item in addresses)
                    if (best == null || best > item)
                        best = item
                cachedBest = best
            }
            return cachedBest!!.info
        }

        suspend fun getOrdered() = mutex.withLock { addresses.sorted().map { it.info } }

        suspend fun update(info: HostInfo, generation: Int, delay: Duration?): Unit = mutex.withLock {
            val item = addresses.firstOrNull { it.info == info } ?: RankedHost(info).also { addresses.add(it) }
            if (item.generation > generation)
                return
            updated = MockableClock.now()
            item.generation = generation
            item.delay = delay
            if (delay != null)
                item.failures = 0
            else
                item.failures++
            if (item.failures > MAX_FAILURES)
                addresses.remove(item)
            cachedBest = null
        }

        @Serializable
        data class RankedHost(val info: HostInfo,
                              @Transient var generation: Int = 0,
                              var delay: Duration? = null,
                              var failures: Int = 0) : Comparable<RankedHost> {
            override fun compareTo(other: RankedHost) = when {
                delay != null && other.delay != null && generation == other.generation -> delay!!.compareTo(other.delay!!)
                delay != null && other.delay != null -> other.generation - generation
                delay != null && other.delay == null -> -1
                delay == null && other.delay != null -> 1
                else -> failures.compareTo(other.failures)
            }
        }
    }
}
