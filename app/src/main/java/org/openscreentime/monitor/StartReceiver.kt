package org.openscreentime.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openscreentime.OpenScreenTimeApplication

/**
 * Runs when the phone starts and when the app is updated.
 *
 * - Locked boot (before the first unlock): pauses apps straight away, so
 *   nothing is usable before the rules have been checked (see StartupPause).
 *   This is the only part of the app that runs before the first unlock.
 * - Boot completed (after the unlock): hands those apps to normal
 *   enforcement and starts the background service, which unpauses
 *   whatever is allowed.
 */
class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as OpenScreenTimeApplication
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> pauseAtStartup(app)
            Intent.ACTION_BOOT_COMPLETED -> afterUnlock(app)
            Intent.ACTION_MY_PACKAGE_REPLACED -> MonitorService.start(context)
        }
    }

    private fun pauseAtStartup(app: OpenScreenTimeApplication) {
        try {
            val container = app.container
            container.devicePolicyAppBlocker.pauseBeforeUnlock(container.startupPause.appsToPause())
        } catch (e: RuntimeException) {
            // Only the error type is logged: never app names.
            Log.w(TAG, "Startup pause failed: ${e.javaClass.simpleName}")
        }
    }

    private fun afterUnlock(app: OpenScreenTimeApplication) {
        app.onUnlocked()
        val handOver = app.container.startupPause.appsToHandOver()
        if (handOver.isEmpty()) {
            MonitorService.start(app)
            return
        }
        // Saving needs a moment; keep the broadcast alive until it's done.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.devicePolicyAppBlocker.handOver(handOver)
            } finally {
                // Started even if saving failed: the service still unpauses its own apps.
                MonitorService.start(app)
                pending?.finish()
            }
        }
    }

    private companion object {
        const val TAG = "StartReceiver"
    }
}
