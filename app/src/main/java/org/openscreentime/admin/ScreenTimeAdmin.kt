package org.openscreentime.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * The component Android needs for an app to become the phone's device
 * owner. Device-owner powers (pausing apps, restrictions, protecting this
 * app) are used through [DeviceOwner]; this class only has to exist.
 */
class ScreenTimeAdmin : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context) = ComponentName(context, ScreenTimeAdmin::class.java)
    }
}
