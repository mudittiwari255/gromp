package app.gromp.smart

import app.gromp.util.Fmt
import java.time.LocalDate
import kotlin.math.abs

data class DayTotal(
    val day: LocalDate,
    val ms: Long,
    val pickups: Int = 0,
    val shortSessions: Int = 0,
    val sessions: Int = 0,
    val firstPickupMinute: Int? = null,
)

/** A logged check-in and what the user did. [outcome] is "closed", "continued" or null. */
data class CheckInEvent(val kind: String, val pkg: String, val outcome: String?)

enum class ExperimentKind { FocusHours, AppLimit, MuteNotifications, PhoneDown, MorningFree }

/** One concrete thing to try next week. */
data class Experiment(
    val kind: ExperimentKind,
    val title: String,
    val body: String,
    val pkg: String? = null,
    val limitMin: Int? = null,
    val hour: Int? = null,
)

data class ReviewInput(
    val weekStart: LocalDate,
    val days: List<DayTotal>,
    val prevWeekMs: Long?,
    val appsThis: Map<String, Long>,
    val appsPrev: Map<String, Long>,
    val ratings: List<Rating>,
    val checkIns: List<CheckInEvent>,
    val goalMin: Int,
    val taper: TaperDecision?,
    val weakSpots: List<WeakSpot>,
    val topNotif: Pair<String, Int>?,
    val trades: List<Trade>,
    val voice: Voice,
    val label: (String) -> String,
)

data class AppWeek(val pkg: String, val ms: Long, val prevMs: Long?)

data class Review(
    val weekStart: LocalDate,
    val totalMs: Long,
    val prevWeekMs: Long?,
    val headline: String,
    val costLine: String,
    val wins: List<String>,
    val slips: List<String>,
    val worthLine: String?,
    val checkInLine: String?,
    val goalLine: String,
    val nextGoalLine: String?,
    val experiment: Experiment,
    val days: List<DayTotal>,
    val topApps: List<AppWeek>,
)

/** The Sunday review, built from plain rules over the week's numbers. No model involved. */
object WeeklyReview {
    private const val MIN = 60_000L

    fun build(i: ReviewInput): Review {
        val v = i.voice
        val data = i.days.filter { it.ms > 0 }
        val total = data.sumOf { it.ms }
        val goalMs = i.goalMin * MIN

        // Headline
        val prev = i.prevWeekMs?.takeIf { it > 0 }
        val headline = if (prev == null) {
            when (v) {
                Voice.Gentle -> "Your first week with Gromp. Here's what it looked like."
                Voice.Honest -> "Your first full week, in numbers."
                Voice.Tough -> "Week one. Now you can't say you didn't know."
            }
        } else {
            val diff = total - prev
            val pct = (abs(diff) * 100 / prev).toInt()
            when {
                pct < 3 -> when (v) {
                    Voice.Gentle -> "A steady week, about the same as last."
                    Voice.Honest -> "About the same as last week."
                    Voice.Tough -> "No better than last week. Flat isn't progress."
                }
                diff < 0 -> when (v) {
                    Voice.Gentle -> "A calmer week: $pct% less than last week."
                    Voice.Honest -> "${Fmt.duration(-diff)} less than last week (down $pct%)."
                    Voice.Tough -> "Down $pct%. Good. Don't get comfortable."
                }
                else -> when (v) {
                    Voice.Gentle -> "A heavier week, up $pct%. That happens. Let's look at why."
                    Voice.Honest -> "${Fmt.duration(diff)} more than last week (up $pct%)."
                    Voice.Tough -> "Up $pct%. You went backwards this week."
                }
            }
        }

        val best = data.minByOrNull { it.ms }
        val worst = data.maxByOrNull { it.ms }
        val met = data.count { it.ms < goalMs }

        // App changes
        val top = i.appsThis.entries.sortedByDescending { it.value }.take(3).map { AppWeek(it.key, it.value, i.appsPrev[it.key]) }
        val changes = i.appsThis.keys.union(i.appsPrev.keys).associateWith { (i.appsThis[it] ?: 0L) - (i.appsPrev[it] ?: 0L) }
        val biggestDrop = if (i.appsPrev.isEmpty()) null else changes.entries.filter { it.value <= -30 * MIN }.minByOrNull { it.value }
        val biggestRise = if (i.appsPrev.isEmpty()) null else changes.entries.filter { it.value >= 30 * MIN }.maxByOrNull { it.value }

        // Check-ins
        val outcomes = i.checkIns.filter { it.outcome != null && it.kind != "Intention" }
        val closed = outcomes.count { it.outcome == "closed" }
        val bingesContinued = i.checkIns.count { it.kind == "Binge" && it.outcome == "continued" }

        val wins = buildList {
            if (best != null && data.size >= 2) add("Calmest day: ${Fmt.weekdayLong(best.day)}, ${Fmt.duration(best.ms)}")
            if (met > 0) add("Under your ${Fmt.duration(goalMs)} limit on $met of ${data.size} days")
            if (prev != null && total < prev) add("${Fmt.duration(prev - total)} back in your week")
            biggestDrop?.let { add("${i.label(it.key)} down ${Fmt.duration(-it.value)}") }
            if (outcomes.isNotEmpty() && closed > 0) add("You closed the app $closed of ${outcomes.size} times Gromp checked in")
        }
        val slips = buildList {
            if (worst != null && data.size >= 2 && worst != best) add("Heaviest day: ${Fmt.weekdayLong(worst.day)}, ${Fmt.duration(worst.ms)}")
            biggestRise?.let { add("${i.label(it.key)} up ${Fmt.duration(it.value)}") }
            if (bingesContinued >= 2) add("You kept going through $bingesContinued binge check-ins")
        }

        val scores = Worth.scores(i.ratings)
        val worthLine = scores.firstOrNull()?.let { Worth.scoreLine(it, i.label(it.pkg), v) }
        val checkInLine = if (outcomes.isEmpty()) null else "Gromp checked in ${outcomes.size} ${if (outcomes.size == 1) "time" else "times"}. You closed the app on $closed."

        val goalLine = if (data.isEmpty()) "No days recorded this week." else "Under your ${Fmt.duration(goalMs)} limit on $met of ${data.size} days."
        val nextGoalLine = i.taper?.let { t ->
            if (t.changed) when (v) {
                Voice.Gentle -> "Next week's limit eases down to ${Fmt.duration(t.newGoalMin * MIN)}. You've earned it."
                Voice.Honest -> "Next week's limit: ${Fmt.duration(t.newGoalMin * MIN)}, down from ${Fmt.duration(t.oldGoalMin * MIN)}."
                Voice.Tough -> "New limit: ${Fmt.duration(t.newGoalMin * MIN)}. You proved you can. Now do it again."
            } else "Your limit stays at ${Fmt.duration(t.oldGoalMin * MIN)}. Stay under it on most days to bring it down."
        }

        return Review(
            weekStart = i.weekStart,
            totalMs = total,
            prevWeekMs = prev,
            headline = headline,
            costLine = Cost.weekLine(total, v),
            wins = wins,
            slips = slips,
            worthLine = worthLine,
            checkInLine = checkInLine,
            goalLine = goalLine,
            nextGoalLine = nextGoalLine,
            experiment = experiment(i, data, scores),
            days = i.days,
            topApps = top,
        )
    }

    /** Picks the single most useful thing to try, in priority order. */
    fun experiment(i: ReviewInput, data: List<DayTotal>, scores: List<WorthScore>): Experiment {
        i.weakSpots.firstOrNull()?.let { s ->
            val app = i.label(s.pkg)
            val h = s.hour % 12
            val clock = "${if (h == 0) 12 else h} ${if (s.hour < 12) "am" else "pm"}"
            return Experiment(
                ExperimentKind.FocusHours,
                "Close your $clock window",
                "${WeakSpots.describe(s)} you average ${Fmt.duration(s.avgMs)} on $app. Try focus hours from $clock for one week.",
                pkg = s.pkg, hour = s.hour,
            )
        }

        val days = data.size.coerceAtLeast(1)
        val topApps = i.appsThis.entries.sortedByDescending { it.value }.take(3).map { it.key }.toSet()
        scores.firstOrNull { it.pct <= 35 && it.pkg in topApps }?.let { s ->
            val avgMin = ((i.appsThis[s.pkg] ?: 0L) / days / MIN).toInt()
            val limit = maxOf(15, (avgMin * 0.7).toInt() / 5 * 5)
            return Experiment(
                ExperimentKind.AppLimit,
                "Give ${i.label(s.pkg)} a ${Fmt.duration(limit * MIN)} limit",
                "You rated it worth it only ${s.pct}% of the time, and it averaged ${Fmt.duration(avgMin * MIN)} a day.",
                pkg = s.pkg, limitMin = limit,
            )
        }

        i.topNotif?.takeIf { it.second / 7 >= 30 }?.let { (pkg, count) ->
            return Experiment(
                ExperimentKind.MuteNotifications,
                "Mute ${i.label(pkg)} for a week",
                "It sent you $count notifications this week, about ${count / 7} a day. Most of them pulled you back in.",
                pkg = pkg,
            )
        }

        val sessions = data.sumOf { it.sessions }
        val short = data.sumOf { it.shortSessions }
        if (sessions >= 20 && short * 2 >= sessions) {
            return Experiment(
                ExperimentKind.PhoneDown,
                "Phone face down while you work",
                "${short * 100 / sessions}% of your app visits lasted under a minute. Those checks are habit, not need.",
            )
        }

        val first = data.mapNotNull { it.firstPickupMinute }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        return Experiment(
            ExperimentKind.MorningFree,
            "One phone-free hour after waking",
            if (first != null) "Your first pickup averaged ${Fmt.clockFull(first)}. Push it back an hour and see how the day feels."
            else "Keep the first hour of the day for yourself and see how the rest of it goes.",
        )
    }
}
