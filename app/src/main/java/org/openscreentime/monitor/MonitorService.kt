package org.openscreentime.monitor

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.openscreentime.OpenScreenTimeApplication
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlin.coroutines.resume

/**
 * Keeps today's usage up to date while the phone is in use and sends the
 * 5-minute warnings. Runs as a foreground service (with a quiet notification,
 * as Android requires) and stops itself when there's nothing to watch.
 * While the screen is off it does no work at all.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            Notifications.STATUS_ID,
            Notifications.status(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun run() {
        val power = getSystemService(PowerManager::class.java)
        while (scope.isActive) {
            if (!power.isInteractive) {
                awaitScreenOn()
                continue
            }
            val keepGoing = try {
                checkNow()
            } catch (e: Exception) {
                // Only the error type is logged: never app names or usage.
                Log.w(TAG, "Check failed: ${e.javaClass.simpleName}")
                true
            }
            if (!keepGoing) {
                stopSelf()
                return
            }
            delay(CHECK_EVERY.toMillis())
        }
    }

    /** Updates usage and sends due warnings. Returns false when there's nothing left to watch. */
    private suspend fun checkNow(): Boolean {
        val container = (application as OpenScreenTimeApplication).container
        val settings = container.settingsRepository.settings.first()
        val limits = container.limitRepository.limits.first()
        val downtime = container.downtimeRepository.schedule.first()
        if (!WarningPlanner.hasSomethingToWatch(settings.trackingEnabled, limits, downtime)) return false
        if (!container.usageAccessChecker.hasUsageAccess()) return true // Step 5 handles this case.

        container.usageRepository.syncToday()
        val clock = Clock.systemDefaultZone()
        val today = LocalDate.now(clock)
        val allowedConfig = container.alwaysAllowedRepository.config.first()
        val warnings = WarningPlanner.due(
            now = clock.instant(),
            zone = clock.zone,
            usageToday = container.usageRepository.usageForDay(today).first(),
            limits = limits,
            downtime = downtime,
            isAlwaysAllowed = { container.alwaysAllowedPolicy.isAlwaysAllowed(it, allowedConfig) },
            alreadyShown = container.shownWarningsStore.shown(),
        )
        warnings.forEach { Notifications.showWarning(this, it, container.appLabels::labelFor) }
        container.shownWarningsStore.add(warnings.map { it.key }, today.toEpochDay())
        return true
    }

    private suspend fun awaitScreenOn() = suspendCancellableCoroutine { continuation ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                unregisterReceiver(this)
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON), RECEIVER_NOT_EXPORTED)
        continuation.invokeOnCancellation { runCatching { unregisterReceiver(receiver) } }
    }

    companion object {
        private const val TAG = "MonitorService"

        /** How often usage is checked while the screen is on. Warnings arrive within this time. */
        val CHECK_EVERY: Duration = Duration.ofSeconds(30)

        /**
         * Starts the service if allowed. Android limits starting it from the
         * background; it's started when the app opens, after a restart and
         * after an update, which Android allows.
         */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, MonitorService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Not allowed to start now: ${e.javaClass.simpleName}")
            }
        }
    }
}
