package app.gromp.lock

import app.gromp.smart.IntentKind
import app.gromp.smart.IntentionMark
import app.gromp.smart.LoopDetector
import app.gromp.smart.SessionWatch
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory state shared by the locks service and the check-in screens:
 * what the user said they came for, and what they chose on each check-in.
 */
class NudgeState {
    /** Only touched from the service's main thread. */
    val watch = SessionWatch()
    val loops = LoopDetector()

    private val intentions = ConcurrentHashMap<String, IntentionMark>()
    private val askAgainAfter = ConcurrentHashMap<String, Long>()
    private val driftHandledFor = ConcurrentHashMap<String, Long>()
    private val bingeSnooze = ConcurrentHashMap<String, Long>()

    /** Don't ask "what for?" again if the user comes back within this window. */
    private val intentionPassMs = 3 * 60_000L

    fun needsIntention(pkg: String, now: Long): Boolean = now >= (askAgainAfter[pkg] ?: 0L)

    fun recordIntention(pkg: String, kind: IntentKind?, now: Long = System.currentTimeMillis()) {
        if (kind != null) intentions[pkg] = IntentionMark(pkg, kind, now) else intentions.remove(pkg)
        askAgainAfter[pkg] = now + intentionPassMs
    }

    fun intention(pkg: String): IntentionMark? = intentions[pkg]

    fun isDriftHandled(pkg: String): Boolean {
        val i = intentions[pkg] ?: return true
        return driftHandledFor[pkg] == i.at
    }

    fun markDriftHandled(pkg: String) {
        intentions[pkg]?.let { driftHandledFor[pkg] = it.at }
    }

    fun bingeSnoozeUntil(pkg: String): Long = bingeSnooze[pkg] ?: 0L

    fun snoozeBinge(pkg: String, until: Long) {
        bingeSnooze[pkg] = until
    }

    /** A fresh session starts with a clean binge clock. */
    fun onNewSession(pkg: String) {
        bingeSnooze.remove(pkg)
    }
}
