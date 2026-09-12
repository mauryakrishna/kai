package dev.krishna.kai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AppEvent::class], version = 2, exportSchema = false)
abstract class KaiDatabase : RoomDatabase() {

    abstract fun appEvents(): AppEventDao

    companion object {
        @Volatile private var instance: KaiDatabase? = null

        /** Index for the per-app daily count. A real migration, not a destructive
         *  one -- the measured history is the whole point of Stage 1. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_app_events_day_package_name " +
                        "ON app_events (day, package_name)"
                )
            }
        }

        fun get(context: Context): KaiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KaiDatabase::class.java,
                    "kai.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
