package app.gromp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DaySummary::class, AppDay::class, HourUsage::class, NotifDay::class, LockRule::class, CategoryRule::class,
        AppHour::class, SessionRating::class, NudgeEvent::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class GrompDatabase : RoomDatabase() {
    abstract fun dao(): GrompDao

    companion object {
        fun create(context: Context): GrompDatabase =
            Room.databaseBuilder(context, GrompDatabase::class.java, "gromp.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v3 adds the smart features: per-app hourly time, worth-it ratings and check-in history. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_hour` (`day` TEXT NOT NULL, `pkg` TEXT NOT NULL, `hour` INTEGER NOT NULL, " +
                        "`ms` INTEGER NOT NULL, PRIMARY KEY(`day`, `pkg`, `hour`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_rating` (`pkg` TEXT NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, " +
                        "`day` TEXT NOT NULL, `worth` INTEGER NOT NULL, `ratedAt` INTEGER NOT NULL, PRIMARY KEY(`pkg`, `start`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `nudge_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL, " +
                        "`day` TEXT NOT NULL, `pkg` TEXT NOT NULL, `kind` TEXT NOT NULL, `detail` TEXT, `outcome` TEXT)",
                )
            }
        }

        /** v2 adds category limits. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_rule` (`category` TEXT NOT NULL, `dailyLimitMin` INTEGER NOT NULL, PRIMARY KEY(`category`))",
                )
            }
        }
    }
}
