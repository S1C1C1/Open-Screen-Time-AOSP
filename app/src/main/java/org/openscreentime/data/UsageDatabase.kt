package org.openscreentime.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
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

@Database(entities = [DailyUsageEntity::class], version = 1, exportSchema = true)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun dailyUsageDao(): DailyUsageDao
}
