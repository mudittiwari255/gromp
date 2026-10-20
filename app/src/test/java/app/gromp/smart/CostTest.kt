package app.gromp.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CostTest {
    private val min = 60_000L
    private val hour = 60 * min

    @Test
    fun `units round down`() {
        assertEquals(3, Cost.units(65 * min, Trade.Reading))
        assertEquals(0, Cost.units(19 * min, Trade.Reading))
    }

    @Test
    fun `phrase handles one, many and too little`() {
        assertEquals("a chapter of a book", Cost.phrase(25 * min, Trade.Reading))
        assertEquals("3 chapters of a book", Cost.phrase(60 * min, Trade.Reading))
        assertEquals("an hour of sleep", Cost.phrase(70 * min, Trade.Sleep))
        assertNull(Cost.phrase(10 * min, Trade.Reading))
    }

    @Test
    fun `pick prefers natural counts and rotates with the seed`() {
        // 3h: Reading = 9 chapters (natural), Films = 1 film (natural), Walking = 6 walks (natural)
        val trades = listOf(Trade.Reading, Trade.Films, Trade.Walking)
        val picks = (0..2).map { Cost.pick(3 * hour, trades, it)!!.first }.toSet()
        assertEquals(trades.toSet(), picks)
        // 10h: Reading = 30 chapters is not natural, Films = 5 is.
        assertEquals(Trade.Films, Cost.pick(10 * hour, listOf(Trade.Reading, Trade.Films), 0)!!.first)
    }

    @Test
    fun `pick falls back to defaults when no trades chosen`() {
        val p = Cost.pick(2 * hour, emptyList(), 0)!!
        assertTrue(p.first in Trade.Defaults)
    }

    @Test
    fun `pick is null for very short time`() {
        assertNull(Cost.pick(5 * min, listOf(Trade.Reading), 0))
    }

    @Test
    fun `today line mentions the time, app and cost in every voice`() {
        for (v in Voice.entries) {
            val line = Cost.todayLine(100 * min, listOf(Trade.Reading), v, 0, "Instagram")
            assertTrue(line, line.contains("1h 40m"))
            assertTrue(line, line.contains("Instagram"))
            assertTrue(line, line.contains("5 chapters of a book"))
        }
    }

    @Test
    fun `today line without a cost still reads well`() {
        for (v in Voice.entries) {
            val line = Cost.todayLine(5 * min, listOf(Trade.Films), v)
            assertTrue(line, line.startsWith("5m on your phone"))
        }
    }

    @Test
    fun `year projection uses waking days`() {
        // 4h a day × 365 / 16h = 91.25
        assertEquals(91, Cost.yearDays(4 * hour))
        assertTrue(Cost.yearLine(4 * hour, Voice.Honest).contains("91 waking days"))
    }

    @Test
    fun `week line describes full waking days`() {
        assertEquals("26h this week. That's a full waking day and 10h.", Cost.weekLine(26 * hour, Voice.Honest))
        assertEquals("32h this week. That's 2 full waking days.", Cost.weekLine(32 * hour, Voice.Honest))
        assertEquals("10h on your phone this week.", Cost.weekLine(10 * hour, Voice.Honest))
    }
}
