package app.gromp.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaperTest {
    private val h = 3_600_000L

    @Test
    fun `good week lowers the goal by about 7 percent, in 5 minute steps`() {
        val week = listOf(5 * h, 5 * h, 4 * h, 5 * h, 3 * h, 7 * h, 5 * h) // 6 of 7 under 6h
        val d = Taper.evaluate(360, 60, week)
        assertTrue(d.changed)
        assertEquals(330, d.newGoalMin) // 360 × 0.93 = 334.8 → 330
        assertEquals(6, d.metDays)
        assertEquals(7, d.dataDays)
    }

    @Test
    fun `bad week keeps the goal`() {
        val week = listOf(7 * h, 7 * h, 5 * h, 8 * h, 7 * h, 6 * h, 5 * h)
        val d = Taper.evaluate(360, 60, week)
        assertFalse(d.changed)
        assertEquals(360, d.newGoalMin)
    }

    @Test
    fun `needs enough days of data`() {
        val week = listOf(1 * h, 1 * h, 1 * h, null, null, null, null)
        assertFalse(Taper.evaluate(360, 60, week).changed)
    }

    @Test
    fun `zero-usage days don't count as data`() {
        val week = listOf(0L, 0L, 0L, 1 * h, 1 * h, 1 * h, null)
        assertFalse(Taper.evaluate(360, 60, week).changed)
    }

    @Test
    fun `never goes below the floor`() {
        val week = List(7) { 30 * 60_000L }
        assertEquals(60, Taper.evaluate(62, 60, week).newGoalMin)
        assertFalse(Taper.evaluate(60, 60, week).changed)
    }

    @Test
    fun `seventy percent pass rate is required`() {
        // 7 days, need ceil(4.9) = 5 met
        val four = listOf(1 * h, 1 * h, 1 * h, 1 * h, 9 * h, 9 * h, 9 * h)
        val five = listOf(1 * h, 1 * h, 1 * h, 1 * h, 1 * h, 9 * h, 9 * h)
        assertFalse(Taper.evaluate(360, 60, four).changed)
        assertTrue(Taper.evaluate(360, 60, five).changed)
    }

    @Test
    fun `week keys and previous week`() {
        val tue = LocalDate.of(2026, 10, 20)
        assertEquals("2026-W43", Taper.weekKey(tue))
        assertEquals(LocalDate.of(2026, 10, 19), Taper.mondayOf(tue))
        val prev = Taper.previousWeek(tue)
        assertEquals(LocalDate.of(2026, 10, 12), prev.first())
        assertEquals(LocalDate.of(2026, 10, 18), prev.last())
        assertEquals("2021-W01", Taper.weekKey(LocalDate.of(2021, 1, 4)))
        assertEquals("2020-W53", Taper.weekKey(LocalDate.of(2021, 1, 3)))
    }
}
