package org.openscreentime.pin

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class PinManagerTest {

    private val store = FakePinStore()
    private val clock = FakeDeviceClock()
    private val manager = PinManager(store, testHasher(), clock)

    private fun minutes(n: Long) = Duration.ofMinutes(n)

    private suspend fun withPin(value: String = "4826") {
        assertEquals(SetPinResult.Saved, manager.setInitialPin(pin(value)))
    }

    private suspend fun wrong(times: Int) = List(times) { manager.verify(pin("0000")) }

    /** Makes [count] wrong attempts in total, letting each wait finish before the next try. */
    private suspend fun wrongWithWaitsPassing(count: Int) {
        repeat(count) {
            manager.waitRemaining()?.let { clock.advance(it) }
            manager.verify(pin("0000"))
        }
    }

    @Test
    fun noPinAtFirst() = runBlocking {
        assertFalse(manager.isPinSet.first())
        assertEquals(VerifyResult.NoPinSet, manager.verify(pin("1234")))
    }

    @Test
    fun settingAPin() = runBlocking {
        withPin()
        assertTrue(manager.isPinSet.first())
        assertEquals(VerifyResult.Correct, manager.verify(pin("4826")))
    }

    @Test
    fun invalidPinIsNotSaved() = runBlocking {
        assertEquals(SetPinResult.InvalidFormat, manager.setInitialPin(pin("12")))
        assertEquals(SetPinResult.InvalidFormat, manager.setInitialPin(pin("12ab")))
        assertFalse(manager.isPinSet.first())
    }

    @Test
    fun existingPinCannotBeReplacedWithoutTheCurrentOne() = runBlocking {
        withPin()
        assertEquals(SetPinResult.AlreadySet, manager.setInitialPin(pin("1111")))
        assertEquals(VerifyResult.Correct, manager.verify(pin("4826")))
    }

    @Test
    fun pinArraysAreWipedAfterUse() = runBlocking {
        val newPin = pin("4826")
        manager.setInitialPin(newPin)
        assertTrue(newPin.all { it == '\u0000' })

        val attempt = pin("4826")
        manager.verify(attempt)
        assertTrue(attempt.all { it == '\u0000' })
    }

    @Test
    fun firstFourWrongAttemptsHaveNoWait() = runBlocking {
        withPin()
        val results = wrong(4)
        assertEquals(
            listOf(3, 2, 1, 0).map { VerifyResult.Wrong(attemptsBeforeWait = it, waitFor = null) },
            results,
        )
        assertNull(manager.waitRemaining())
    }

    @Test
    fun fifthWrongAttemptStartsAOneMinuteWait() = runBlocking {
        withPin()
        wrong(4)
        assertEquals(VerifyResult.Wrong(0, minutes(1)), manager.verify(pin("0000")))
        assertEquals(minutes(1), manager.waitRemaining())
    }

    @Test
    fun duringAWaitNoPinIsChecked() = runBlocking {
        withPin()
        wrong(5)
        clock.advance(Duration.ofSeconds(20))

        // Even the correct PIN is refused until the wait is over, and it doesn't count as a try.
        assertEquals(VerifyResult.Waiting(Duration.ofSeconds(40)), manager.verify(pin("4826")))
        assertEquals(5, store.state.value.failedAttempts)
    }

    @Test
    fun afterTheWaitThePinCanBeTriedAgain() = runBlocking {
        withPin()
        wrong(5)
        clock.advance(minutes(1))
        assertNull(manager.waitRemaining())
        assertEquals(VerifyResult.Correct, manager.verify(pin("4826")))
    }

    @Test
    fun waitsGrowWithEachFurtherWrongAttempt() = runBlocking {
        withPin()
        wrong(5)
        val waits = mutableListOf<Duration?>()
        repeat(6) {
            clock.advance(Duration.ofHours(25)) // let any wait finish
            waits += (manager.verify(pin("0000")) as VerifyResult.Wrong).waitFor
        }
        assertEquals(
            listOf(minutes(5), minutes(15), Duration.ofHours(1), Duration.ofHours(4), Duration.ofHours(24), Duration.ofHours(24)),
            waits,
        )
    }

    @Test
    fun correctPinResetsTheCount() = runBlocking {
        withPin()
        wrong(4)
        assertEquals(VerifyResult.Correct, manager.verify(pin("4826")))
        // Back to four free tries.
        assertEquals(VerifyResult.Wrong(3, null), manager.verify(pin("0000")))
    }

    @Test
    fun countIsKeptWhenTheAppRestarts() = runBlocking {
        withPin()
        wrong(4)
        val reopened = PinManager(store, testHasher(), clock)
        assertEquals(VerifyResult.Wrong(0, minutes(1)), reopened.verify(pin("0000")))
    }

    @Test
    fun changingTheDateOrTimeDoesNotShortenAWait() = runBlocking {
        withPin()
        wrongWithWaitsPassing(8) // now a one-hour wait
        // Setting the phone's date forward has no effect on time since start,
        // so only real time passing counts.
        clock.advance(minutes(10))
        assertEquals(minutes(50), manager.waitRemaining())
    }

    @Test
    fun restartingThePhoneStartsTheWaitAgain() = runBlocking {
        withPin()
        wrongWithWaitsPassing(8) // now a one-hour wait
        clock.advance(minutes(50))
        clock.restartPhone()

        assertEquals(Duration.ofHours(1), manager.waitRemaining())
        clock.advance(minutes(59))
        assertEquals(minutes(1), manager.waitRemaining())
        clock.advance(minutes(1))
        assertNull(manager.waitRemaining())
    }

    @Test
    fun restartingDuringAWaitIsSavedSoReopeningDoesNotRestartItAgain() = runBlocking {
        withPin()
        wrong(5)
        clock.restartPhone()
        manager.waitRemaining()
        clock.advance(Duration.ofSeconds(30))

        val reopened = PinManager(store, testHasher(), clock)
        assertEquals(Duration.ofSeconds(30), reopened.waitRemaining())
    }

    @Test
    fun changingThePinNeedsTheCurrentOne() = runBlocking {
        withPin()
        assertEquals(ChangePinResult.Changed, manager.changePin(pin("4826"), pin("135790")))
        assertEquals(VerifyResult.Correct, manager.verify(pin("135790")))
        assertEquals(VerifyResult.Wrong(3, null), manager.verify(pin("4826")))
    }

    @Test
    fun wrongCurrentPinCountsAsAWrongAttempt() = runBlocking {
        withPin()
        val result = manager.changePin(pin("1111"), pin("2222"))
        assertEquals(ChangePinResult.NotVerified(VerifyResult.Wrong(3, null)), result)
        assertEquals(1, store.state.value.failedAttempts)
        assertEquals(VerifyResult.Correct, manager.verify(pin("4826")))
    }

    @Test
    fun invalidNewPinIsRejectedWithoutUsingAnAttempt() = runBlocking {
        withPin()
        assertEquals(ChangePinResult.InvalidNewPin, manager.changePin(pin("1111"), pin("12")))
        assertEquals(0, store.state.value.failedAttempts)
    }

    @Test
    fun changingThePinIsBlockedDuringAWait() = runBlocking {
        withPin()
        wrong(5)
        assertEquals(
            ChangePinResult.NotVerified(VerifyResult.Waiting(minutes(1))),
            manager.changePin(pin("4826"), pin("2222")),
        )
        assertEquals(VerifyResult.Waiting(minutes(1)), manager.verify(pin("2222")))
    }

    @Test
    fun wrongAttemptIsSavedBeforeTheAnswerIsGiven() = runBlocking {
        withPin()
        val writesBefore = store.writes
        manager.verify(pin("0000"))
        assertEquals(writesBefore + 1, store.writes)
        assertEquals(1, store.state.value.failedAttempts)
    }
}
