package app.gromp.lock

import app.gromp.data.db.LockRule
import java.util.concurrent.ConcurrentHashMap

sealed interface Decision {
    /** Let the app through. Re-check after [recheckInMs] if the app is still in front. */
    data class Allow(val recheckInMs: Long?) : Decision
    data object Pause : Decision
    data class Limit(val limitMin: Int, val usedMs: Long) : Decision
    data class Focus(val endMinute: Int) : Decision
}

/** What the user has already been let through, per app. Times are epoch millis. */
data class GrantView(
    val pausePassedUntil: Long = 0,
    val limitSnoozeUntil: Long = 0,
    val focusSnoozeUntil: Long = 0,
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
    ): Decision {
        if (rule == null) return Decision.Allow(null)

        val focusSnoozed = now < grants.focusSnoozeUntil
        if (rule.focusBlocked && focusActive && !focusSnoozed) return Decision.Focus(focusEndMinute)

        val limitMin = rule.dailyLimitMin
        val limitMs = limitMin?.let { it * 60_000L }
        val limitSnoozed = now < grants.limitSnoozeUntil
        if (limitMin != null && limitMs != null && usedMs >= limitMs && !limitSnoozed) {
            return Decision.Limit(limitMin, usedMs)
        }

        val recentlyLetThrough = now < grants.pausePassedUntil || limitSnoozed || focusSnoozed
        if (onOpen && rule.pauseEnabled && !recentlyLetThrough) return Decision.Pause

        val waits = buildList {
            if (limitMs != null) add(if (usedMs < limitMs) limitMs - usedMs else grants.limitSnoozeUntil - now)
            if (rule.focusBlocked) add(if (focusSnoozed) grants.focusSnoozeUntil - now else RECHECK_MS)
        }.filter { it > 0 }
        return Decision.Allow(waits.minOrNull()?.coerceAtLeast(1_000))
    }
}

/** In-memory passes the user earned on a lock screen. Cleared when the process dies, which is fine. */
class LockGrants {
    private val pause = ConcurrentHashMap<String, Long>()
    private val limit = ConcurrentHashMap<String, Long>()
    private val focus = ConcurrentHashMap<String, Long>()
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

    fun snoozes(pkg: String): Int = snoozesToday[pkg] ?: 0
}
