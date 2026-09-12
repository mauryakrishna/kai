package dev.krishna.kai.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per time an app came to the foreground.
 *
 * Duration isn't stored -- it's the gap to the next event, which keeps writes
 * cheap and means a crash can never leave a half-open row.
 */
@Entity(tableName = "app_events")
data class AppEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** Local date, "2026-09-13". Stored so a day can be queried without timezone maths. */
    @ColumnInfo(name = "day") val day: String,
)
