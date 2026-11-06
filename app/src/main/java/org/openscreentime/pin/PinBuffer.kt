package org.openscreentime.pin

/**
 * Holds the digits typed so far. Kept out of normal app state so the PIN is
 * never copied into strings, logs or saved screen state; [toString] only
 * shows the length.
 */
class PinBuffer {
    private val digits = CharArray(PinRules.MAX_LENGTH)

    var length: Int = 0
        private set

    /** Adds a digit. Ignores anything that isn't 0–9, or input past the maximum length. */
    fun append(digit: Char) {
        if (digit in '0'..'9' && length < PinRules.MAX_LENGTH) digits[length++] = digit
    }

    fun deleteLast() {
        if (length > 0) digits[--length] = '\u0000'
    }

    /** Returns the digits and empties the buffer. The caller must wipe the result. */
    fun take(): CharArray {
        val result = digits.copyOf(length)
        clear()
        return result
    }

    fun clear() {
        digits.wipe()
        length = 0
    }

    override fun toString() = "PinBuffer(length=$length)"
}
