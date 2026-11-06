package org.openscreentime.pin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class PinRulesTest {

    @Test
    fun fourToSixDigitsAreValid() {
        assertTrue(PinRules.isValid(pin("1234")))
        assertTrue(PinRules.isValid(pin("12345")))
        assertTrue(PinRules.isValid(pin("123456")))
        assertTrue(PinRules.isValid(pin("0000")))
    }

    @Test
    fun wrongLengthsAreInvalid() {
        assertFalse(PinRules.isValid(pin("")))
        assertFalse(PinRules.isValid(pin("123")))
        assertFalse(PinRules.isValid(pin("1234567")))
    }

    @Test
    fun onlyPlainDigitsAreAllowed() {
        assertFalse(PinRules.isValid(pin("12a4")))
        assertFalse(PinRules.isValid(pin("12 4")))
        assertFalse(PinRules.isValid(pin("-123")))
        // Digits from other scripts, e.g. Arabic-Indic, could be confused on other keyboards.
        assertFalse(PinRules.isValid(pin("١٢٣٤")))
    }

    @Test
    fun waitSchedule() {
        val expected = listOf(
            1 to Duration.ZERO,
            4 to Duration.ZERO,
            5 to Duration.ofMinutes(1),
            6 to Duration.ofMinutes(5),
            7 to Duration.ofMinutes(15),
            8 to Duration.ofHours(1),
            9 to Duration.ofHours(4),
            10 to Duration.ofHours(24),
            50 to Duration.ofHours(24),
        )
        expected.forEach { (attempts, wait) ->
            assertEquals("after $attempts wrong", wait, LockoutSchedule.waitAfter(attempts))
        }
    }
}
