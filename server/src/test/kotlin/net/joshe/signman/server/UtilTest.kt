package net.joshe.signman.server

import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class UtilTest {
    @Timeout(5, unit = TimeUnit.SECONDS)
    @Test fun testCacheableCollectShared() = runTest {
        val flow = MutableSharedFlow<String>(replay = 3)
        val res = mutableListOf<String>()

        val job = launch {
            flow.cancellableCollect(coroutineContext) { arg ->
                res.add(arg)
                if (res.size == 2)
                    currentCoroutineContext().cancel()
            }
        }

        flow.emit("foo")
        flow.emit("bar")
        flow.emit("baz")
        job.join()
        assertEquals(listOf("foo", "bar"), res)
    }

    @Timeout(5, unit = TimeUnit.SECONDS)
    @Test fun testCacheableCollectState() = runTest {
        val flow = MutableStateFlow("initial")

        flow.cancellableCollect(coroutineContext) { arg ->
            if (arg == "initial")
                flow.emit("updated")
            else if (arg == "updated")
                currentCoroutineContext().cancel()
        }

        assertEquals("updated", flow.value)
    }
}
