package org.openscreentime.ui.pin

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openscreentime.pin.FakeDeviceClock
import org.openscreentime.pin.FakePinStore
import org.openscreentime.pin.VerifyResult
import org.openscreentime.pin.pin
import org.openscreentime.pin.testPinManager
import java.time.Duration

class PinFlowControllerTest {

    private val store = FakePinStore()
    private val clock = FakeDeviceClock()
    private val manager = testPinManager(store, clock)
    private val flow = PinFlowController(manager)

    private val ui get() = flow.ui.value

    private suspend fun type(digits: String, submit: Boolean = true) {
        digits.forEach(flow::addDigit)
        if (submit) flow.submit()
    }

    private suspend fun withPin(value: String = "4826") {
        manager.setInitialPin(pin(value))
    }

    @Test
    fun setupGoesIntroThenChooseThenConfirmThenDone() = runBlocking {
        flow.start(PinFlowKind.Setup)
        assertEquals(PinStep.Intro, ui!!.step)

        flow.continueFromIntro()
        assertEquals(PinStep.EnterNew, ui!!.step)

        type("4826")
        assertEquals(PinStep.ConfirmNew, ui!!.step)
        assertEquals(0, ui!!.digitCount)

        type("4826")
        assertEquals(PinStep.Finished, ui!!.step)
        assertTrue(manager.isPinSet.first())
        assertEquals(VerifyResult.Correct, manager.verify(pin("4826")))
    }

    @Test
    fun mismatchedConfirmationStartsTheChoiceAgain() = runBlocking {
        flow.start(PinFlowKind.Setup)
        flow.continueFromIntro()
        type("4826")
        type("4827")

        assertEquals(PinStep.EnterNew, ui!!.step)
        assertEquals(PinMessage.Mismatch, ui!!.message)
        assertFalse(manager.isPinSet.first())
    }

    @Test
    fun cannotSubmitFewerThanFourDigits() = runBlocking {
        flow.start(PinFlowKind.Setup)
        flow.continueFromIntro()
        type("482")
        assertFalse(ui!!.canSubmit)
        assertEquals(PinStep.EnterNew, ui!!.step)

        flow.addDigit('6')
        assertTrue(ui!!.canSubmit)
    }

    @Test
    fun typingStopsAtSixDigitsAndDeleteWorks() = runBlocking {
        flow.start(PinFlowKind.Setup)
        flow.continueFromIntro()
        type("12345678", submit = false)
        assertEquals(6, ui!!.digitCount)
        assertFalse(ui!!.canType)

        flow.deleteDigit()
        assertEquals(5, ui!!.digitCount)
    }

    @Test
    fun verifySucceedsAndRunsTheAction() = runBlocking {
        withPin()
        var ran = false
        flow.start(PinFlowKind.Verify) { ran = true }
        assertEquals(PinStep.EnterCurrent, ui!!.step)

        type("4826")
        assertTrue(ran)
        assertNull(ui)
    }

    @Test
    fun wrongPinShowsTriesLeftAndDoesNotRunTheAction() = runBlocking {
        withPin()
        var ran = false
        flow.start(PinFlowKind.Verify) { ran = true }
        type("0000")

        assertFalse(ran)
        assertEquals(PinMessage.WrongPin(attemptsBeforeWait = 3), ui!!.message)
        assertEquals(0, ui!!.digitCount)
    }

    @Test
    fun fifthWrongPinStartsAWaitAndBlocksTyping() = runBlocking {
        withPin()
        flow.start(PinFlowKind.Verify)
        repeat(5) { type("0000") }

        assertEquals(Duration.ofMinutes(1), ui!!.waitRemaining)
        assertFalse(ui!!.canType)
        flow.addDigit('4')
        assertEquals(0, ui!!.digitCount)

        flow.waitFinished()
        assertTrue(ui!!.canType)
    }

    @Test
    fun openingDuringAWaitShowsTheRemainingTime() = runBlocking {
        withPin()
        repeat(5) { manager.verify(pin("0000")) }
        clock.advance(Duration.ofSeconds(15))

        flow.start(PinFlowKind.Verify)
        assertEquals(Duration.ofSeconds(45), ui!!.waitRemaining)
    }

    @Test
    fun changeAsksForCurrentThenNewTwice() = runBlocking {
        withPin()
        flow.start(PinFlowKind.Change)
        assertEquals(PinStep.EnterCurrent, ui!!.step)

        type("4826")
        assertEquals(PinStep.EnterNew, ui!!.step)
        type("135790")
        type("135790")

        assertEquals(PinStep.Finished, ui!!.step)
        assertEquals(VerifyResult.Correct, manager.verify(pin("135790")))
    }

    @Test
    fun changeWithWrongCurrentPinStays() = runBlocking {
        withPin()
        flow.start(PinFlowKind.Change)
        type("1111")
        assertEquals(PinStep.EnterCurrent, ui!!.step)
        assertEquals(PinMessage.WrongPin(3), ui!!.message)
    }

    @Test
    fun cancelClosesAndForgetsTypedDigits() = runBlocking {
        flow.start(PinFlowKind.Setup)
        flow.continueFromIntro()
        type("48", submit = false)
        flow.cancel()
        assertNull(ui)

        flow.start(PinFlowKind.Setup)
        flow.continueFromIntro()
        assertEquals(0, ui!!.digitCount)
    }

    @Test
    fun cancellingAChangeMidwayKeepsTheOldPin() = runBlocking {
        withPin()
        flow.start(PinFlowKind.Change)
        type("4826")
        type("1111")
        flow.cancel()
        assertEquals(VerifyResult.Correct, manager.verify(pin("4826")))
    }
}
