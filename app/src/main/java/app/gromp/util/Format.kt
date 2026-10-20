package app.gromp.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Fmt {
    private const val MIN = 60_000L

    /** "3h 42m", "52m", "0m". */
    fun duration(ms: Long): String {
        val totalMin = (ms / MIN.toDouble()).roundToLong()
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** "3m 24s" for short spans. */
    fun durationSeconds(ms: Long): String {
        val s = ms / 1000
        return if (s < 60) "${s}s" else "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s"
    }

    /** "18 min" — used in comparisons. */
    fun minutes(ms: Long): String {
        val m = (abs(ms) / MIN.toDouble()).roundToLong()
        return if (m >= 90) duration(abs(ms)) else "$m min"
    }

    private val clock = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())
    private val ampm = DateTimeFormatter.ofPattern("a", Locale.getDefault())

    fun clock(minuteOfDay: Int): String = LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60).format(clock)
    fun amPm(minuteOfDay: Int): String = LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60).format(ampm).lowercase()
    fun clockFull(minuteOfDay: Int): String = "${clock(minuteOfDay)} ${amPm(minuteOfDay)}"

    fun timeOf(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
        return clockFull(t.hour * 60 + t.minute)
    }

    private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val weekdayLong = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    private val month = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

    fun longDate(d: LocalDate): String = d.format(longDate)
    fun shortDate(d: LocalDate): String = d.format(shortDate)
    fun weekday(d: LocalDate): String = d.format(weekday)
    fun weekdayLong(d: LocalDate): String = d.format(weekdayLong)
    fun month(d: LocalDate): String = d.format(month)

    fun greeting(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Winding down"
    }
}
