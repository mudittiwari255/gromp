package app.gromp.data

import app.gromp.data.db.AppDay
import app.gromp.data.db.DaySummary
import app.gromp.data.db.HourUsage
import app.gromp.data.db.NotifDay
import app.gromp.util.Fmt
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

enum class Tone { Sage, Amber, Blue, Lilac }

data class Insight(
    val kicker: String,
    val title: String,
    val body: String,
    val badge: String,
    val tone: Tone,
    /** Suggests setting up focus hours from this insight. */
    val suggestsFocus: Boolean = false,
)

data class GoalDay(val label: String, val met: Boolean?, val isToday: Boolean)

data class InsightsData(
    val featured: Insight?,
    /** Short visits and total visits today, drawn as dots. */
    val featuredDots: Pair<Int, Int>?,
    val cards: List<Insight>,
    val goalDays: List<GoalDay>,
    val goalMin: Int,
    val enoughHistory: Boolean,
)

/**
 * Plain, explainable rules over the user's own history. No model, no network:
 * every sentence here can be traced back to numbers stored on the phone.
 */
object Insights {
    private const val HOUR = 3_600_000L

    fun forToday(today: DaySnapshot, usualSoFarMs: Long?, last7: List<DaySummary>): Insight? {
        val now = LocalTime.now()
        val total = today.totalMs
        if (total < 20 * 60_000) return null

        val evening = (19..23).sumOf { today.hours[it] }
        if (now.hour >= 19 && evening * 100 / total >= 25) {
            val pct = evening * 100 / total
            val share = when {
                pct >= 60 -> "Most of today"
                pct >= 45 -> "Nearly half of today"
                pct >= 30 -> "Nearly a third of today"
                else -> "A quarter of today"
            }
            return Insight(
                kicker = "Tonight's pattern",
                title = "$share happened after 7 pm.",
                body = "Evenings tend to stretch. Focus hours can quietly close your busiest apps at a time you choose.",
                badge = "7pm", tone = Tone.Sage, suggestsFocus = true,
            )
        }
        if (usualSoFarMs != null && usualSoFarMs > 0 && total < usualSoFarMs * 0.85) {
            return Insight(
                kicker = "Nice going",
                title = "You're ${Fmt.minutes(usualSoFarMs - total)} under your usual pace.",
                body = "Whatever you did differently today, it's working. Notice it.",
                badge = "↓", tone = Tone.Sage,
            )
        }
        val short = today.parsed.sessions.count { it.duration < 60_000 }
        if (short >= 15) {
            return Insight(
                kicker = "Quick checks",
                title = "$short visits today lasted under a minute.",
                body = "Each quick check restarts the pull. Try leaving the phone face down for your next hour of focus.",
                badge = "$short", tone = Tone.Blue,
            )
        }
        if (last7.isEmpty()) {
            return Insight(
                kicker = "Getting to know you",
                title = "Gromp is learning your usual day.",
                body = "After a few days, you'll see how today compares and where your time tends to slip.",
                badge = "…", tone = Tone.Sage,
            )
        }
        return null
    }

    fun build(
        today: DaySnapshot,
        summaries: List<DaySummary>,
        hours: List<HourUsage>,
        appDays: List<AppDay>,
        notifsToday: List<NotifDay>,
        goalMs: Long,
        label: (String) -> String,
    ): InsightsData {
        val t = today.day
        val withData = summaries.filter { it.totalMs > 0 }
        val cards = mutableListOf<Insight>()

        // Featured: quick checks today.
        val sessions = today.parsed.sessions
        val short = sessions.count { it.duration < 60_000 }
        val featured = if (sessions.size >= 10) Insight(
            kicker = "Today's big one",
            title = "$short of your ${sessions.size} app visits today lasted under a minute.",
            body = if (short * 2 >= sessions.size)
                "Quick checks feel like nothing, but each one restarts the pull. Try leaving the phone face down for your next focus block."
            else "Most of your visits today were deliberate. Short, frequent checks are the habit to watch.",
            badge = "$short", tone = Tone.Sage,
        ) else null

        // Time of day over the last 7 days.
        val week = LongArray(24)
        hours.forEach { week[it.hour] += it.ms }
        val weekTotal = week.sum()
        if (weekTotal > HOUR) {
            val evening = (19..23).sumOf { week[it] }
            val pct = evening * 100 / weekTotal
            if (pct >= 30) {
                cards += Insight(
                    "Time of day", "Evenings are your pull",
                    "About $pct% of this week's screen time happened between 7 pm and midnight.",
                    "7pm", Tone.Amber, suggestsFocus = true,
                )
            } else {
                val peak = (0..21).maxBy { week[it] + week[it + 1] + week[it + 2] }
                val share = (week[peak] + week[peak + 1] + week[peak + 2]) * 100 / weekTotal
                cards += Insight(
                    "Time of day", "Your busiest stretch is ${Fmt.clockFull(peak * 60)} to ${Fmt.clockFull((peak + 3) * 60)}",
                    "Those three hours hold $share% of this week's screen time.",
                    Fmt.clock(peak * 60), Tone.Amber,
                )
            }
        }

        // Notifications.
        val notifTotal = notifsToday.sumOf { it.count }
        notifsToday.maxByOrNull { it.count }?.takeIf { it.count >= 5 }?.let { top ->
            cards += Insight(
                "Notifications", "${label(top.pkg)} sent ${top.count} notifications today",
                "That's ${top.count * 100 / notifTotal.coerceAtLeast(1)}% of the $notifTotal you got. Muting the noisiest chats is the easiest win.",
                "${top.count}", Tone.Sage,
            )
        }

        // First pickup trend: this week vs the week before.
        fun avgFirst(from: LocalDate, to: LocalDate) = withData
            .filter { LocalDate.parse(it.day) in from..to }
            .mapNotNull { it.firstPickupMinute }
            .takeIf { it.size >= 3 }?.average()?.toInt()
        val thisWeek = avgFirst(t.minusDays(6), t)
        val lastWeek = avgFirst(t.minusDays(13), t.minusDays(7))
        if (thisWeek != null && lastWeek != null && abs(thisWeek - lastWeek) >= 5) {
            val later = thisWeek > lastWeek
            cards += Insight(
                "Mornings",
                if (later) "Your first pickup is getting later" else "Your first pickup is getting earlier",
                "It averaged ${Fmt.clockFull(lastWeek)} last week. This week it's ${Fmt.clockFull(thisWeek)}." +
                    if (later) " Small win, worth keeping." else " A phone-free first half hour is a gentle place to start.",
                Fmt.clock(thisWeek), Tone.Blue,
            )
        }

        // Calmest day in the last 30.
        val past = withData.filter { it.day != t.toString() }
        if (past.size >= 5) {
            past.minBy { it.totalMs }.let { calm ->
                val d = LocalDate.parse(calm.day)
                cards += Insight(
                    "Best day", "${Fmt.weekdayLong(d)}, ${Fmt.shortDate(d)} was your calmest day in a month",
                    "${Fmt.duration(calm.totalMs)} in total, with ${calm.pickups} pickups. What was different about it?",
                    Fmt.weekday(d), Tone.Lilac,
                )
            }
        }

        // Biggest app this week.
        val byApp = appDays.groupBy { it.pkg }.mapValues { (_, r) -> r.sumOf { it.ms } }
        val appTotal = byApp.values.sum()
        byApp.maxByOrNull { it.value }?.takeIf { appTotal > HOUR }?.let { (pkg, ms) ->
            cards += Insight(
                "This week", "${label(pkg)} took ${ms * 100 / appTotal}% of your week",
                "${Fmt.duration(ms)} over the last 7 days. A daily limit or a pause before opening can soften that.",
                label(pkg).take(1).uppercase(), Tone.Amber,
            )
        }

        // Goal ring: last 7 days.
        val byDay = summaries.associateBy { it.day }
        val goalDays = (6 downTo 0).map { back ->
            val d = t.minusDays(back.toLong())
            val s = byDay[d.toString()]
            GoalDay(
                label = Fmt.weekday(d).take(1),
                met = s?.takeIf { it.totalMs > 0 }?.let { it.totalMs < goalMs },
                isToday = back == 0,
            )
        }

        return InsightsData(
            featured = featured,
            featuredDots = if (featured != null) short to sessions.size else null,
            cards = cards,
            goalDays = goalDays,
            goalMin = (goalMs / 60_000).toInt(),
            enoughHistory = withData.size >= 3,
        )
    }
}
