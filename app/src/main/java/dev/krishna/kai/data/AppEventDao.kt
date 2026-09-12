package dev.krishna.kai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AppEventDao {

    @Insert
    suspend fun insert(event: AppEvent)

    @Query("SELECT * FROM app_events WHERE day = :day ORDER BY started_at")
    suspend fun forDay(day: String): List<AppEvent>

    @Query("SELECT COUNT(*) FROM app_events")
    suspend fun total(): Int

    /** How many times this app has already been opened today. Drives the wait. */
    @Query("SELECT COUNT(*) FROM app_events WHERE day = :day AND package_name = :pkg")
    suspend fun countForApp(day: String, pkg: String): Int
}
