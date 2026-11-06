package org.openscreentime.allowed

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AlwaysAllowedStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun savesAndReadsChoices() = runBlocking {
        val repository = DataStoreAlwaysAllowedRepository(
            PreferenceDataStoreFactory.create(scope = scope) { tempFolder.root.resolve("allowed.preferences_pb") },
        )
        assertEquals(AlwaysAllowedConfig(), repository.config.first())

        val config = AlwaysAllowedConfig(includePhone = false, includeSettings = true, addedPackages = setOf("a", "b"))
        repository.save(config)
        assertEquals(config, repository.config.first())
    }

    private fun addActivity(pkg: String, action: String, system: Boolean = true) {
        val appInfo = ApplicationInfo().apply {
            packageName = pkg
            if (system) flags = ApplicationInfo.FLAG_SYSTEM
        }
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = pkg
                applicationInfo = appInfo
            },
        )
        val component = ComponentName(pkg, "$pkg.Main")
        shadowOf(context.packageManager).addActivityIfNotPresent(component)
        shadowOf(context.packageManager).addIntentFilterForActivity(
            component,
            IntentFilter(action).apply { addCategory(Intent.CATEGORY_DEFAULT) },
        )
    }

    @Test
    fun findsTheSystemRoleApps() {
        shadowOf(context.getSystemService(TelecomManager::class.java)).setDefaultDialerPackage("com.example.dialer")
        addActivity("com.android.phone", "com.android.phone.EmergencyDialer.DIAL")
        addActivity("com.android.settings", Settings.ACTION_SETTINGS)

        val apps = AndroidSystemApps(context)
        assertEquals("com.example.dialer", apps.defaultPhoneApp())
        assertEquals("com.android.phone", apps.emergencyDialer())
        assertEquals("com.android.settings", apps.settingsApp())
    }

    @Test
    fun installedAppsCannotPoseAsSettings() {
        // An app installed by the user that claims to open Settings isn't trusted.
        addActivity("com.example.fake", Settings.ACTION_SETTINGS, system = false)
        assertEquals(null, AndroidSystemApps(context).settingsApp())
    }
}
