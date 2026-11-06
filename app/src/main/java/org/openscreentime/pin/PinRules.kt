package org.openscreentime.pin

import java.time.Duration

/** Rules for what a screen time PIN may be. */
object PinRules {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    fun isValid(pin: CharArray): Boolean =
        pin.size in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }
}

/**
 * How long to wait after wrong PINs in a row. Short PINs can be guessed, so
 * waits grow quickly once someone keeps trying, while a few honest mistakes
 * cost nothing or a minute.
 */
object LockoutSchedule {
    /** Wrong attempts allowed before the first wait. */
    const val FREE_ATTEMPTS = 4

    /** Wait required after [failedAttempts] wrong PINs in a row. */
    fun waitAfter(failedAttempts: Int): Duration = when {
        failedAttempts <= FREE_ATTEMPTS -> Duration.ZERO
        failedAttempts == 5 -> Duration.ofMinutes(1)
        failedAttempts == 6 -> Duration.ofMinutes(5)
        failedAttempts == 7 -> Duration.ofMinutes(15)
        failedAttempts == 8 -> Duration.ofHours(1)
        failedAttempts == 9 -> Duration.ofHours(4)
        else -> Duration.ofHours(24)
    }
}
