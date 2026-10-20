package app.gromp.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTest {
    private val sunday7pm = LocalDateTime.of(2026, 10, 25, 19, 5)

    @Test
    fun `review is due Sunday evening once per week`() {
        assertTrue(Schedule.reviewDue(sunday7pm, null))
        assertTrue(Schedule.reviewDue(sunday7pm, "2026-W42"))
        assertFalse(Schedule.reviewDue(sunday7pm, "2026-W43"))
        assertFalse(Schedule.reviewDue(sunday7pm.withHour(18), null))
        assertFalse(Schedule.reviewDue(sunday7pm.minusDays(1), null))
    }

    @Test
    fun `heads-up key is per day and hour`() {
        val s = WeakSpot(false, 22, "insta", 1, 1f)
        assertEquals("2026-10-25:22", Schedule.headsUpKey(sunday7pm, s))
    }

    @Test
    fun `quiet before 7 am`() {
        assertTrue(Schedule.quietHours(sunday7pm.withHour(6)))
        assertFalse(Schedule.quietHours(sunday7pm.withHour(7)))
    }
}
