package org.openscreentime.monitor

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

// The start receiver uses the app's shared objects, so the real app setup is needed.
@RunWith(AndroidJUnit4::class)
@Config(application = org.openscreentime.TestScreenTimeApplication::class)
class MonitorPartsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun statusNotificationIsQuietAndOngoing() {
        Notifications.ensureChannels(context)
        val channel = notifications.getNotificationChannel(Notifications.STATUS_CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_MIN, channel.importance)

        val notification = Notifications.status(context)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Screen time is on", notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun warningsNeedNotificationPermission() {
        Notifications.ensureChannels(context)
        val warning = Warning.LimitSoon("video", Duration.ofSeconds(250), 1)

        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.showWarning(context, warning) { "Video" }
        assertEquals(0, shadowOf(notifications).allNotifications.size)

        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.showWarning(context, warning) { "Video" }
        val shown = shadowOf(notifications).allNotifications.single()
        // 4 min 10 s left reads as 5 min.
        assertEquals("5 min left on Video today", shown.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun restartAndUpdateStartTheService() {
        listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED).forEach { action ->
            StartReceiver().onReceive(context, Intent(action))
            assertEquals(MonitorService::class.java.name, shadowOf(context).nextStartedService.component?.className)
        }
    }

    @Test
    fun lockedBootDoesNotStartTheServiceAndPausesNothingWithoutDeviceOwner() {
        StartReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun startReceiverRunsBeforeTheFirstUnlock() {
        val info = context.packageManager.getReceiverInfo(
            ComponentName(context, StartReceiver::class.java),
            PackageManager.ComponentInfoFlags.of(0),
        )
        // Needed to receive LOCKED_BOOT_COMPLETED; nothing else in the app runs before the unlock.
        assertTrue(info.directBootAware)
    }

    @Test
    fun otherBroadcastsDoNothing() {
        StartReceiver().onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun serviceIsPrivateAndDeclaredForSpecialUse() {
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, MonitorService::class.java),
            PackageManager.ComponentInfoFlags.of(0),
        )
        assertFalse(info.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, info.foregroundServiceType)
    }

    @Test
    fun shownWarningsAreRememberedAndOldOnesForgotten() = runBlocking {
        val store = DataStoreShownWarningsStore(
            PreferenceDataStoreFactory.create(scope = scope) { tempFolder.root.resolve("shown.preferences_pb") },
        )
        val today = 20_397L
        store.add(listOf("limit:video:${today - 3}", "limit:maps:${today - 1}"), today - 1)
        store.add(listOf("limit:video:$today", "downtime:${today * 86_400 + 3600}"), today)
        assertEquals(
            setOf("limit:maps:${today - 1}", "limit:video:$today", "downtime:${today * 86_400 + 3600}"),
            store.shown(),
        )
    }

    @Test
    fun keyDays() {
        assertEquals(20_397L, dayOf("limit:com.example.app:20397"))
        assertEquals(20_397L, dayOf("downtime:${20_397L * 86_400 + 75_600}"))
        assertNull(dayOf("something-else"))
    }
}
