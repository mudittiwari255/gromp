package app.gromp.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** Reads raw foreground events from Android and hands them to [SessionParser]. */
class UsageReader(context: Context) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    // Raw values so we don't depend on API-29 constant names (they are inlined ints anyway).
    private companion object {
        const val ACTIVITY_RESUMED = 1      // was MOVE_TO_FOREGROUND
        const val ACTIVITY_PAUSED = 2       // was MOVE_TO_BACKGROUND
        const val SCREEN_INTERACTIVE = 15
        const val SCREEN_NON_INTERACTIVE = 16
        const val KEYGUARD_SHOWN = 17
        const val KEYGUARD_HIDDEN = 18

        /** Look back this far before a range so sessions crossing its start are seen whole. */
        const val LOOKBACK_MS = 3 * 3_600_000L
    }

    fun read(rangeStart: Long, rangeEnd: Long, ignored: Set<String>): ParsedRange {
        val events = usm.queryEvents(rangeStart - LOOKBACK_MS, rangeEnd) ?: return ParsedRange(emptyList(), 0, null)
        val out = ArrayList<Ev>(4096)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val kind = when (e.eventType) {
                ACTIVITY_RESUMED -> EvKind.RESUMED
                ACTIVITY_PAUSED -> EvKind.PAUSED
                SCREEN_INTERACTIVE -> EvKind.SCREEN_ON
                SCREEN_NON_INTERACTIVE -> EvKind.SCREEN_OFF
                KEYGUARD_SHOWN -> EvKind.LOCK_SHOWN
                KEYGUARD_HIDDEN -> EvKind.UNLOCK
                else -> null
            } ?: continue
            out += Ev(e.packageName ?: "", e.timeStamp, kind)
        }
        return SessionParser.parse(out, rangeStart, rangeEnd, ignored)
    }

    /** Package that most recently came to the foreground within [lookbackMs], if any. */
    fun currentForeground(lookbackMs: Long = 10_000L): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - lookbackMs, now) ?: return null
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == ACTIVITY_RESUMED) last = e.packageName
        }
        return last
    }
}
