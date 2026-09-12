package dev.krishna.kai.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One use of the escape hatch.
 *
 * Recorded so the pause length can be set from evidence rather than guesswork.
 * A handful a week means two minutes is right; reaching for it constantly means
 * the gate is mistuned, and never touching it means it's too hard to find.
 */
@Entity(tableName = "escapes")
data class Escape(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val day: String,
    @ColumnInfo(name = "package_name") val packageName: String,
)

@Dao
interface EscapeDao {

    @Insert
    suspend fun insert(escape: Escape)

    @Query("SELECT COUNT(*) FROM escapes WHERE day = :day")
    suspend fun countForDay(day: String): Int

    @Query("SELECT COUNT(*) FROM escapes")
    suspend fun total(): Int

    @Query("SELECT package_name, COUNT(*) AS uses FROM escapes GROUP BY package_name ORDER BY uses DESC LIMIT 5")
    suspend fun topPackages(): List<EscapeCount>
}

data class EscapeCount(
    @ColumnInfo(name = "package_name") val packageName: String,
    val uses: Int,
)
