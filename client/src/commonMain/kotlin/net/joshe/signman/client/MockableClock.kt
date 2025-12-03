package net.joshe.signman.client

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object MockableClock : Clock {
    private var clock: Clock = Clock.System

    override fun now() = clock.now()

    internal fun replace(clock: Clock): Clock {
        val prev = this.clock
        this.clock = clock
        return prev
    }
}
