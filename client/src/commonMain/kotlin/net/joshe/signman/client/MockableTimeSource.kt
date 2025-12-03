package net.joshe.signman.client

import kotlin.time.TimeSource

object MockableTimeSource : TimeSource {
    private var source: TimeSource = TimeSource.Monotonic

    override fun markNow() = source.markNow()

    internal fun replace(timeSource: TimeSource): TimeSource {
        val prev = source
        source = timeSource
        return prev
    }
}
