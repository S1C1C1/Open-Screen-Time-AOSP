package org.openscreentime.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that updating the app from a version with only usage history
 * (database version 1) keeps that history and adds the limits table.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseUpgradeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun upgradeFromVersion1KeepsUsageHistory() = runBlocking {
        val name = "upgrade-test.db"
        context.deleteDatabase(name)

        // Create a database exactly as version 1 of the app did (from schemas/…/1.json).
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_usage` (`day` INTEGER NOT NULL, `package_name` TEXT NOT NULL, " +
                    "`foreground_ms` INTEGER NOT NULL, PRIMARY KEY(`day`, `package_name`))",
            )
            db.execSQL("INSERT INTO daily_usage VALUES (20397, 'video', 3600000)")
            db.version = 1
        }

        val upgraded = Room.databaseBuilder(context, UsageDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            val kept = upgraded.dailyUsageDao().forDay(20397).first()
            assertEquals(listOf(DailyUsageEntity(20397, "video", 3_600_000)), kept)

            upgraded.appLimitDao().upsert(AppLimitEntity("video", 60, null))
            assertEquals(1, upgraded.appLimitDao().all().first().size)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}
