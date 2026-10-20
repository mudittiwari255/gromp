package app.gromp.smart

import java.time.DayOfWeek
import java.time.LocalDateTime

/** When background notifications are due. Pure, so the timing rules can be tested. */
object Schedule {
    /** Sunday review goes out once, from 7 pm on Sunday. */
    const val REVIEW_HOUR = 19

    fun reviewDue(now: LocalDateTime, lastNotifiedWeek: String?): Boolean =
        now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= REVIEW_HOUR && lastNotifiedWeek != Taper.weekKey(now.toLocalDate())

    /** Key that makes each weak spot announce at most once per day. */
    fun headsUpKey(now: LocalDateTime, spot: WeakSpot): String = "${now.toLocalDate()}:${spot.hour}"

    /** No nudging notifications during the night unless it's the weak spot itself. */
    fun quietHours(now: LocalDateTime): Boolean = now.hour < 7
}
