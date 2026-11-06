package org.openscreentime.pin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    private val hasher = testHasher()

    @Test
    fun correctPinMatchesAndWrongPinDoesNot() {
        val credential = hasher.create(pin("4826"))
        assertTrue(hasher.matches(pin("4826"), credential))
        assertFalse(hasher.matches(pin("4827"), credential))
        assertFalse(hasher.matches(pin("48260"), credential))
    }

    @Test
    fun samePinGetsDifferentSaltAndHashEachTime() {
        val first = hasher.create(pin("4826"))
        val second = hasher.create(pin("4826"))
        assertFalse(first.salt.contentEquals(second.salt))
        assertFalse(first.hash.contentEquals(second.hash))
    }

    @Test
    fun savedDataNeverContainsThePin() {
        val credential = hasher.create(pin("482619"))
        val pinBytes = "482619".toByteArray()
        listOf(credential.salt, credential.hash).forEach { bytes ->
            assertFalse(bytes.toList().windowed(pinBytes.size).any { it == pinBytes.toList() })
        }
    }

    @Test
    fun aDifferentDeviceKeyCannotCheckThePin() {
        // Someone who copied the saved data to another phone or computer
        // doesn't have this phone's keystore key.
        val credential = hasher.create(pin("4826"))
        val otherDevice = testHasher(SoftwareMac(ByteArray(32) { 7 }))
        assertFalse(otherDevice.matches(pin("4826"), credential))
    }

    @Test
    fun roundsAreSavedSoTheyCanChangeLater() {
        val credential = hasher.create(pin("4826"))
        assertEquals(1_000, credential.iterations)
        // A hasher with a different default still checks old PINs with their saved rounds.
        assertTrue(PinHasher(SoftwareMac(), iterations = 2_000).matches(pin("4826"), credential))
    }

    @Test
    fun hashIsTheExpectedSize() {
        val credential = hasher.create(pin("4826"))
        assertEquals(16, credential.salt.size)
        assertEquals(32, credential.hash.size)
        assertNotEquals(0, credential.hash.sum())
    }
}
