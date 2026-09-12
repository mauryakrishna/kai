package dev.krishna.kai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TruthDao {

    @Query("SELECT * FROM truths ORDER BY id")
    suspend fun all(): List<Truth>

    /** The one you've seen least recently. Ties broken by id so it's stable. */
    @Query("SELECT * FROM truths ORDER BY last_shown_at ASC, id ASC LIMIT 1")
    suspend fun leastRecentlyShown(): Truth?

    @Query("UPDATE truths SET last_shown_at = :at, shown_count = shown_count + 1 WHERE id = :id")
    suspend fun markShown(id: Long, at: Long)

    @Insert
    suspend fun insert(truth: Truth)

    @Delete
    suspend fun delete(truth: Truth)

    @Query("SELECT COUNT(*) FROM truths")
    suspend fun count(): Int
}
