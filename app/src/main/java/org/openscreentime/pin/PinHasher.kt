package org.openscreentime.pin

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A keyed fingerprint (HMAC) made with a secret key that never leaves its keeper. */
fun interface KeyedMac {
    fun mac(data: ByteArray): ByteArray
}

/** What is saved for a PIN. Never contains the PIN itself. */
class PinCredential(
    val salt: ByteArray,
    val hash: ByteArray,
    val iterations: Int,
)

/**
 * Turns a PIN into a [PinCredential] and checks PINs against one.
 *
 * Two layers, because a 4–6 digit PIN has at most a million possibilities:
 * 1. PBKDF2 with a random salt and many rounds makes each guess slow.
 * 2. The result is then sealed with [keyedMac], whose key lives in Android's
 *    hardware keystore and cannot be copied off the phone. Without it, saved
 *    data is useless for guessing on another computer.
 */
class PinHasher(
    private val keyedMac: KeyedMac,
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {

    fun create(pin: CharArray): PinCredential {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PinCredential(salt, hash(pin, salt, iterations), iterations)
    }

    fun matches(pin: CharArray, credential: PinCredential): Boolean {
        val candidate = hash(pin, credential.salt, credential.iterations)
        // Constant-time comparison, so timing doesn't reveal how close a guess was.
        return MessageDigest.isEqual(candidate, credential.hash)
    }

    private fun hash(pin: CharArray, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, rounds, KEY_BITS)
        val stretched = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        try {
            return keyedMac.mac(salt + stretched)
        } finally {
            stretched.fill(0)
        }
    }

    companion object {
        const val DEFAULT_ITERATIONS = 100_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
    }
}

/** Overwrites a PIN in memory once it's no longer needed. */
fun CharArray.wipe() = fill('\u0000')

