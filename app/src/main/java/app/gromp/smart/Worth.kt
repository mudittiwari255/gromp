package app.gromp.smart

/** One "was it worth it?" answer about a session. */
data class Rating(val pkg: String, val start: Long, val end: Long, val worth: Boolean)

/** How often an app's sessions were worth it, once there are enough answers to mean something. */
data class WorthScore(val pkg: String, val worth: Int, val total: Int) {
    val pct: Int get() = if (total == 0) 0 else worth * 100 / total
}

/** A finished session long enough to ask about. */
data class LongSession(val pkg: String, val start: Long, val end: Long) {
    val durationMs: Long get() = end - start
}

object Worth {
    const val MIN_RATINGS = 3
    const val MIN_SESSION_MS = 10 * 60_000L

    /** Scores per app with at least [minRatings] answers, least worth it first. */
    fun scores(ratings: List<Rating>, minRatings: Int = MIN_RATINGS): List<WorthScore> =
        ratings.groupBy { it.pkg }
            .map { (pkg, r) -> WorthScore(pkg, r.count { it.worth }, r.size) }
            .filter { it.total >= minRatings }
            .sortedWith(compareBy<WorthScore> { it.pct }.thenByDescending { it.total })

    /**
     * Sessions worth asking about: pull apps, at least 10 minutes, already over (ended before [now] minus
     * [settleMs], so a session still running isn't asked about), and not yet rated or asked.
     */
    fun toAsk(
        sessions: List<LongSession>,
        isPull: (String) -> Boolean,
        alreadyHandled: Set<Pair<String, Long>>,
        now: Long,
        settleMs: Long = 2 * 60_000L,
        minMs: Long = MIN_SESSION_MS,
    ): List<LongSession> = sessions
        .filter { isPull(it.pkg) && it.durationMs >= minMs && it.end <= now - settleMs && (it.pkg to it.start) !in alreadyHandled }
        .sortedByDescending { it.end }

    /**
     * Stitches together sessions of the same app separated by less than [gapMs]
     * (a glance at the notification shade shouldn't split one long scroll into two).
     */
    fun merge(sessions: List<LongSession>, gapMs: Long = 60_000L): List<LongSession> {
        val out = mutableListOf<LongSession>()
        for (s in sessions.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && last.pkg == s.pkg && s.start - last.end <= gapMs) {
                out[out.lastIndex] = last.copy(end = maxOf(last.end, s.end))
            } else {
                out += s
            }
        }
        return out
    }

    fun scoreLine(s: WorthScore, label: String, voice: Voice): String = when (voice) {
        Voice.Gentle -> "$label felt worth it ${s.pct}% of the time (${s.worth} of ${s.total})."
        Voice.Honest -> "You rate $label worth it ${s.pct}% of the time."
        Voice.Tough -> if (s.pct < 50) "$label was a waste ${100 - s.pct}% of the time, by your own rating." else "You rate $label worth it ${s.pct}% of the time."
    }
}
