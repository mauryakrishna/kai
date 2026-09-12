package dev.krishna.kai.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One thing you told yourself, in your own words.
 *
 * lastShownAt drives rotation: the gate always shows the one you have seen
 * least recently, so a statement doesn't wear out through repetition.
 */
@Entity(tableName = "truths")
data class Truth(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    @ColumnInfo(name = "last_shown_at") val lastShownAt: Long = 0L,
    @ColumnInfo(name = "shown_count") val shownCount: Int = 0,
)
