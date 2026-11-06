package org.openscreentime.pin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Software HMAC standing in for the hardware keystore, which tests can't reach. */
class SoftwareMac(key: ByteArray = ByteArray(32) { it.toByte() }) : KeyedMac {
    private val keySpec = SecretKeySpec(key, "HmacSHA256")
    override fun mac(data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(keySpec) }.doFinal(data)
}

/** A clock the test moves by hand, including "restarting the phone". */
class FakeDeviceClock : DeviceClock {
    var now: Duration = Duration.ofHours(5)
    var boots = 1

    override fun sinceBoot() = now
    override fun bootCount() = boots

    fun advance(by: Duration) {
        now += by
    }

    fun restartPhone() {
        boots++
        now = Duration.ofSeconds(30)
    }
}

class FakePinStore : PinStore {
    val state = MutableStateFlow(PinState())
    var writes = 0

    override val isPinSet: Flow<Boolean> = state.map { it.credential != null }
    override suspend fun read(): PinState = state.value
    override suspend fun write(state: PinState) {
        writes++
        this.state.value = state
    }
}

/** Fewer rounds than on a phone, so tests stay fast. The logic is identical. */
fun testHasher(mac: KeyedMac = SoftwareMac()) = PinHasher(mac, iterations = 1_000)

fun pin(text: String) = text.toCharArray()

/** A PIN manager that does all its work immediately, for tests. */
fun testPinManager(
    store: FakePinStore = FakePinStore(),
    clock: FakeDeviceClock = FakeDeviceClock(),
) = PinManager(store, testHasher(), clock, Dispatchers.Unconfined)
