package net.joshe.signman.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

suspend fun <T> SharedFlow<T>.cancellableCollect(coroutineContext: CoroutineContext,
                                                 block: suspend CoroutineScope.(T) -> Unit) {
    val flow = this
    val scope = CoroutineScope(coroutineContext)
    scope.launch {
        flow.collect { block(scope, it) }
    }.join()
}
