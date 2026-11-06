package org.openscreentime.pin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PinBufferTest {

    @Test
    fun collectsDigitsUpToSix() {
        val buffer = PinBuffer()
        "12345678".forEach(buffer::append)
        assertEquals(6, buffer.length)
        assertEquals("123456", String(buffer.take()))
    }

    @Test
    fun ignoresAnythingButDigits() {
        val buffer = PinBuffer()
        "1a 2-٣3".forEach(buffer::append)
        assertEquals("123", String(buffer.take()))
    }

    @Test
    fun deleteRemovesTheLastDigit() {
        val buffer = PinBuffer()
        "123".forEach(buffer::append)
        buffer.deleteLast()
        assertEquals("12", String(buffer.take()))
        buffer.deleteLast() // nothing to delete: no error
        assertEquals(0, buffer.length)
    }

    @Test
    fun takeEmptiesTheBuffer() {
        val buffer = PinBuffer()
        "4826".forEach(buffer::append)
        buffer.take()
        assertEquals(0, buffer.length)
        assertEquals("", String(buffer.take()))
    }

    @Test
    fun textDescriptionNeverShowsTheDigits() {
        val buffer = PinBuffer()
        "4826".forEach(buffer::append)
        assertEquals("PinBuffer(length=4)", buffer.toString())
        assertFalse(buffer.toString().contains("4826"))
    }
}
