package app.gromp.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WeakSpotsWorthTest {
    private val min = 60_000L
    private val today = LocalDate.of(2026, 10, 20) // Tuesday
    private val pull = setOf("insta", "yt")

    private fun weekdays(n: Int) = (1..40).map { today.minusDays(it.toLong()) }.filter { !WeakSpots.isWeekend(it) }.take(n)

    @Test
    fun `finds a weeknight Instagram habit`() {
        val rows = weekdays(10).flatMap { d ->
            listOf(
                AppHourRow(d, "insta", 22, 40 * min),
                AppHourRow(d, "maps", 22, 30 * min), // not a pull app
                AppHourRow(d, "yt", 13, 5 * min),
            )
        }
        val spots = WeakSpots.find(rows, { it in pull }, today)
        assertEquals(1, spots.size)
        val s = spots.first()
        assertEquals(false, s.weekend)
        assertEquals(22, s.hour)
        assertEquals("insta", s.pkg)
        assertEquals(40 * min, s.avgMs)
        assertEquals("Weekday nights around 10 pm", WeakSpots.describe(s))
    }

    @Test
    fun `occasional binges are not a habit`() {
        val days = weekdays(10)
        val rows = days.mapIndexed { i, d -> AppHourRow(d, "insta", 22, if (i < 2) 120 * min else 1 * min) } +
            days.map { AppHourRow(it, "yt", 9, 1 * min) }
        // avg = (240 + 8) / 10 = 24.8 min (< 25) and hit rate 20%
        assertTrue(WeakSpots.find(rows, { it in pull }, today).isEmpty())
    }

    @Test
    fun `neighbouring hours collapse to the stronger one`() {
        val rows = weekdays(8).flatMap { d -> listOf(AppHourRow(d, "insta", 22, 40 * min), AppHourRow(d, "insta", 23, 30 * min)) }
        val spots = WeakSpots.find(rows, { it in pull }, today)
        assertEquals(listOf(22), spots.map { it.hour })
    }

    @Test
    fun `needs a few days of history and ignores today`() {
        val rows = weekdays(2).map { AppHourRow(it, "insta", 22, 60 * min) } + AppHourRow(today, "insta", 22, 60 * min)
        assertTrue(WeakSpots.find(rows, { it in pull }, today).isEmpty())
    }

    @Test
    fun `heads-up fires 10 to 45 minutes before, on the right kind of day`() {
        val spot = WeakSpot(false, 22, "insta", 40 * min, 0.8f)
        assertEquals(spot, WeakSpots.upcoming(listOf(spot), LocalDateTime.of(2026, 10, 20, 21, 30)))
        assertNull(WeakSpots.upcoming(listOf(spot), LocalDateTime.of(2026, 10, 20, 21, 55)))
        assertNull(WeakSpots.upcoming(listOf(spot), LocalDateTime.of(2026, 10, 20, 21, 0)))
        // Saturday
        assertNull(WeakSpots.upcoming(listOf(spot), LocalDateTime.of(2026, 10, 24, 21, 30)))
    }

    // ---- Worth ----

    @Test
    fun `scores need three ratings and sort least worth first`() {
        val r = listOf(
            Rating("insta", 1, 2, false), Rating("insta", 3, 4, false), Rating("insta", 5, 6, true),
            Rating("yt", 1, 2, true), Rating("yt", 3, 4, true), Rating("yt", 5, 6, true), Rating("yt", 7, 8, false),
            Rating("x", 1, 2, false),
        )
        val s = Worth.scores(r)
        assertEquals(listOf("insta", "yt"), s.map { it.pkg })
        assertEquals(33, s[0].pct)
        assertEquals(75, s[1].pct)
    }

    @Test
    fun `only finished, long, pull sessions that weren't asked are offered`() {
        val now = 100 * min
        val sessions = listOf(
            LongSession("insta", 10 * min, 30 * min),   // yes
            LongSession("insta", 40 * min, 45 * min),   // too short
            LongSession("maps", 50 * min, 80 * min),    // not pull
            LongSession("yt", 60 * min, 99 * min),      // still settling
            LongSession("yt", 0, 15 * min),             // already handled
        )
        val ask = Worth.toAsk(sessions, { it in pull }, setOf("yt" to 0L), now)
        assertEquals(listOf(LongSession("insta", 10 * min, 30 * min)), ask)
    }

    @Test
    fun `tough voice calls out low worth apps`() {
        val line = Worth.scoreLine(WorthScore("insta", 1, 5), "Instagram", Voice.Tough)
        assertEquals("Instagram was a waste 80% of the time, by your own rating.", line)
    }

    @Test
    fun `merge stitches quick returns to the same app only`() {
        val m = Worth.merge(
            listOf(
                LongSession("insta", 0, 5 * min),
                LongSession("insta", 5 * min + 20_000, 12 * min),  // 20 s gap: same scroll
                LongSession("yt", 12 * min + 5_000, 20 * min),
                LongSession("insta", 20 * min + 5_000, 22 * min),  // different app in between: separate
                LongSession("insta", 30 * min, 40 * min),          // 8 min gap: separate
            ),
        )
        assertEquals(
            listOf(
                LongSession("insta", 0, 12 * min),
                LongSession("yt", 12 * min + 5_000, 20 * min),
                LongSession("insta", 20 * min + 5_000, 22 * min),
                LongSession("insta", 30 * min, 40 * min),
            ),
            m,
        )
    }
}
