package org.openscreentime.pin

import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * HMAC with a key kept in Android's hardware keystore. The key is created on
 * first use and can't be exported, so PIN fingerprints can only be checked
 * on this phone. Uses StrongBox (a separate security chip, e.g. Titan M on
 * Pixels) when the phone has one.
 *
 * Clearing the app's data deletes this key along with the saved PIN, so the
 * two never get out of step.
 */
class KeystoreMac(
    private val packageManager: PackageManager,
    private val alias: String = "screen_time_pin_key",
) : KeyedMac {

    override fun mac(data: ByteArray): ByteArray =
        Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
            .apply { init(key()) }
            .doFinal(data)

    @Synchronized
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val hasStrongBox = packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        return if (hasStrongBox) {
            try {
                generate(strongBox = true)
            } catch (_: StrongBoxUnavailableException) {
                generate(strongBox = false)
            }
        } else {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
