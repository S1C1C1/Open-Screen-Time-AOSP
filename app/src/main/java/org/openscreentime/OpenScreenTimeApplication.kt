package org.openscreentime

import android.app.Application
import android.os.UserManager
import org.openscreentime.usage.UsageSyncWorker

open class OpenScreenTimeApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Before the first unlock (the startup pause), saved data and
        // WorkManager aren't available yet; onUnlocked runs after the unlock.
        if (getSystemService(UserManager::class.java)?.isUserUnlocked != false) scheduleBackgroundWork()
    }

    /** Called after the first unlock following a restart. Scheduling is safe to repeat. */
    fun onUnlocked() {
        scheduleBackgroundWork()
    }

    /** Separate so tests can skip it: WorkManager isn't set up in on-computer tests. */
    protected open fun scheduleBackgroundWork() {
        UsageSyncWorker.schedule(this)
    }
}
