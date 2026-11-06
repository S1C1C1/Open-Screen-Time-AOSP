package org.openscreentime.ui.pin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.openscreentime.pin.ChangePinResult
import org.openscreentime.pin.PinBuffer
import org.openscreentime.pin.PinManager
import org.openscreentime.pin.PinRules
import org.openscreentime.pin.SetPinResult
import org.openscreentime.pin.VerifyResult
import org.openscreentime.pin.wipe
import java.time.Duration

/** Why the PIN screens are open. */
enum class PinFlowKind {
    /** Check the PIN before a protected change. */
    Verify,

    /** Create the first PIN. */
    Setup,

    /** Replace the PIN, after entering the current one. */
    Change,
}

/** Which PIN screen is showing. */
enum class PinStep { Intro, EnterCurrent, EnterNew, ConfirmNew, Finished }

/** A message under the PIN dots. */
sealed interface PinMessage {
    /** [attemptsBeforeWait] more wrong tries are allowed before a wait. */
    data class WrongPin(val attemptsBeforeWait: Int) : PinMessage

    /** The confirmation didn't match the first entry. */
    data object Mismatch : PinMessage

    /** Saving failed, e.g. the PIN was changed elsewhere; the user starts again. */
    data object NotSaved : PinMessage
}

/** What the PIN screens show. Contains only how many digits were typed, never the digits. */
data class PinFlowUi(
    val kind: PinFlowKind,
    val step: PinStep,
    val digitCount: Int = 0,
    val message: PinMessage? = null,
    /** Set while a wait after wrong PINs is in progress; the number pad is off. */
    val waitRemaining: Duration? = null,
    /** True while a PIN is being checked or saved. */
    val busy: Boolean = false,
) {
    val canType: Boolean get() = !busy && waitRemaining == null && digitCount < PinRules.MAX_LENGTH
    val canSubmit: Boolean get() = !busy && waitRemaining == null && digitCount >= PinRules.MIN_LENGTH
}

/**
 * Runs the PIN screens: typing, checking, setting up and changing the PIN.
 * Typed digits live only in [PinBuffer]s and are wiped as soon as they're
 * used, when the flow ends, or when it's cancelled.
 */
class PinFlowController(private val pinManager: PinManager) {

    private val state = MutableStateFlow<PinFlowUi?>(null)

    /** The PIN screens to show, or null when none are open. */
    val ui: StateFlow<PinFlowUi?> = state.asStateFlow()

    private val input = PinBuffer()
    private var newPin: CharArray? = null
    private var currentPin: CharArray? = null
    private var onVerified: () -> Unit = {}

    /** Opens the PIN screens. For [PinFlowKind.Verify], [onSuccess] runs after the correct PIN. */
    suspend fun start(kind: PinFlowKind, onSuccess: () -> Unit = {}) {
        clearSecrets()
        onVerified = onSuccess
        val step = if (kind == PinFlowKind.Setup) PinStep.Intro else PinStep.EnterCurrent
        state.value = PinFlowUi(kind, step)
        if (step == PinStep.EnterCurrent) {
            // Show a wait that's still running from earlier wrong attempts.
            val wait = pinManager.waitRemaining()
            state.update { it?.copy(waitRemaining = wait) }
        }
    }

    fun continueFromIntro() {
        state.update { if (it?.step == PinStep.Intro) it.copy(step = PinStep.EnterNew) else it }
    }

    fun addDigit(digit: Char) {
        val ui = state.value ?: return
        if (!ui.canType) return
        input.append(digit)
        state.value = ui.copy(digitCount = input.length, message = null)
    }

    fun deleteDigit() {
        val ui = state.value ?: return
        if (ui.busy) return
        input.deleteLast()
        state.value = ui.copy(digitCount = input.length)
    }

    /** Called by the screen when a wait's countdown reaches zero. */
    fun waitFinished() {
        state.update { it?.copy(waitRemaining = null) }
    }

    /** Closes the PIN screens and forgets anything typed. */
    fun cancel() {
        clearSecrets()
        state.value = null
    }

    suspend fun submit() {
        val ui = state.value ?: return
        if (!ui.canSubmit) return
        when (ui.step) {
            PinStep.EnterCurrent -> checkCurrent(ui)
            PinStep.EnterNew -> {
                newPin = input.take()
                state.value = ui.copy(step = PinStep.ConfirmNew, digitCount = 0, message = null)
            }
            PinStep.ConfirmNew -> confirmNew(ui)
            PinStep.Intro, PinStep.Finished -> Unit
        }
    }

    private suspend fun checkCurrent(ui: PinFlowUi) {
        state.value = ui.copy(busy = true)
        val attempt = input.take()
        // Changing the PIN needs the current one again at the end; keep a copy only if it's right.
        val keep = if (ui.kind == PinFlowKind.Change) attempt.copyOf() else null
        val result = pinManager.verify(attempt) // wipes attempt
        val after = ui.copy(digitCount = 0, busy = false)
        when (result) {
            VerifyResult.Correct, VerifyResult.NoPinSet -> when (ui.kind) {
                PinFlowKind.Change -> {
                    currentPin = keep
                    state.value = after.copy(step = PinStep.EnterNew, message = null)
                }
                else -> {
                    state.value = null
                    onVerified()
                }
            }
            is VerifyResult.Wrong -> {
                keep?.wipe()
                state.value = after.copy(
                    message = PinMessage.WrongPin(result.attemptsBeforeWait),
                    waitRemaining = result.waitFor,
                )
            }
            is VerifyResult.Waiting -> {
                keep?.wipe()
                state.value = after.copy(waitRemaining = result.remaining, message = null)
            }
        }
    }

    private suspend fun confirmNew(ui: PinFlowUi) {
        val first = newPin ?: return
        val confirmation = input.take()
        val matches = first.contentEquals(confirmation)
        confirmation.wipe()
        newPin = null
        if (!matches) {
            first.wipe()
            state.value = ui.copy(step = PinStep.EnterNew, digitCount = 0, message = PinMessage.Mismatch)
            return
        }

        state.value = ui.copy(busy = true, digitCount = 0)
        val saved = when (ui.kind) {
            PinFlowKind.Setup -> pinManager.setInitialPin(first) == SetPinResult.Saved
            PinFlowKind.Change -> {
                val current = currentPin ?: CharArray(0)
                currentPin = null
                pinManager.changePin(current, first) == ChangePinResult.Changed
            }
            PinFlowKind.Verify -> false
        }
        first.wipe()
        state.value = if (saved) {
            ui.copy(step = PinStep.Finished, digitCount = 0, busy = false, message = null)
        } else {
            val restart = if (ui.kind == PinFlowKind.Setup) PinStep.Intro else PinStep.EnterCurrent
            ui.copy(step = restart, digitCount = 0, busy = false, message = PinMessage.NotSaved)
        }
    }

    private fun clearSecrets() {
        input.clear()
        newPin?.wipe()
        newPin = null
        currentPin?.wipe()
        currentPin = null
    }
}
