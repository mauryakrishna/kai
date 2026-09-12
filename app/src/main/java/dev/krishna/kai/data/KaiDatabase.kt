package dev.krishna.kai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AppEvent::class], version = 1, exportSchema = false)
abstract class KaiDatabase : RoomDatabase() {

    abstract fun appEvents(): AppEventDao

    companion object {
        @Volatile private var instance: KaiDatabase? = null

        fun get(context: Context): KaiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KaiDatabase::class.java,
                    "kai.db",
                ).build().also { instance = it }
            }
    }
}
