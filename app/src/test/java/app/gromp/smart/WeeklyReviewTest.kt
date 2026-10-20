package app.gromp.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyReviewTest {
    private val min = 60_000L
    private val h = 60 * min
    private val monday = LocalDate.of(2026, 10, 12)
    private val labels = mapOf("insta" to "Instagram", "yt" to "YouTube", "wa" to "WhatsApp")

    private fun week(vararg hours: Long, sessions: Int = 0, short: Int = 0) =
        hours.mapIndexed { i, v -> DayTotal(monday.plusDays(i.toLong()), v * h, 50, short, sessions, 7 * 60 + 10) }

    private fun input(
        days: List<DayTotal> = week(5, 4, 6, 3, 5, 7, 2),
        prev: Long? = 40 * h,
        appsThis: Map<String, Long> = mapOf("insta" to 10 * h, "yt" to 6 * h, "wa" to 3 * h),
        appsPrev: Map<String, Long> = mapOf("insta" to 12 * h, "yt" to 4 * h, "wa" to 3 * h),
        ratings: List<Rating> = emptyList(),
        checkIns: List<CheckInEvent> = emptyList(),
        taper: TaperDecision? = null,
        spots: List<WeakSpot> = emptyList(),
        notif: Pair<String, Int>? = null,
        voice: Voice = Voice.Honest,
    ) = ReviewInput(monday, days, prev, appsThis, appsPrev, ratings, checkIns, 360, taper, spots, notif, listOf(Trade.Reading), voice) { labels[it] ?: it }

    @Test
    fun `totals, headline and cost line`() {
        val r = WeeklyReview.build(input())
        assertEquals(32 * h, r.totalMs)
        assertEquals("8h less than last week (down 20%).", r.headline)
        assertEquals("32h this week. That's 2 full waking days.", r.costLine)
    }

    @Test
    fun `headline per voice and direction`() {
        assertEquals("Up 25%. You went backwards this week.", WeeklyReview.build(input(prev = 32 * h * 100 / 125, voice = Voice.Tough)).headline)
        assertTrue(WeeklyReview.build(input(prev = null)).headline.contains("first"))
        assertEquals("About the same as last week.", WeeklyReview.build(input(prev = 32 * h + 10 * min)).headline)
    }

    @Test
    fun `wins and slips`() {
        val r = WeeklyReview.build(
            input(checkIns = listOf(
                CheckInEvent("Binge", "insta", "closed"), CheckInEvent("Binge", "insta", "continued"),
                CheckInEvent("Binge", "insta", "continued"), CheckInEvent("Intention", "insta", null),
            )),
        )
        assertTrue(r.wins.toString(), r.wins.contains("Calmest day: Sunday, 2h"))
        assertTrue(r.wins.toString(), r.wins.contains("Under your 6h limit on 5 of 7 days"))
        assertTrue(r.wins.toString(), r.wins.contains("8h back in your week"))
        assertTrue(r.wins.toString(), r.wins.contains("Instagram down 2h"))
        assertTrue(r.wins.toString(), r.wins.contains("You closed the app 1 of 3 times Gromp checked in"))
        assertTrue(r.slips.toString(), r.slips.contains("Heaviest day: Saturday, 7h"))
        assertTrue(r.slips.toString(), r.slips.contains("YouTube up 2h"))
        assertTrue(r.slips.toString(), r.slips.contains("You kept going through 2 binge check-ins"))
        assertEquals("Gromp checked in 3 times. You closed the app on 1.", r.checkInLine)
        assertEquals("Under your 6h limit on 5 of 7 days.", r.goalLine)
        assertEquals(3, r.topApps.size)
        assertEquals(AppWeek("insta", 10 * h, 12 * h), r.topApps.first())
    }

    @Test
    fun `taper line reports the new goal`() {
        val r = WeeklyReview.build(input(taper = TaperDecision(360, 330, 6, 7)))
        assertEquals("Next week's limit: 5h 30m, down from 6h.", r.nextGoalLine)
        val same = WeeklyReview.build(input(taper = TaperDecision(360, 360, 2, 7)))
        assertTrue(same.nextGoalLine!!.startsWith("Your limit stays at 6h"))
        assertNull(WeeklyReview.build(input()).nextGoalLine)
    }

    @Test
    fun `experiment priority puts the weak spot first`() {
        val r = WeeklyReview.build(input(spots = listOf(WeakSpot(false, 22, "insta", 40 * min, 0.8f))))
        assertEquals(ExperimentKind.FocusHours, r.experiment.kind)
        assertEquals(22, r.experiment.hour)
        assertEquals("Close your 10 pm window", r.experiment.title)
    }

    @Test
    fun `experiment limits a top app you rarely rate as worth it`() {
        val ratings = List(4) { Rating("insta", it.toLong(), it + 1L, it == 0) } // 25%
        val r = WeeklyReview.build(input(ratings = ratings))
        assertEquals(ExperimentKind.AppLimit, r.experiment.kind)
        assertEquals("insta", r.experiment.pkg)
        // 10h over 7 days = 85 min/day × 0.7 = 59 → 55
        assertEquals(55, r.experiment.limitMin)
        assertNotNull(r.worthLine)
    }

    @Test
    fun `experiment falls back to mute, then phone down, then mornings`() {
        assertEquals(ExperimentKind.MuteNotifications, WeeklyReview.build(input(notif = "wa" to 300)).experiment.kind)
        val shorty = input(days = week(5, 4, 6, 3, 5, 7, 2, sessions = 40, short = 25))
        assertEquals(ExperimentKind.PhoneDown, WeeklyReview.build(shorty).experiment.kind)
        val r = WeeklyReview.build(input())
        assertEquals(ExperimentKind.MorningFree, r.experiment.kind)
        assertTrue(r.experiment.body.contains("7:10"))
    }

    @Test
    fun `empty week doesn't crash`() {
        val r = WeeklyReview.build(input(days = emptyList(), prev = null, appsThis = emptyMap(), appsPrev = emptyMap()))
        assertEquals(0L, r.totalMs)
        assertEquals("No days recorded this week.", r.goalLine)
        assertTrue(r.wins.isEmpty())
        assertEquals(ExperimentKind.MorningFree, r.experiment.kind)
    }

    @Test
    fun `every voice produces a full review`() {
        for (v in Voice.entries) {
            val r = WeeklyReview.build(input(voice = v, taper = TaperDecision(360, 330, 6, 7)))
            assertTrue(r.headline.isNotBlank() && r.costLine.isNotBlank() && r.nextGoalLine!!.isNotBlank())
        }
    }
}
