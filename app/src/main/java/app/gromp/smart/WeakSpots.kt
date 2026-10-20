package app.gromp.smart

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** One app's time in one hour of one day. */
data class AppHourRow(val day: LocalDate, val pkg: String, val hour: Int, val ms: Long)

/** A time of day that tends to swallow you, e.g. weeknights at 10 pm on Instagram. */
data class WeakSpot(
    val weekend: Boolean,
    val hour: Int,
    val pkg: String,
    /** Average pull-app time in that hour on days of this type. */
    val avgMs: Long,
    /** Share of days of this type where that hour had 15+ minutes. */
    val hitRate: Float,
)

object WeakSpots {
    private const val MIN = 60_000L

    fun isWeekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    /**
     * Finds up to [max] weak spots from the last 28 days (today excluded), weekdays and weekends
     * judged separately. Neighbouring hours collapse into the stronger one.
     */
    fun find(
        rows: List<AppHourRow>,
        isPull: (String) -> Boolean,
        today: LocalDate,
        minAvgMs: Long = 25 * MIN,
        minHitRate: Float = 0.4f,
        minDays: Int = 3,
        max: Int = 3,
    ): List<WeakSpot> {
        val from = today.minusDays(28)
        val recent = rows.filter { it.day >= from && it.day < today }
        val out = mutableListOf<WeakSpot>()
        for (weekend in listOf(false, true)) {
            val ofType = recent.filter { isWeekend(it.day) == weekend }
            val days = ofType.map { it.day }.distinct()
            if (days.size < minDays) continue
            val pull = ofType.filter { isPull(it.pkg) }
            for (hour in 0..23) {
                val inHour = pull.filter { it.hour == hour }
                if (inHour.isEmpty()) continue
                val perDay = inHour.groupBy { it.day }.mapValues { (_, r) -> r.sumOf { it.ms } }
                val avg = perDay.values.sum() / days.size
                val hitRate = perDay.values.count { it >= 15 * MIN }.toFloat() / days.size
                if (avg < minAvgMs || hitRate < minHitRate) continue
                val top = inHour.groupBy { it.pkg }.maxBy { (_, r) -> r.sumOf { it.ms } }.key
                out += WeakSpot(weekend, hour, top, avg, hitRate)
            }
        }
        val chosen = mutableListOf<WeakSpot>()
        for (s in out.sortedByDescending { it.avgMs }) {
            if (chosen.any { it.weekend == s.weekend && kotlin.math.abs(it.hour - s.hour) <= 1 }) continue
            chosen += s
            if (chosen.size == max) break
        }
        return chosen
    }

    /** A weak spot starting 10 to 45 minutes from [now] on the same kind of day, for a heads-up. */
    fun upcoming(spots: List<WeakSpot>, now: LocalDateTime, minLead: Int = 10, maxLead: Int = 45): WeakSpot? {
        val weekend = isWeekend(now.toLocalDate())
        val minute = now.hour * 60 + now.minute
        return spots.firstOrNull { it.weekend == weekend && (it.hour * 60 - minute) in minLead..maxLead }
    }

    /** "Weeknights around 10 pm" / "Weekend afternoons around 3 pm". */
    fun describe(s: WeakSpot): String {
        val part = when (s.hour) {
            in 5..11 -> "mornings"
            in 12..16 -> "afternoons"
            in 17..20 -> "evenings"
            else -> "nights"
        }
        val days = if (s.weekend) "Weekend" else "Weekday"
        val h = s.hour % 12
        val clock = "${if (h == 0) 12 else h} ${if (s.hour < 12) "am" else "pm"}"
        return "$days $part around $clock"
    }
}
