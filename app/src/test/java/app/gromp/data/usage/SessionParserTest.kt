package app.gromp.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionParserTest {
    private val s = 1_000L
    private val m = 60_000L

    private fun ev(pkg: String, t: Long, k: EvKind) = Ev(pkg, t, k)

    @Test
    fun `simple open and close`() {
        val r = SessionParser.parse(
            listOf(ev("a", 0, EvKind.RESUMED), ev("a", 5 * m, EvKind.PAUSED), ev("home", 5 * m + s, EvKind.RESUMED)),
            rangeStart = 0, rangeEnd = 60 * m, ignored = setOf("home"),
        )
        assertEquals(listOf(Session("a", 0, 5 * m)), r.sessions)
    }

    @Test
    fun `activity switch inside one app stays one session`() {
        val r = SessionParser.parse(
            listOf(
                ev("a", 0, EvKind.RESUMED),
                ev("a", 2 * m, EvKind.PAUSED),
                ev("a", 2 * m + 300, EvKind.RESUMED),
                ev("a", 4 * m, EvKind.PAUSED),
                ev("b", 4 * m + 200, EvKind.RESUMED),
                ev("b", 6 * m, EvKind.PAUSED),
            ),
            rangeStart = 0, rangeEnd = 7 * m,
        )
        assertEquals(listOf(Session("a", 0, 4 * m), Session("b", 4 * m + 200, 6 * m)), r.sessions)
    }

    @Test
    fun `same app resumed after a long gap is a new open`() {
        val r = SessionParser.parse(
            listOf(ev("a", 0, EvKind.RESUMED), ev("a", m, EvKind.PAUSED), ev("a", 10 * m, EvKind.RESUMED), ev("a", 11 * m, EvKind.PAUSED)),
            rangeStart = 0, rangeEnd = 20 * m,
        )
        assertEquals(2, r.sessions.size)
        assertEquals(2 * m, r.sessions.sumOf { it.duration })
    }

    @Test
    fun `screen off ends the session`() {
        val r = SessionParser.parse(
            listOf(ev("a", 0, EvKind.RESUMED), ev("", 3 * m, EvKind.SCREEN_OFF)),
            rangeStart = 0, rangeEnd = 60 * m,
        )
        assertEquals(listOf(Session("a", 0, 3 * m)), r.sessions)
    }

    @Test
    fun `session crossing the range start is clipped`() {
        val r = SessionParser.parse(
            listOf(ev("a", -10 * m, EvKind.RESUMED), ev("a", 5 * m, EvKind.PAUSED), ev("b", 5 * m + s, EvKind.RESUMED)),
            rangeStart = 0, rangeEnd = 6 * m,
        )
        assertEquals(Session("a", 0, 5 * m), r.sessions.first())
        assertEquals(Session("b", 5 * m + s, 6 * m), r.sessions.last())
    }

    @Test
    fun `open session at the end runs until range end`() {
        val r = SessionParser.parse(listOf(ev("a", 10 * m, EvKind.RESUMED)), rangeStart = 0, rangeEnd = 15 * m)
        assertEquals(listOf(Session("a", 10 * m, 15 * m)), r.sessions)
    }

    @Test
    fun `pickups prefer unlocks and ignore events before range`() {
        val r = SessionParser.parse(
            listOf(
                ev("", -m, EvKind.UNLOCK),
                ev("", m, EvKind.SCREEN_ON), ev("", m + s, EvKind.UNLOCK),
                ev("", 9 * m, EvKind.SCREEN_ON), ev("", 9 * m + s, EvKind.UNLOCK),
            ),
            rangeStart = 0, rangeEnd = 60 * m,
        )
        assertEquals(2, r.pickups)
        assertEquals(m + s, r.firstPickup)
    }

    @Test
    fun `no pickups gives null first pickup`() {
        val r = SessionParser.parse(emptyList(), 0, 60 * m)
        assertEquals(0, r.pickups)
        assertNull(r.firstPickup)
    }

    @Test
    fun `hour split divides sessions across buckets`() {
        val h = 3_600_000L
        val hours = Aggregator.byHour(listOf(Session("a", h - 10 * m, h + 20 * m)), dayStart = 0)
        assertEquals(10 * m, hours[0])
        assertEquals(20 * m, hours[1])
    }

    @Test
    fun `app totals bucket session lengths`() {
        val t = Aggregator.byApp(
            listOf(Session("a", 0, 30 * s), Session("a", m, 4 * m), Session("a", 10 * m, 30 * m), Session("b", 0, 7 * m)),
        )
        val a = t.first { it.pkg == "a" }
        assertEquals(3, a.opens)
        assertEquals(1, a.under1)
        assertEquals(1, a.m1to5)
        assertEquals(1, a.over15)
        assertEquals(20 * m, a.longestMs)
        assertEquals(1, t.first { it.pkg == "b" }.m5to15)
    }
}
