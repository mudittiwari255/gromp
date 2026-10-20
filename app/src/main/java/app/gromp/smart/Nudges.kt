package app.gromp.smart

/** The pull-app session currently in front, as seen by the lock service. */
data class Watch(val pkg: String, val start: Long)

data class IntentionMark(val pkg: String, val kind: IntentKind, val at: Long)

enum class NudgeKind { Drift, Binge }

data class Due(val kind: NudgeKind, val at: Long)

/** Decides when the next in-app check-in should appear. Pure, so it can be tested without a phone. */
object NudgePlanner {
    private const val MIN = 60_000L

    /**
     * @param intention the reason the user gave when opening, if any
     * @param driftHandled true once a drift check-in has been shown for this intention
     * @param bingeSnoozeUntil "keep going" pushes the next binge check-in to this time
     */
    fun next(
        watch: Watch?,
        intention: IntentionMark?,
        driftHandled: Boolean,
        bingeAfterMs: Long,
        bingeSnoozeUntil: Long,
        bingeEnabled: Boolean = true,
    ): Due? {
        if (watch == null) return null
        val due = mutableListOf<Due>()
        if (intention != null && intention.pkg == watch.pkg && !driftHandled && intention.at >= watch.start - MIN) {
            due += Due(NudgeKind.Drift, intention.at + intention.kind.driftMin * MIN)
        }
        if (bingeEnabled) due += Due(NudgeKind.Binge, maxOf(watch.start + bingeAfterMs, bingeSnoozeUntil))
        return due.minByOrNull { it.at }
    }
}

/**
 * Tracks the current pull-app session. Coming back to the same app within [gapMs]
 * (for example after one of Gromp's own check-in screens) continues the session.
 */
class SessionWatch(private val gapMs: Long = 60_000L) {
    var current: Watch? = null
        private set
    private var leftAt: Long? = null

    /** @return true if this started a new session. */
    fun onForeground(pkg: String, isPull: Boolean, now: Long): Boolean {
        val c = current
        val left = leftAt
        if (c != null && c.pkg == pkg && (left == null || now - left <= gapMs)) {
            leftAt = null
            return false
        }
        current = if (isPull) Watch(pkg, now) else null
        leftAt = null
        return current != null
    }

    fun onLeave(now: Long) {
        if (current != null && leftAt == null) leftAt = now
    }

    fun onScreenOff() {
        current = null
        leftAt = null
    }
}

data class LoopHit(val apps: Int, val spanMs: Long)

/** Spots the doom-loop: hopping between several pull apps in a few minutes. */
class LoopDetector(
    private val windowMs: Long = 10 * 60_000L,
    private val minOpens: Int = 4,
    private val minApps: Int = 3,
    private val cooldownMs: Long = 15 * 60_000L,
) {
    private val opens = ArrayDeque<Pair<String, Long>>()
    private var lastAlert = Long.MIN_VALUE / 2

    /** Call when a pull app comes to the front. Returns a hit if this completes a loop worth calling out. */
    fun onPullOpen(pkg: String, now: Long): LoopHit? {
        opens.addLast(pkg to now)
        while (opens.isNotEmpty() && opens.first().second < now - windowMs) opens.removeFirst()
        val apps = opens.map { it.first }.distinct().size
        if (opens.size < minOpens || apps < minApps || now - lastAlert < cooldownMs) return null
        val hit = LoopHit(apps, now - opens.first().second)
        lastAlert = now
        opens.clear()
        return hit
    }

    fun reset() = opens.clear()
}
