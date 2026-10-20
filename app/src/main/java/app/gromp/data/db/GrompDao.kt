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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAppHours(rows: List<AppHour>)

    @Query("DELETE FROM app_hour WHERE day = :day")
    abstract suspend fun deleteAppHours(day: String)

    /** Replaces everything stored for one day in a single transaction. */
    @Transaction
    open suspend fun replaceDay(summary: DaySummary, apps: List<AppDay>, hours: List<HourUsage>, appHours: List<AppHour> = emptyList()) {
        deleteAppDays(summary.day)
        deleteHours(summary.day)
        deleteAppHours(summary.day)
        upsertSummary(summary)
        insertAppDays(apps)
        insertHours(hours)
        insertAppHours(appHours)
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

    // ---- Smart features ----

    @Query("SELECT * FROM app_hour WHERE day BETWEEN :from AND :to")
    abstract suspend fun appHours(from: String, to: String): List<AppHour>

    @Query("SELECT * FROM app_hour")
    abstract suspend fun allAppHours(): List<AppHour>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRating(r: SessionRating)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRatings(rows: List<SessionRating>)

    @Query("SELECT * FROM session_rating WHERE day BETWEEN :from AND :to")
    abstract suspend fun ratings(from: String, to: String): List<SessionRating>

    @Query("SELECT * FROM session_rating")
    abstract suspend fun allRatings(): List<SessionRating>

    @Query("SELECT * FROM session_rating")
    abstract fun ratingsFlow(): Flow<List<SessionRating>>

    @Insert
    abstract suspend fun insertEvent(e: NudgeEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertEvents(rows: List<NudgeEvent>)

    @Query("UPDATE nudge_event SET outcome = :outcome WHERE id = :id")
    abstract suspend fun setOutcome(id: Long, outcome: String)

    @Query("SELECT * FROM nudge_event WHERE day BETWEEN :from AND :to ORDER BY at")
    abstract suspend fun events(from: String, to: String): List<NudgeEvent>

    @Query("SELECT * FROM nudge_event WHERE kind = :kind AND day BETWEEN :from AND :to")
    abstract suspend fun eventsOfKind(kind: String, from: String, to: String): List<NudgeEvent>

    @Query("SELECT * FROM nudge_event ORDER BY at")
    abstract suspend fun allEvents(): List<NudgeEvent>

    // ---- Wipe ----

    @Query("DELETE FROM day_summary") abstract suspend fun clearSummaries()
    @Query("DELETE FROM app_day") abstract suspend fun clearAppDays()
    @Query("DELETE FROM hour_usage") abstract suspend fun clearHours()
    @Query("DELETE FROM notif_day") abstract suspend fun clearNotifs()
    @Query("DELETE FROM lock_rule") abstract suspend fun clearRules()
    @Query("DELETE FROM category_rule") abstract suspend fun clearCategoryRules()
    @Query("DELETE FROM app_hour") abstract suspend fun clearAppHours()
    @Query("DELETE FROM session_rating") abstract suspend fun clearRatings()
    @Query("DELETE FROM nudge_event") abstract suspend fun clearEvents()

    @Transaction
    open suspend fun clearAll() {
        clearSummaries(); clearAppDays(); clearHours(); clearNotifs(); clearRules(); clearCategoryRules()
        clearAppHours(); clearRatings(); clearEvents()
    }

    @Transaction
    open suspend fun restore(
        summaries: List<DaySummary>, apps: List<AppDay>, hours: List<HourUsage>,
        notifs: List<NotifDay>, rules: List<LockRule>, categoryRules: List<CategoryRule> = emptyList(),
        appHours: List<AppHour> = emptyList(), ratings: List<SessionRating> = emptyList(), events: List<NudgeEvent> = emptyList(),
    ) {
        clearAll()
        summaries.forEach { upsertSummary(it) }
        insertAppDays(apps)
        insertHours(hours)
        insertNotifs(notifs)
        insertRules(rules)
        insertCategoryRules(categoryRules)
        insertAppHours(appHours)
        insertRatings(ratings)
        insertEvents(events)
    }
}
