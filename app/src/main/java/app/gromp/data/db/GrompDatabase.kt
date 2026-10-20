package app.gromp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DaySummary::class, AppDay::class, HourUsage::class, NotifDay::class, LockRule::class, CategoryRule::class],
    version = 2,
    exportSchema = true,
)
abstract class GrompDatabase : RoomDatabase() {
    abstract fun dao(): GrompDao

    companion object {
        fun create(context: Context): GrompDatabase =
            Room.databaseBuilder(context, GrompDatabase::class.java, "gromp.db")
                .addMigrations(MIGRATION_1_2)
                .build()

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
