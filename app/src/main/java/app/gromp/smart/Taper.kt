package app.gromp.smart

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.max

data class TaperDecision(
    val oldGoalMin: Int,
    val newGoalMin: Int,
    val metDays: Int,
    val dataDays: Int,
) {
    val changed: Boolean get() = newGoalMin != oldGoalMin
}

/**
 * Goals that tighten as you improve: after a week where you stayed under your limit on most days,
 * the limit drops by about 7%. A bad week never makes it stricter, and it never goes below the floor.
 */
object Taper {
    const val STEP = 0.93
    /** Need data for at least this many days of the week to judge it. */
    const val MIN_DAYS = 4
    /** Share of those days that must be under the limit. */
    const val PASS_RATE = 0.7

    fun evaluate(goalMin: Int, floorMin: Int, weekTotalsMs: List<Long?>): TaperDecision {
        val data = weekTotalsMs.filterNotNull().filter { it > 0 }
        val met = data.count { it < goalMin * 60_000L }
        val same = TaperDecision(goalMin, goalMin, met, data.size)
        if (data.size < MIN_DAYS || met < ceil(data.size * PASS_RATE).toInt()) return same
        val next = max(floorMin, (goalMin * STEP).toInt() / 5 * 5)
        return if (next >= goalMin) same else TaperDecision(goalMin, next, met, data.size)
    }

    /** ISO week key, e.g. "2026-W43". */
    fun weekKey(d: LocalDate): String =
        "%d-W%02d".format(d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    fun mondayOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The Monday-to-Sunday dates of the week before [today]'s week. */
    fun previousWeek(today: LocalDate): List<LocalDate> {
        val start = mondayOf(today).minusDays(7)
        return (0..6).map { start.plusDays(it.toLong()) }
    }
}
