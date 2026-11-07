package org.openscreentime.data

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Time one app was used on one day. */
@Entity(tableName = "daily_usage", primaryKeys = ["day", "package_name"])
data class DailyUsageEntity(
    /** Local date as days since 1970-01-01 (LocalDate.toEpochDay). */
    @ColumnInfo(name = "day") val epochDay: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "foreground_ms") val foregroundMillis: Long,
)

@Dao
abstract class DailyUsageDao {

    @Query("SELECT * FROM daily_usage WHERE day = :epochDay ORDER BY foreground_ms DESC, package_name")
    abstract fun forDay(epochDay: Long): Flow<List<DailyUsageEntity>>

    /** Every app's time on each day from [fromEpochDay] to [toEpochDay], inclusive. */
    @Query("SELECT * FROM daily_usage WHERE day BETWEEN :fromEpochDay AND :toEpochDay")
    abstract fun forDays(fromEpochDay: Long, toEpochDay: Long): Flow<List<DailyUsageEntity>>

    @Query("SELECT foreground_ms FROM daily_usage WHERE day = :epochDay AND package_name = :packageName")
    protected abstract suspend fun storedMillis(epochDay: Long, packageName: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertOrReplace(rows: List<DailyUsageEntity>)

    /**
     * Saves rows, but never lowers a stored value. Android only keeps detailed
     * usage events for a few days, so recalculating an older day can come out
     * lower than what was saved when the events still existed.
     */
    @Transaction
    open suspend fun saveKeepingLarger(rows: List<DailyUsageEntity>) {
        val merged = rows.map { row ->
            val stored = storedMillis(row.epochDay, row.packageName) ?: 0L
            if (stored > row.foregroundMillis) row.copy(foregroundMillis = stored) else row
        }
        insertOrReplace(merged)
    }

    /** Deletes every day before [epochDay]. Returns how many rows were deleted. */
    @Query("DELETE FROM daily_usage WHERE day < :epochDay")
    abstract suspend fun deleteBefore(epochDay: Long): Int
}

/** A daily limit for one app, in minutes. */
@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "weekday_minutes") val weekdayMinutes: Int,
    /** Null when the weekday limit applies every day. */
    @ColumnInfo(name = "weekend_minutes") val weekendMinutes: Int?,
)

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits ORDER BY package_name")
    fun all(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits WHERE package_name = :packageName")
    fun forApp(packageName: String): Flow<AppLimitEntity?>

    @Upsert
    suspend fun upsert(limit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE package_name = :packageName")
    suspend fun delete(packageName: String)
}

/** A group of apps. Limit minutes are null when the group has no limit. */
@Entity(tableName = "app_groups")
data class AppGroupEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "limit_weekday_minutes") val limitWeekdayMinutes: Int?,
    @ColumnInfo(name = "limit_weekend_minutes") val limitWeekendMinutes: Int?,
)

/** One app in one group. Deleting a group deletes its members. */
@Entity(
    tableName = "app_group_members",
    primaryKeys = ["group_id", "package_name"],
    foreignKeys = [
        ForeignKey(
            entity = AppGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AppGroupMemberEntity(
    @ColumnInfo(name = "group_id") val groupId: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
)

data class GroupWithMembers(
    @Embedded val group: AppGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "group_id")
    val members: List<AppGroupMemberEntity>,
)

@Dao
abstract class AppGroupDao {
    @Transaction
    @Query("SELECT * FROM app_groups ORDER BY name COLLATE NOCASE")
    abstract fun all(): Flow<List<GroupWithMembers>>

    @Insert
    protected abstract suspend fun insert(group: AppGroupEntity): Long

    @Update
    protected abstract suspend fun update(group: AppGroupEntity)

    @Query("DELETE FROM app_group_members WHERE group_id = :groupId")
    protected abstract suspend fun clearMembers(groupId: Long)

    @Insert
    protected abstract suspend fun insertMembers(members: List<AppGroupMemberEntity>)

    /** Adds or replaces a group and its apps in one step. Returns the group's id. */
    @Transaction
    open suspend fun save(group: AppGroupEntity, packages: Set<String>): Long {
        val id = if (group.id == 0L) insert(group) else group.id.also { update(group) }
        clearMembers(id)
        insertMembers(packages.map { AppGroupMemberEntity(id, it) })
        return id
    }

    @Query("DELETE FROM app_groups WHERE id = :groupId")
    abstract suspend fun delete(groupId: Long)
}

/**
 * The app's database. When its layout changes, raise [version] and add a
 * migration, so saved usage history is kept when the app is updated.
 * Version 2 added app limits; version 3 added app groups.
 */
@Database(
    entities = [DailyUsageEntity::class, AppLimitEntity::class, AppGroupEntity::class, AppGroupMemberEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun appGroupDao(): AppGroupDao
}
