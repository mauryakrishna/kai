package dev.krishna.kai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppEvent::class, Truth::class, OffPhoneAction::class, Escape::class],
    version = 5,
    exportSchema = false,
)
abstract class KaiDatabase : RoomDatabase() {

    abstract fun appEvents(): AppEventDao

    abstract fun truths(): TruthDao

    abstract fun actions(): ActionDao

    abstract fun escapes(): EscapeDao

    companion object {
        @Volatile private var instance: KaiDatabase? = null

        /** Adds the escape log, so the pause length can be tuned from evidence. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS escapes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "at INTEGER NOT NULL, " +
                        "day TEXT NOT NULL, " +
                        "package_name TEXT NOT NULL)"
                )
            }
        }

        /** Adds the off-phone actions table. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS actions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "minutes INTEGER NOT NULL, " +
                        "last_done_at INTEGER NOT NULL DEFAULT 0, " +
                        "done_count INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        /** Adds the truths table. Again a real migration: losing the statements
         *  you wrote would be worse than losing the measurements. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS truths (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "last_shown_at INTEGER NOT NULL DEFAULT 0, " +
                        "shown_count INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
