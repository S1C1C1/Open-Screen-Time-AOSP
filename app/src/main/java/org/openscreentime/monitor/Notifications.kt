package org.openscreentime.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.openscreentime.MainActivity
import org.openscreentime.R
import org.openscreentime.ui.format.formatTime

/** The app's notifications: the quiet "Screen time is on" one and the 5-minute warnings. */
object Notifications {
    const val STATUS_CHANNEL = "status"
    const val WARNINGS_CHANNEL = "warnings"
    const val STATUS_ID = 1

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                // Lowest importance: no sound, no status bar icon, collapsed at the bottom.
                NotificationChannel(
                    STATUS_CHANNEL,
                    context.getString(R.string.notify_channel_status),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply { description = context.getString(R.string.notify_channel_status_description) },
                NotificationChannel(
                    WARNINGS_CHANNEL,
                    context.getString(R.string.notify_channel_warnings),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notify_channel_warnings_description) },
            ),
        )
    }

    /** Shown while the background service runs, as Android requires. */
    fun status(context: Context): Notification =
        NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_hourglass)
            .setContentTitle(context.getString(R.string.notify_status_title))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    /** Shows a warning, if the user allowed notifications. */
    fun showWarning(context: Context, warning: Warning, appLabel: (String) -> String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val text = when (warning) {
            is Warning.LimitSoon -> {
                // Round up, so 4 min 10 s reads "5 min".
                val minutes = (warning.left.seconds + 59) / 60
                context.resources.getQuantityString(
                    R.plurals.notify_limit_soon,
                    minutes.toInt(),
                    minutes,
                    appLabel(warning.packageName),
                )
            }
            is Warning.GroupLimitSoon -> {
                val minutes = (warning.left.seconds + 59) / 60
                context.resources.getQuantityString(
                    R.plurals.notify_limit_soon,
                    minutes.toInt(),
                    minutes,
                    warning.groupName,
                )
            }
            is Warning.ScheduleSoon -> context.getString(
                R.string.notify_schedule_soon,
                warning.name,
                formatTime(
                    warning.start.toLocalTime(),
                    context.resources.configuration.locales[0],
                    DateFormat.is24HourFormat(context),
                ),
            )
            is Warning.DowntimeSoon -> context.getString(
                R.string.notify_downtime_soon,
                formatTime(
                    warning.start.toLocalTime(),
                    context.resources.configuration.locales[0],
                    DateFormat.is24HourFormat(context),
                ),
            )
        }
        val notification = NotificationCompat.Builder(context, WARNINGS_CHANNEL)
            .setSmallIcon(R.drawable.ic_hourglass)
            .setContentTitle(text)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(warning.key.hashCode(), notification)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
