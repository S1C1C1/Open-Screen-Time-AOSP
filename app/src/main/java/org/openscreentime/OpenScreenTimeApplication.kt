package org.openscreentime

import android.app.Application
import org.openscreentime.usage.UsageSyncWorker

class OpenScreenTimeApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        UsageSyncWorker.schedule(this)
    }
}
