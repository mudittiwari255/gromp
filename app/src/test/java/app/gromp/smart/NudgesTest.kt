package app.gromp.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgesTest {
    private val min = 60_000L
    private val t0 = 1_000_000_000L

    // ---- NudgePlanner ----

    @Test
    fun `no session means no check-in`() {
        assertNull(NudgePlanner.next(null, null, false, 20 * min, 0))
    }

    @Test
    fun `binge check-in after the threshold`() {
        val d = NudgePlanner.next(Watch("a", t0), null, false, 20 * min, 0)
        assertEquals(Due(NudgeKind.Binge, t0 + 20 * min), d)
    }

    @Test
    fun `keep going pushes the binge check-in out`() {
        val d = NudgePlanner.next(Watch("a", t0), null, false, 20 * min, t0 + 35 * min)
        assertEquals(Due(NudgeKind.Binge, t0 + 35 * min), d)
    }

    @Test
    fun `drift comes before binge for a short intention`() {
        val d = NudgePlanner.next(Watch("a", t0), IntentionMark("a", IntentKind.Reply, t0), false, 20 * min, 0)
        assertEquals(Due(NudgeKind.Drift, t0 + 10 * min), d)
    }

    @Test
    fun `handled drift falls back to binge`() {
        val d = NudgePlanner.next(Watch("a", t0), IntentionMark("a", IntentKind.Reply, t0), true, 20 * min, 0)
        assertEquals(NudgeKind.Binge, d!!.kind)
    }

    @Test
    fun `intention for another app or an old session is ignored`() {
        assertEquals(NudgeKind.Binge, NudgePlanner.next(Watch("a", t0), IntentionMark("b", IntentKind.Reply, t0), false, 20 * min, 0)!!.kind)
        val stale = IntentionMark("a", IntentKind.Reply, t0 - 30 * min)
        assertEquals(NudgeKind.Binge, NudgePlanner.next(Watch("a", t0), stale, false, 20 * min, 0)!!.kind)
    }

    @Test
    fun `binge can be switched off but drift still works`() {
        assertNull(NudgePlanner.next(Watch("a", t0), null, false, 20 * min, 0, bingeEnabled = false))
        val d = NudgePlanner.next(Watch("a", t0), IntentionMark("a", IntentKind.Bored, t0), false, 20 * min, 0, bingeEnabled = false)
        assertEquals(Due(NudgeKind.Drift, t0 + 15 * min), d)
    }

    @Test
    fun `voice sets binge thresholds`() {
        assertEquals(30, Voice.Gentle.bingeAfterMin)
        assertEquals(20, Voice.Honest.bingeAfterMin)
        assertEquals(15, Voice.Tough.bingeAfterMin)
        assertEquals(Voice.Honest, Voice.from(null))
        assertEquals(Voice.Tough, Voice.from("Tough"))
    }

    // ---- SessionWatch ----

    @Test
    fun `session starts on a pull app and survives a quick return`() {
        val w = SessionWatch()
        assertTrue(w.onForeground("insta", true, t0))
        w.onLeave(t0 + 5 * min)
        assertFalse(w.onForeground("insta", true, t0 + 5 * min + 30_000))
        assertEquals(Watch("insta", t0), w.current)
    }

    @Test
    fun `coming back after the gap is a new session`() {
        val w = SessionWatch()
        w.onForeground("insta", true, t0)
        w.onLeave(t0 + min)
        assertTrue(w.onForeground("insta", true, t0 + 5 * min))
        assertEquals(t0 + 5 * min, w.current!!.start)
    }

    @Test
    fun `switching to a non-pull app clears the session`() {
        val w = SessionWatch()
        w.onForeground("insta", true, t0)
        assertFalse(w.onForeground("maps", false, t0 + min))
        assertNull(w.current)
    }

    @Test
    fun `repeated events for the same app keep the session`() {
        val w = SessionWatch()
        w.onForeground("insta", true, t0)
        assertFalse(w.onForeground("insta", true, t0 + 10 * min))
        assertEquals(t0, w.current!!.start)
    }

    @Test
    fun `screen off ends the session`() {
        val w = SessionWatch()
        w.onForeground("insta", true, t0)
        w.onScreenOff()
        assertNull(w.current)
        assertTrue(w.onForeground("insta", true, t0 + min))
    }

    // ---- LoopDetector ----

    @Test
    fun `hopping between three apps four times is a loop`() {
        val l = LoopDetector()
        assertNull(l.onPullOpen("a", t0))
        assertNull(l.onPullOpen("b", t0 + min))
        assertNull(l.onPullOpen("c", t0 + 2 * min))
        val hit = l.onPullOpen("a", t0 + 3 * min)
        assertNotNull(hit)
        assertEquals(3, hit!!.apps)
        assertEquals(3 * min, hit.spanMs)
    }

    @Test
    fun `two apps back and forth is not a loop`() {
        val l = LoopDetector()
        listOf("a", "b", "a", "b", "a").forEachIndexed { i, p -> assertNull(l.onPullOpen(p, t0 + i * min)) }
    }

    @Test
    fun `opens spread over more than ten minutes don't count`() {
        val l = LoopDetector()
        listOf("a", "b", "c", "a").forEachIndexed { i, p -> assertNull(l.onPullOpen(p, t0 + i * 6 * min)) }
    }

    @Test
    fun `cooldown stops repeated loop alerts`() {
        val l = LoopDetector()
        listOf("a", "b", "c").forEachIndexed { i, p -> l.onPullOpen(p, t0 + i * min) }
        assertNotNull(l.onPullOpen("a", t0 + 3 * min))
        listOf("b", "c", "a").forEachIndexed { i, p -> l.onPullOpen(p, t0 + (4 + i) * min) }
        assertNull(l.onPullOpen("b", t0 + 7 * min))
        // After the cooldown a new loop is reported again.
        listOf("a", "b", "c").forEachIndexed { i, p -> l.onPullOpen(p, t0 + (20 + i) * min) }
        assertNotNull(l.onPullOpen("a", t0 + 23 * min))
    }

    // ---- Copy ----

    @Test
    fun `every check-in has words in every voice`() {
        for (v in Voice.entries) {
            val all = listOf(
                Copy.intention("Instagram", v),
                Copy.binge("Instagram", 20, "a workout", v),
                Copy.binge("Instagram", 20, null, v),
                Copy.loop(3, 4 * min, v),
            ) + IntentKind.entries.map { Copy.drift("Instagram", it, 12, v) }
            all.forEach { c ->
                assertTrue(c.title.isNotBlank() && c.body.isNotBlank() && c.leave.isNotBlank() && c.stay.isNotBlank())
            }
        }
    }

    @Test
    fun `binge copy includes the cost when there is one`() {
        assertTrue(Copy.binge("Instagram", 20, "a workout", Voice.Honest).body.contains("a workout"))
        assertTrue(Copy.binge("Instagram", 20, "a workout", Voice.Tough).body.contains("a workout"))
    }
}
