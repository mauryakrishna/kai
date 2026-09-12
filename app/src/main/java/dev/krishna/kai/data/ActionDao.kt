package dev.krishna.kai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ActionDao {

    @Query("SELECT * FROM actions ORDER BY id")
    suspend fun all(): List<OffPhoneAction>

    /** Offer the one you've done least recently, so it isn't always the same. */
    @Query("SELECT * FROM actions ORDER BY last_done_at ASC, id ASC LIMIT 1")
    suspend fun leastRecentlyDone(): OffPhoneAction?

    @Query("UPDATE actions SET last_done_at = :at, done_count = done_count + 1 WHERE id = :id")
    suspend fun markDone(id: Long, at: Long)

    @Insert
    suspend fun insert(action: OffPhoneAction)

    @Delete
    suspend fun delete(action: OffPhoneAction)

    @Query("SELECT COUNT(*) FROM actions")
    suspend fun count(): Int
}
