package org.openscreentime.blocking

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.openscreentime.admin.EnforcementMode
import org.openscreentime.admin.ScreenTimeAdmin
import org.openscreentime.ui.FakeDeviceOwner
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DevicePolicyAppBlockerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val policy = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ScreenTimeAdmin.component(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var blocker: DevicePolicyAppBlocker

    @Before
    fun setUp() {
        shadowOf(policy).setDeviceOwner(admin)
        listOf("com.example.video", "com.example.maps").forEach { shadowOf(context.packageManager).installPackage(
            android.content.pm.PackageInfo().apply {
                packageName = it
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = it }
            },
        ) }
        blocker = DevicePolicyAppBlocker(
            context,
            PreferenceDataStoreFactory.create(scope = scope) { tempFolder.root.resolve("paused.preferences_pb") },
        )
    }

    @After
    fun tearDown() = scope.cancel()

    private fun paused(pkg: String) = policy.isPackageSuspended(admin, pkg)

    @Test
    fun pausesAndUnpausesOnlyWhatChanged() = runBlocking {
        blocker.setBlockedApps(setOf("com.example.video", "com.example.maps"))
        assertTrue(paused("com.example.video"))
        assertTrue(paused("com.example.maps"))

        blocker.setBlockedApps(setOf("com.example.maps"))
        assertFalse(paused("com.example.video"))
        assertTrue(paused("com.example.maps"))
        assertEquals(setOf("com.example.maps"), blocker.pausedApps())

        blocker.setBlockedApps(emptySet())
        assertFalse(paused("com.example.maps"))
    }

    @Test
    fun pausedAppsPointToScreenTime() = runBlocking {
        blocker.setBlockedApps(setOf("com.example.video"))
        assertEquals(
            "Paused by Screen time. Open Screen time to get more time.",
            policy.getShortSupportMessage(admin).toString(),
        )
    }

    @Test
    fun previewModeNeverPauses() = runBlocking {
        val owner = FakeDeviceOwner(EnforcementMode.PreviewOnly)
        ModeAwareAppBlocker(owner, blocker).setBlockedApps(setOf("com.example.video"))
        assertFalse(paused("com.example.video"))

        owner.current = EnforcementMode.DeviceOwner
        ModeAwareAppBlocker(owner, blocker).setBlockedApps(setOf("com.example.video"))
        assertTrue(paused("com.example.video"))
    }

    @Test
    fun appsPausedAtStartupAreUnpausedOnceRulesAreChecked() = runBlocking {
        // Before the unlock: paused, but nothing can be saved yet.
        blocker.pauseBeforeUnlock(setOf("com.example.video", "com.example.maps"))
        assertTrue(paused("com.example.video"))
        assertEquals(emptySet<String>(), blocker.pausedApps())

        // After the unlock: handed over, then the first check keeps only what the rules block.
        blocker.handOver(setOf("com.example.video", "com.example.maps"))
        blocker.setBlockedApps(setOf("com.example.maps"))
        assertFalse(paused("com.example.video"))
        assertTrue(paused("com.example.maps"))
        assertEquals(setOf("com.example.maps"), blocker.pausedApps())
    }
}
