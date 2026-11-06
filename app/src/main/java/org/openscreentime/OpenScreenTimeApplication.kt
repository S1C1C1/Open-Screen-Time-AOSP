package org.openscreentime

import android.app.Application
import org.openscreentime.usage.UsageSyncWorker

open class OpenScreenTimeApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleBackgroundWork()
    }

    /** Separate so tests can skip it: WorkManager isn't set up in on-computer tests. */
    protected open fun scheduleBackgroundWork() {
        UsageSyncWorker.schedule(this)
    }
}
