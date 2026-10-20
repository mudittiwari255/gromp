package app.gromp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DaySummary::class, AppDay::class, HourUsage::class, NotifDay::class, LockRule::class],
    version = 1,
    exportSchema = true,
)
abstract class GrompDatabase : RoomDatabase() {
    abstract fun dao(): GrompDao

    companion object {
        fun create(context: Context): GrompDatabase =
            Room.databaseBuilder(context, GrompDatabase::class.java, "gromp.db").build()
    }
}
