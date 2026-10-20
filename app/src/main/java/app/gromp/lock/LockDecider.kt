package app.gromp.lock

import app.gromp.data.db.LockRule
import java.util.concurrent.ConcurrentHashMap

sealed interface Decision {
    /** Let the app through. Re-check after [recheckInMs] if the app is still in front. */
    data class Allow(val recheckInMs: Long?) : Decision
    data object Pause : Decision
    /** A daily limit is used up. [category] is set when it's a shared category limit rather than the app's own. */
    data class Limit(val limitMin: Int, val usedMs: Long, val category: String? = null) : Decision
    data class Focus(val endMinute: Int) : Decision
}

/** What the user has already been let through, per app. Times are epoch millis. */
data class GrantView(
    val pausePassedUntil: Long = 0,
    val limitSnoozeUntil: Long = 0,
    val focusSnoozeUntil: Long = 0,
)

/** A shared limit for the app's category (e.g. Social 1h), with today's total across those apps. */
data class CategoryLimit(
    val category: String,
    val limitMin: Int,
    val usedMs: Long,
    val snoozeUntil: Long = 0,
)

/** Pure decision logic, kept free of Android so it can be unit tested. */
object LockDecider {
    const val RECHECK_MS = 60_000L

    /**
     * @param onOpen true when the app has just come to the front (only then can a pause screen appear);
     *               false for a timed re-check while the user is already inside the app.
     */
    fun decide(
        rule: LockRule?,
        usedMs: Long,
        focusActive: Boolean,
        focusEndMinute: Int,
        grants: GrantView,
        now: Long,
        onOpen: Boolean,
        category: CategoryLimit? = null,
    ): Decision {
        if (rule == null && category == null) return Decision.Allow(null)

        val focusSnoozed = now < grants.focusSnoozeUntil
        if (rule?.focusBlocked == true && focusActive && !focusSnoozed) return Decision.Focus(focusEndMinute)

        val limitMin = rule?.dailyLimitMin
        val limitMs = limitMin?.let { it * 60_000L }
        val limitSnoozed = now < grants.limitSnoozeUntil
        if (limitMin != null && limitMs != null && usedMs >= limitMs && !limitSnoozed) {
            return Decision.Limit(limitMin, usedMs)
        }

        val catMs = category?.let { it.limitMin * 60_000L }
        val catSnoozed = category != null && now < category.snoozeUntil
        if (category != null && catMs != null && category.usedMs >= catMs && !catSnoozed) {
            return Decision.Limit(category.limitMin, category.usedMs, category.category)
        }

        val recentlyLetThrough = now < grants.pausePassedUntil || limitSnoozed || focusSnoozed || catSnoozed
        if (onOpen && rule?.pauseEnabled == true && !recentlyLetThrough) return Decision.Pause

        val waits = buildList {
            if (limitMs != null) add(if (usedMs < limitMs) limitMs - usedMs else grants.limitSnoozeUntil - now)
            if (category != null && catMs != null) add(if (category.usedMs < catMs) catMs - category.usedMs else category.snoozeUntil - now)
            if (rule?.focusBlocked == true) add(if (focusSnoozed) grants.focusSnoozeUntil - now else RECHECK_MS)
        }.filter { it > 0 }
        return Decision.Allow(waits.minOrNull()?.coerceAtLeast(1_000))
    }
}

/** In-memory passes the user earned on a lock screen. Cleared when the process dies, which is fine. */
class LockGrants {
    private val pause = ConcurrentHashMap<String, Long>()
    private val limit = ConcurrentHashMap<String, Long>()
    private val focus = ConcurrentHashMap<String, Long>()
    private val categories = ConcurrentHashMap<String, Long>()
    private val snoozesToday = ConcurrentHashMap<String, Int>()

    fun view(pkg: String) = GrantView(pause[pkg] ?: 0, limit[pkg] ?: 0, focus[pkg] ?: 0)

    fun passPause(pkg: String, forMs: Long = 3 * 60_000L) { pause[pkg] = System.currentTimeMillis() + forMs }

    fun snoozeLimit(pkg: String, forMs: Long = 5 * 60_000L) {
        limit[pkg] = System.currentTimeMillis() + forMs
        snoozesToday.merge(pkg, 1, Int::plus)
    }

    fun snoozeFocus(pkg: String, forMs: Long = 5 * 60_000L) {
        focus[pkg] = System.currentTimeMillis() + forMs
        snoozesToday.merge(pkg, 1, Int::plus)
    }

    /** 5 more minutes for every app in a category at once. */
    fun snoozeCategory(category: String, forMs: Long = 5 * 60_000L) {
        categories[category] = System.currentTimeMillis() + forMs
        snoozesToday.merge("cat:$category", 1, Int::plus)
    }

    fun categorySnoozeUntil(category: String): Long = categories[category] ?: 0

    fun snoozes(key: String): Int = snoozesToday[key] ?: 0
}
