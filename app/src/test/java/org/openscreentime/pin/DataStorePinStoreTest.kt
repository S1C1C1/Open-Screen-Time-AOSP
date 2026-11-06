package org.openscreentime.pin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

// Robolectric, because saving uses Android's Base64.
@RunWith(AndroidJUnit4::class)
class DataStorePinStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file by lazy { tempFolder.root.resolve("pin.preferences_pb") }
    private val dataStore by lazy { PreferenceDataStoreFactory.create(scope = scope) { file } }
    private val store by lazy { DataStorePinStore(dataStore) }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun emptyAtFirst() = runBlocking {
        assertFalse(store.isPinSet.first())
        val state = store.read()
        assertNull(state.credential)
        assertEquals(0, state.failedAttempts)
        assertNull(state.lockout)
    }

    @Test
    fun savesAndReadsEverything() = runBlocking {
        val credential = testHasher().create(pin("4826"))
        val lockout = Lockout(bootCount = 3, endsAtSinceBootMillis = 90_000, durationMillis = 60_000)
        store.write(PinState(credential, failedAttempts = 5, lockout = lockout))

        assertTrue(store.isPinSet.first())
        val read = store.read()
        assertArrayEquals(credential.salt, read.credential!!.salt)
        assertArrayEquals(credential.hash, read.credential.hash)
        assertEquals(credential.iterations, read.credential.iterations)
        assertEquals(5, read.failedAttempts)
        assertEquals(lockout, read.lockout)
    }

    @Test
    fun clearingTheWaitRemovesIt() = runBlocking {
        val credential = testHasher().create(pin("4826"))
        store.write(PinState(credential, 5, Lockout(1, 2, 3)))
        store.write(PinState(credential))
        assertNull(store.read().lockout)
        assertEquals(0, store.read().failedAttempts)
    }

    @Test
    fun theSavedFileNeverContainsThePin() = runBlocking {
        val manager = PinManager(store, testHasher(), FakeDeviceClock())
        manager.setInitialPin(pin("482619"))

        val saved = String(file.readBytes(), Charsets.ISO_8859_1)
        assertTrue(saved.isNotEmpty())
        assertFalse(saved.contains("482619"))
    }
}
