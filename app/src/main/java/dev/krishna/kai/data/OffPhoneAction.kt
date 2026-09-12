package dev.krishna.kai.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Something to do instead, away from the phone.
 *
 * [minutes] is how long the phone stays locked once you say you're doing it --
 * long enough to actually start, short enough that committing isn't frightening.
 */
@Entity(tableName = "actions")
data class OffPhoneAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val minutes: Int,
    @ColumnInfo(name = "last_done_at") val lastDoneAt: Long = 0L,
    @ColumnInfo(name = "done_count") val doneCount: Int = 0,
)
