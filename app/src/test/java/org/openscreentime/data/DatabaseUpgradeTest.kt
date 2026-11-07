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
import org.openscreentime.groups.AppGroup
import org.openscreentime.groups.RoomGroupRepository

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

    @Test
    fun upgradeFromVersion2KeepsLimitsAndAddsGroups() = runBlocking {
        val name = "upgrade-test-2.db"
        context.deleteDatabase(name)

        // Create a database exactly as version 2 of the app did (from schemas/…/2.json).
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_usage` (`day` INTEGER NOT NULL, `package_name` TEXT NOT NULL, " +
                    "`foreground_ms` INTEGER NOT NULL, PRIMARY KEY(`day`, `package_name`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_limits` (`package_name` TEXT NOT NULL, `weekday_minutes` INTEGER NOT NULL, " +
                    "`weekend_minutes` INTEGER, PRIMARY KEY(`package_name`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '31a0b62876233c9f9f3a20253655be2d')")
            db.execSQL("INSERT INTO daily_usage VALUES (20397, 'video', 3600000)")
            db.execSQL("INSERT INTO app_limits VALUES ('video', 60, NULL)")
            db.version = 2
        }

        val upgraded = Room.databaseBuilder(context, UsageDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(1, upgraded.dailyUsageDao().forDay(20397).first().size)
            assertEquals(listOf(AppLimitEntity("video", 60, null)), upgraded.appLimitDao().all().first())

            val groups = RoomGroupRepository(upgraded.appGroupDao())
            val id = groups.save(AppGroup(name = "Video", packages = setOf("video")))
            assertEquals(listOf(AppGroup(id, "Video", setOf("video"))), groups.groups.first())
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}
