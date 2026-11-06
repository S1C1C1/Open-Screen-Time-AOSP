package org.openscreentime.usage

import java.time.Instant

/** The few kinds of Android usage events that matter for counting screen time. */
sealed interface UsageEvent {
    val time: Instant

    /** An app's screen (activity) came to the front. */
    data class AppResumed(
        override val time: Instant,
        val packageName: String,
        val className: String,
    ) : UsageEvent

    /** An app's screen left the front (paused or stopped). */
    data class AppPaused(
        override val time: Instant,
        val packageName: String,
        val className: String,
    ) : UsageEvent

    /** The screen turned off, the phone locked, or it shut down: nothing is being used. */
    data class ScreenOff(override val time: Instant) : UsageEvent
}
