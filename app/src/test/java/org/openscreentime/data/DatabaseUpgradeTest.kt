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
import org.openscreentime.schedules.AppSchedule
import org.openscreentime.schedules.RoomScheduleRepository
import org.openscreentime.schedules.SchedulePreset

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

    @Test
    fun upgradeFromVersion3KeepsGroupsAndAddsSchedules() = runBlocking {
        val name = "upgrade-test-3.db"
        context.deleteDatabase(name)

        // Create a database exactly as version 3 of the app did (from schemas/…/3.json).
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_usage` (`day` INTEGER NOT NULL, `package_name` TEXT NOT NULL, " +
                    "`foreground_ms` INTEGER NOT NULL, PRIMARY KEY(`day`, `package_name`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_limits` (`package_name` TEXT NOT NULL, `weekday_minutes` INTEGER NOT NULL, " +
                    "`weekend_minutes` INTEGER, PRIMARY KEY(`package_name`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`limit_weekday_minutes` INTEGER, `limit_weekend_minutes` INTEGER)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_group_members` (`group_id` INTEGER NOT NULL, `package_name` TEXT NOT NULL, " +
                    "PRIMARY KEY(`group_id`, `package_name`), FOREIGN KEY(`group_id`) REFERENCES `app_groups`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c4ae20a0b315f81fd8919eabe87196a0')")
            db.execSQL("INSERT INTO app_groups VALUES (1, 'Social', 60, NULL)")
            db.execSQL("INSERT INTO app_group_members VALUES (1, 'chat')")
            db.version = 3
        }

        val upgraded = Room.databaseBuilder(context, UsageDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            val groups = RoomGroupRepository(upgraded.appGroupDao()).groups.first()
            assertEquals(listOf("Social"), groups.map { it.name })
            assertEquals(setOf("chat"), groups.single().packages)

            val schedules = RoomScheduleRepository(upgraded.appScheduleDao())
            val bedtime = AppSchedule(
                name = "Bedtime",
                days = SchedulePreset.Bedtime.days,
                window = SchedulePreset.Bedtime.window,
                groupIds = setOf(1),
            )
            val id = schedules.save(bedtime)
            assertEquals(listOf(bedtime.copy(id = id)), schedules.schedules.first())
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}
