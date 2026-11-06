package org.openscreentime.pin

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration

/** Result of checking a PIN. */
sealed interface VerifyResult {
    data object Correct : VerifyResult

    /**
     * Wrong PIN. [attemptsBeforeWait] is how many more wrong tries are allowed
     * before a wait starts (0 once waits have begun). [waitFor] is the wait
     * that starts now, if any.
     */
    data class Wrong(val attemptsBeforeWait: Int, val waitFor: Duration?) : VerifyResult

    /** Too many wrong PINs: no PIN is checked until [remaining] has passed. */
    data class Waiting(val remaining: Duration) : VerifyResult

    data object NoPinSet : VerifyResult
}

sealed interface SetPinResult {
    data object Saved : SetPinResult

    /** Not 4–6 digits. */
    data object InvalidFormat : SetPinResult

    /** A PIN already exists; it can only be changed with the current PIN. */
    data object AlreadySet : SetPinResult
}

sealed interface ChangePinResult {
    data object Changed : ChangePinResult

    /** The new PIN isn't 4–6 digits. Checked first, so a typo doesn't cost an attempt. */
    data object InvalidNewPin : ChangePinResult

    /** The current PIN was wrong or a wait is in progress. */
    data class NotVerified(val result: VerifyResult) : ChangePinResult
}

/**
 * The screen time PIN: setting, checking and changing it, with growing waits
 * after wrong attempts (see [LockoutSchedule]).
 *
 * Every function wipes the PIN arrays it is given. PINs are never logged.
 *
 * Waits are timed on [DeviceClock], so changing the phone's clock doesn't
 * shorten them. After a restart that clock starts over, so a wait in
 * progress starts again from its full length rather than being skipped.
 */
class PinManager(
    private val store: PinStore,
    private val hasher: PinHasher,
    private val clock: DeviceClock,
    /** Where the slow hashing runs. Tests pass a dispatcher that runs it straight away. */
    private val hashDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    // One check at a time, so two quick guesses can't both slip past the counter.
    private val mutex = Mutex()

    val isPinSet: Flow<Boolean> = store.isPinSet

    suspend fun setInitialPin(pin: CharArray): SetPinResult = try {
        mutex.withLock {
            when {
                !PinRules.isValid(pin) -> SetPinResult.InvalidFormat
                store.read().credential != null -> SetPinResult.AlreadySet
                else -> {
                    store.write(PinState(credential = hash { hasher.create(pin) }))
                    SetPinResult.Saved
                }
            }
        }
    } finally {
        pin.wipe()
    }

    suspend fun verify(pin: CharArray): VerifyResult = try {
        mutex.withLock { verifyLocked(pin) }
    } finally {
        pin.wipe()
    }

    suspend fun changePin(current: CharArray, newPin: CharArray): ChangePinResult = try {
        mutex.withLock {
            if (!PinRules.isValid(newPin)) return@withLock ChangePinResult.InvalidNewPin
            when (val result = verifyLocked(current)) {
                VerifyResult.Correct -> {
                    store.write(PinState(credential = hash { hasher.create(newPin) }))
                    ChangePinResult.Changed
                }
                else -> ChangePinResult.NotVerified(result)
            }
        }
    } finally {
        current.wipe()
        newPin.wipe()
    }

    /** Time left before the PIN can be tried again, or null if it can be tried now. */
    suspend fun waitRemaining(): Duration? = mutex.withLock {
        val state = store.read()
        currentWait(state).also { (_, updated) -> if (updated !== state) store.write(updated) }.first
    }

    private suspend fun verifyLocked(pin: CharArray): VerifyResult {
        val initial = store.read()
        val credential = initial.credential ?: return VerifyResult.NoPinSet

        val (remaining, state) = currentWait(initial)
        if (state !== initial) store.write(state)
        if (remaining != null) return VerifyResult.Waiting(remaining)

        if (hash { hasher.matches(pin, credential) }) {
            store.write(PinState(credential = credential))
            return VerifyResult.Correct
        }

        val failed = state.failedAttempts + 1
        val wait = LockoutSchedule.waitAfter(failed)
        val lockout = if (wait.isZero) {
            null
        } else {
            Lockout(
                bootCount = clock.bootCount(),
                endsAtSinceBootMillis = (clock.sinceBoot() + wait).toMillis(),
                durationMillis = wait.toMillis(),
            )
        }
        // Saved before the answer is returned, so stopping the app can't undo the count.
        store.write(PinState(credential, failed, lockout))
        return VerifyResult.Wrong(
            attemptsBeforeWait = (LockoutSchedule.FREE_ATTEMPTS - failed).coerceAtLeast(0),
            waitFor = wait.takeUnless { it.isZero },
        )
    }

    /**
     * The wait still to go, and the state to save. If the phone restarted
     * during a wait, the wait starts over at full length from now.
     */
    private fun currentWait(state: PinState): Pair<Duration?, PinState> {
        val lockout = state.lockout ?: return null to state
        val now = clock.sinceBoot().toMillis()

        if (lockout.bootCount != clock.bootCount()) {
            val restarted = lockout.copy(
                bootCount = clock.bootCount(),
                endsAtSinceBootMillis = now + lockout.durationMillis,
            )
            return Duration.ofMillis(lockout.durationMillis) to
                PinState(state.credential, state.failedAttempts, restarted)
        }
        val remaining = lockout.endsAtSinceBootMillis - now
        return if (remaining > 0) Duration.ofMillis(remaining) to state else null to state
    }

    /** Hashing is deliberately slow, so keep it off the main thread. */
    private suspend fun <T> hash(block: () -> T): T = withContext(hashDispatcher) { block() }
}
