package app.gromp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Days are stored as ISO dates ("2026-10-20") in the phone's local time zone. */

@Entity(tableName = "day_summary")
data class DaySummary(
    @PrimaryKey val day: String,
    val totalMs: Long,
    val pickups: Int,
    /** Minutes after midnight of the first pickup, or null if the phone wasn't used. */
    val firstPickupMinute: Int?,
    val longestSessionMs: Long,
    val longestPkg: String?,
    val longestStart: Long?,
    /** Sessions shorter than one minute. */
    val shortSessions: Int,
    val sessions: Int,
    val updatedAt: Long,
)

@Entity(tableName = "app_day", primaryKeys = ["day", "pkg"])
data class AppDay(
    val day: String,
    val pkg: String,
    val ms: Long,
    val opens: Int,
    val longestMs: Long,
    val under1: Int,
    val m1to5: Int,
    val m5to15: Int,
    val over15: Int,
)

@Entity(tableName = "hour_usage", primaryKeys = ["day", "hour"])
data class HourUsage(
    val day: String,
    val hour: Int,
    val ms: Long,
)

@Entity(tableName = "notif_day", primaryKeys = ["day", "pkg"])
data class NotifDay(
    val day: String,
    val pkg: String,
    val count: Int,
)

/** A soft lock the user set up for one app. */
@Entity(tableName = "lock_rule")
data class LockRule(
    @PrimaryKey val pkg: String,
    /** Gentle daily limit in minutes, or null for none. */
    val dailyLimitMin: Int? = null,
    /** Show the "breathe" pause screen before each open. */
    val pauseEnabled: Boolean = false,
    /** Blocked during focus hours. */
    val focusBlocked: Boolean = false,
)

fun LockRule.hasNoLocks(): Boolean = dailyLimitMin == null && !pauseEnabled && !focusBlocked

/** A shared daily limit for every app in one category, e.g. Social 1h. [category] is a [app.gromp.data.Category] name. */
@Entity(tableName = "category_rule")
data class CategoryRule(
    @PrimaryKey val category: String,
    val dailyLimitMin: Int,
)
