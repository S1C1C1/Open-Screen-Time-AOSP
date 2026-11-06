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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.openscreentime.AppContainer
import org.openscreentime.OpenScreenTimeApplication
import org.openscreentime.admin.EnforcementMode
import org.openscreentime.blocking.BlockActivity
import org.openscreentime.blocking.BlockRequest
import org.openscreentime.blocking.EnforcementPlanner
import org.openscreentime.restrictions.RestrictionRules
import org.openscreentime.usage.foregroundApp
import java.time.Instant
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

    /** Lets a "check now" request cut a wait short, for example right after extra time is given. */
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    /** The last block screen shown, so the same one isn't opened again every few seconds. */
    private var lastShown: Pair<BlockRequest, Instant>? = null

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
        if (intent?.action == ACTION_CHECK_NOW) wakeUp.trySend(Unit)
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
            val wait = try {
                checkNow()
            } catch (e: Exception) {
                // Only the error type is logged: never app names or usage.
                Log.w(TAG, "Check failed: ${e.javaClass.simpleName}")
                CHECK_EVERY
            }
            if (wait == null) {
                stopSelf()
                return
            }
            withTimeoutOrNull(wait.toMillis()) { wakeUp.receive() }
        }
    }

    /**
     * Updates usage, sends due warnings and, in device-owner mode, pauses
     * and unpauses apps. Returns how long to wait before the next check, or
     * null when there's nothing left to watch.
     */
    private suspend fun checkNow(): Duration? {
        val container = (application as OpenScreenTimeApplication).container
        val settings = container.settingsRepository.settings.first()
        val limits = container.limitRepository.limits.first()
        val downtime = container.downtimeRepository.schedule.first()

        // Restrictions: switch them back on as soon as a 15-minute lift ends.
        val restrictions = container.restrictionsRepository.state.first()
        container.restrictionApplier.apply(RestrictionRules.active(restrictions, container.deviceClock))
        val liftEnds = RestrictionRules.nextLiftEnd(restrictions, container.deviceClock)?.plusSeconds(1)

        if (!WarningPlanner.hasSomethingToWatch(settings.trackingEnabled, limits, downtime)) {
            // Nothing to enforce: make sure nothing stays paused.
            container.appBlocker.setBlockedApps(emptySet())
            // Keep running only to end a restriction lift on time.
            return liftEnds?.coerceAtMost(CHECK_EVERY)
        }

        val clock = Clock.systemDefaultZone()
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val hasAccess = container.usageAccessChecker.hasUsageAccess()
        val allowedConfig = container.alwaysAllowedRepository.config.first()
        val isAllowed = { pkg: String -> container.alwaysAllowedPolicy.isAlwaysAllowed(pkg, allowedConfig) }

        val usageToday = if (hasAccess) {
            container.usageRepository.syncToday()
            container.usageRepository.usageForDay(today).first()
        } else {
            emptyList()
        }

        if (hasAccess) {
            val warnings = WarningPlanner.due(
                now = now,
                zone = clock.zone,
                usageToday = usageToday,
                limits = limits,
                downtime = downtime,
                isAlwaysAllowed = isAllowed,
                alreadyShown = container.shownWarningsStore.shown(),
            )
            warnings.forEach { Notifications.showWarning(this, it, container.appLabels::labelFor) }
            container.shownWarningsStore.add(warnings.map { it.key }, today.toEpochDay())
        }

        if (container.deviceOwner.mode() != EnforcementMode.DeviceOwner) return CHECK_EVERY
        val wait = enforce(container, now, clock, usageToday.associate { it.packageName to it.time }, limits, downtime, hasAccess, isAllowed)
        return if (liftEnds != null) minOf(wait, liftEnds) else wait
    }

    @Suppress("LongParameterList")
    private suspend fun enforce(
        container: AppContainer,
        now: Instant,
        clock: Clock,
        usageToday: Map<String, Duration>,
        limits: List<org.openscreentime.limits.AppLimit>,
        downtime: org.openscreentime.downtime.DowntimeSchedule,
        hasAccess: Boolean,
        isAllowed: (String) -> Boolean,
    ): Duration {
        val candidates = container.installedApps.launchableApps().map { it.packageName }.toSet() +
            limits.map { it.packageName }
        val overrides = candidates.associateWith { container.overrideRepository.overrideFor(it).first() }
        val foreground = if (hasAccess) {
            foregroundApp(container.usageEventSource.events(now - RECENT_EVENTS, now))
        } else {
            null
        }
        val plan = EnforcementPlanner.plan(
            now = now,
            zone = clock.zone,
            candidates = candidates,
            usageToday = usageToday,
            limits = limits.associateBy { it.packageName },
            downtime = downtime,
            isAlwaysAllowed = isAllowed,
            overrides = overrides,
            mode = container.settingsRepository.settings.first().limitMode,
            hasUsageAccess = hasAccess,
            foregroundApp = foreground,
        )
        container.appBlocker.setBlockedApps(plan.pause)
        plan.showBlockScreen?.let { request -> showBlockScreen(request, now) }
        return plan.checkAgainIn
    }

    /** Opens the block screen over the app, unless it was just shown for the same app. */
    private fun showBlockScreen(request: BlockRequest, now: Instant) {
        val previous = lastShown
        if (previous != null && previous.first == request && Duration.between(previous.second, now) < SHOW_AGAIN_AFTER) return
        lastShown = request to now
        // Allowed from the background because this app is the device owner.
        startActivity(BlockActivity.intent(this, request.packageName, request.reason))
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

        /** How far back to look for the app that's open now. */
        private val RECENT_EVENTS: Duration = Duration.ofHours(6)

        /** Don't reopen the same block screen within this time. */
        private val SHOW_AGAIN_AFTER: Duration = Duration.ofSeconds(10)

        private const val ACTION_CHECK_NOW = "org.openscreentime.CHECK_NOW"

        /** Asks the running service to check straight away, for example after extra time was given. */
        fun checkNow(context: Context) {
            try {
                context.startForegroundService(Intent(context, MonitorService::class.java).setAction(ACTION_CHECK_NOW))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Not allowed to start now: ${e.javaClass.simpleName}")
            }
        }

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
