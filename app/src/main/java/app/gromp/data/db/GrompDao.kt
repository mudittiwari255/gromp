package app.gromp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GrompDao {

    // ---- Snapshots ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSummary(s: DaySummary)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAppDays(rows: List<AppDay>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertHours(rows: List<HourUsage>)

    @Query("DELETE FROM app_day WHERE day = :day")
    abstract suspend fun deleteAppDays(day: String)

    @Query("DELETE FROM hour_usage WHERE day = :day")
    abstract suspend fun deleteHours(day: String)

    /** Replaces everything stored for one day in a single transaction. */
    @Transaction
    open suspend fun replaceDay(summary: DaySummary, apps: List<AppDay>, hours: List<HourUsage>) {
        deleteAppDays(summary.day)
        deleteHours(summary.day)
        upsertSummary(summary)
        insertAppDays(apps)
        insertHours(hours)
    }

    // ---- Reads ----

    @Query("SELECT * FROM day_summary WHERE day BETWEEN :from AND :to ORDER BY day")
    abstract suspend fun summaries(from: String, to: String): List<DaySummary>

    @Query("SELECT * FROM day_summary ORDER BY day")
    abstract suspend fun allSummaries(): List<DaySummary>

    @Query("SELECT * FROM app_day WHERE day BETWEEN :from AND :to")
    abstract suspend fun appDays(from: String, to: String): List<AppDay>

    @Query("SELECT * FROM app_day WHERE pkg = :pkg AND day BETWEEN :from AND :to ORDER BY day")
    abstract suspend fun appSeries(pkg: String, from: String, to: String): List<AppDay>

    @Query("SELECT * FROM hour_usage WHERE day BETWEEN :from AND :to")
    abstract suspend fun hours(from: String, to: String): List<HourUsage>

    @Query("SELECT * FROM notif_day WHERE day BETWEEN :from AND :to")
    abstract suspend fun notifs(from: String, to: String): List<NotifDay>

    @Query("SELECT * FROM app_day")
    abstract suspend fun allAppDays(): List<AppDay>

    @Query("SELECT * FROM hour_usage")
    abstract suspend fun allHours(): List<HourUsage>

    @Query("SELECT * FROM notif_day")
    abstract suspend fun allNotifs(): List<NotifDay>

    // ---- Notifications ----

    @Query("UPDATE notif_day SET count = count + 1 WHERE day = :day AND pkg = :pkg")
    abstract suspend fun bumpNotif(day: String, pkg: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertNotif(row: NotifDay): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertNotifs(rows: List<NotifDay>)

    /** SQLite on Android 9 has no UPSERT, so update-then-insert. */
    @Transaction
    open suspend fun countNotification(day: String, pkg: String) {
        if (bumpNotif(day, pkg) == 0) insertNotif(NotifDay(day, pkg, 1))
    }

    // ---- Lock rules ----

    @Query("SELECT * FROM lock_rule")
    abstract fun rulesFlow(): Flow<List<LockRule>>

    @Query("SELECT * FROM lock_rule")
    abstract suspend fun rules(): List<LockRule>

    @Query("SELECT * FROM lock_rule WHERE pkg = :pkg")
    abstract suspend fun rule(pkg: String): LockRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRule(rule: LockRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRules(rules: List<LockRule>)

    @Query("DELETE FROM lock_rule WHERE pkg = :pkg")
    abstract suspend fun deleteRule(pkg: String)

    // ---- Category limits ----

    @Query("SELECT * FROM category_rule")
    abstract fun categoryRulesFlow(): Flow<List<CategoryRule>>

    @Query("SELECT * FROM category_rule")
    abstract suspend fun categoryRules(): List<CategoryRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCategoryRule(rule: CategoryRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCategoryRules(rules: List<CategoryRule>)

    @Query("DELETE FROM category_rule WHERE category = :category")
    abstract suspend fun deleteCategoryRule(category: String)

    // ---- Wipe ----

    @Query("DELETE FROM day_summary") abstract suspend fun clearSummaries()
    @Query("DELETE FROM app_day") abstract suspend fun clearAppDays()
    @Query("DELETE FROM hour_usage") abstract suspend fun clearHours()
    @Query("DELETE FROM notif_day") abstract suspend fun clearNotifs()
    @Query("DELETE FROM lock_rule") abstract suspend fun clearRules()
    @Query("DELETE FROM category_rule") abstract suspend fun clearCategoryRules()

    @Transaction
    open suspend fun clearAll() {
        clearSummaries(); clearAppDays(); clearHours(); clearNotifs(); clearRules(); clearCategoryRules()
    }

    @Transaction
    open suspend fun restore(
        summaries: List<DaySummary>, apps: List<AppDay>, hours: List<HourUsage>,
        notifs: List<NotifDay>, rules: List<LockRule>, categoryRules: List<CategoryRule> = emptyList(),
    ) {
        clearAll()
        summaries.forEach { upsertSummary(it) }
        insertAppDays(apps)
        insertHours(hours)
        insertNotifs(notifs)
        insertRules(rules)
        insertCategoryRules(categoryRules)
    }
}
