package app.gromp.data.usage

import kotlin.math.max
import kotlin.math.min

/** The handful of usage events Gromp cares about, decoupled from Android types so it can be unit tested. */
enum class EvKind { RESUMED, PAUSED, SCREEN_ON, SCREEN_OFF, UNLOCK, LOCK_SHOWN }

data class Ev(val pkg: String, val time: Long, val kind: EvKind)

/** One continuous stretch of time with [pkg] in the foreground. */
data class Session(val pkg: String, val start: Long, val end: Long) {
    val duration: Long get() = end - start
}

data class ParsedRange(
    val sessions: List<Session>,
    /** Number of times the phone was picked up (unlocked, or screen turned on when there is no lock). */
    val pickups: Int,
    val firstPickup: Long?,
)

object SessionParser {
    /** An app that pauses and resumes within this gap (e.g. switching activities) is one session. */
    const val SAME_APP_GAP_MS = 2_000L

    /**
     * Turns raw foreground events into sessions, clipped to [rangeStart, rangeEnd).
     * Events may begin before [rangeStart] so that a session crossing midnight is counted correctly.
     * Packages in [ignored] (launchers, Gromp itself, system UI) still end other sessions but are not recorded.
     */
    fun parse(events: List<Ev>, rangeStart: Long, rangeEnd: Long, ignored: Set<String> = emptySet()): ParsedRange {
        val sorted = events.sortedBy { it.time }
        val sessions = mutableListOf<Session>()
        var current: String? = null
        var start = 0L
        var pausedAt: Long? = null

        fun close(at: Long) {
            val pkg = current ?: return
            val s = max(start, rangeStart)
            val e = min(at, rangeEnd)
            if (e > s && pkg !in ignored) sessions += Session(pkg, s, e)
            current = null
            pausedAt = null
        }

        val unlocks = mutableListOf<Long>()
        val screenOns = mutableListOf<Long>()

        for (ev in sorted) {
            if (ev.time >= rangeEnd) break
            when (ev.kind) {
                EvKind.RESUMED -> {
                    val paused = pausedAt
                    if (current == ev.pkg && (paused == null || ev.time - paused <= SAME_APP_GAP_MS)) {
                        pausedAt = null
                    } else {
                        close(paused ?: ev.time)
                        current = ev.pkg
                        start = ev.time
                    }
                }
                EvKind.PAUSED -> if (current == ev.pkg && pausedAt == null) pausedAt = ev.time
                EvKind.SCREEN_OFF, EvKind.LOCK_SHOWN -> close(pausedAt ?: ev.time)
                EvKind.SCREEN_ON -> if (ev.time >= rangeStart) screenOns += ev.time
                EvKind.UNLOCK -> if (ev.time >= rangeStart) unlocks += ev.time
            }
        }
        if (current != null) close(pausedAt ?: rangeEnd)

        val pickupTimes = if (unlocks.isNotEmpty()) unlocks else screenOns
        return ParsedRange(
            sessions = sessions,
            pickups = pickupTimes.size,
            firstPickup = pickupTimes.minOrNull(),
        )
    }
}

/** Per-app totals for one day. */
data class AppTotals(
    val pkg: String,
    val ms: Long,
    val opens: Int,
    val longestMs: Long,
    val under1: Int,
    val m1to5: Int,
    val m5to15: Int,
    val over15: Int,
)

object Aggregator {
    private const val MIN = 60_000L
    private const val HOUR = 3_600_000L

    fun byApp(sessions: List<Session>): List<AppTotals> =
        sessions.groupBy { it.pkg }.map { (pkg, list) ->
            AppTotals(
                pkg = pkg,
                ms = list.sumOf { it.duration },
                opens = list.size,
                longestMs = list.maxOf { it.duration },
                under1 = list.count { it.duration < MIN },
                m1to5 = list.count { it.duration in MIN until 5 * MIN },
                m5to15 = list.count { it.duration in 5 * MIN until 15 * MIN },
                over15 = list.count { it.duration >= 15 * MIN },
            )
        }.sortedByDescending { it.ms }

    /** Splits sessions into 24 hourly buckets (milliseconds), measured from [dayStart]. */
    fun byHour(sessions: List<Session>, dayStart: Long): LongArray {
        val out = LongArray(24)
        for (s in sessions) {
            var t = s.start
            while (t < s.end) {
                val hour = ((t - dayStart) / HOUR).toInt()
                if (hour !in 0..23) break
                val hourEnd = dayStart + (hour + 1) * HOUR
                val segEnd = min(hourEnd, s.end)
                out[hour] += segEnd - t
                t = segEnd
            }
        }
        return out
    }
}
