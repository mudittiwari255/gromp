package app.gromp.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gromp.AppContainer
import app.gromp.data.db.AppDay
import app.gromp.data.db.AppHour
import app.gromp.data.db.DaySummary
import app.gromp.smart.Taper
import app.gromp.smart.WeakSpots
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The smart repository against a real Room database (Robolectric). */
@RunWith(AndroidJUnit4::class)
class SmartRepositoryTest {
    private lateinit var c: AppContainer
    private val h = 3_600_000L
    private val insta = "com.instagram.android"
    private val today: LocalDate get() = c.repository.today()

    @Before
    fun setup() = runTest {
        c = AppContainer(ApplicationProvider.getApplicationContext())
        c.db.dao().clearAll()
    }

    private suspend fun day(d: LocalDate, ms: Long, pkg: String = insta) {
        c.db.dao().replaceDay(
            DaySummary(d.toString(), ms, 50, 420, ms / 4, pkg, 0, 10, 40, 0),
            listOf(AppDay(d.toString(), pkg, ms, 20, ms / 4, 5, 5, 5, 5)),
            emptyList(),
            listOf(AppHour(d.toString(), pkg, 22, minOf(ms, 40 * 60_000L))),
        )
    }

    @Test
    fun `pull apps follow the chosen categories`() {
        assertTrue(c.smart.isPull(insta))                       // Social
        assertFalse(c.smart.isPull("com.whatsapp"))             // Messaging isn't a pull category by default
        c.prefs.updateSmart { it.copy(pullCategories = it.pullCategories + Category.Messaging) }
        assertTrue(c.smart.isPull("com.whatsapp"))
    }

    @Test
    fun `taper lowers the limit once after a good week`() = runTest {
        Taper.previousWeek(today).forEach { day(it, 3 * h) }
        c.prefs.setDailyGoal(360)
        c.prefs.updateSmart { it.copy(taper = true) }
        c.prefs.lastTaperWeek = "2000-W01" // pretend the last check was long ago

        val d = c.smart.runTaperIfDue()
        assertNotNull(d)
        assertEquals(330, c.prefs.dailyGoalMin.value)
        assertEquals("6h → 5h 30m", c.prefs.lastTaperChange)
        assertEquals(Taper.weekKey(today), c.prefs.lastTaperWeek)
        // Only once per week.
        assertNull(c.smart.runTaperIfDue())
        assertEquals(330, c.prefs.dailyGoalMin.value)
    }

    @Test
    fun `turning taper on waits for next week`() = runTest {
        Taper.previousWeek(today).forEach { day(it, 3 * h) }
        c.prefs.setDailyGoal(360)
        c.prefs.updateSmart { it.copy(taper = true) }
        assertNull(c.smart.runTaperIfDue())
        assertEquals(360, c.prefs.dailyGoalMin.value)
    }

    @Test
    fun `weak spots come from stored hourly data`() = runTest {
        (1..20).map { today.minusDays(it.toLong()) }.forEach { day(it, 2 * h) }
        val spots = c.smart.weakSpots()
        assertTrue(spots.isNotEmpty())
        assertEquals(22, spots.first().hour)
        assertEquals(insta, spots.first().pkg)
        assertTrue(WeakSpots.describe(spots.first()).endsWith("around 10 pm"))
    }

    @Test
    fun `review of last week adds up stored days and ratings`() = runTest {
        val lastMonday = Taper.mondayOf(today).minusDays(7)
        (0..6).forEach { day(lastMonday.plusDays(it.toLong()), (2 + it % 2) * h) }
        c.smart.rate(insta, lastMonday.atStartOfDay().plusHours(22).toEpochMilli(), lastMonday.atStartOfDay().plusHours(23).toEpochMilli(), false)
        val r = c.smart.review(lastMonday)
        assertEquals((2 + 3 + 2 + 3 + 2 + 3 + 2) * h, r.totalMs)
        assertEquals(lastMonday, r.weekStart)
        assertEquals(insta, r.topApps.first().pkg)
        assertTrue(r.headline.isNotBlank())
    }

    @Test
    fun `events and outcomes are logged`() = runTest {
        val id = c.smart.log(insta, Ev.BINGE, detail = "20")
        c.smart.setOutcome(id, Ev.CLOSED)
        val e = c.db.dao().allEvents().single()
        assertEquals(Ev.BINGE, e.kind)
        assertEquals(Ev.CLOSED, e.outcome)
    }

    private fun java.time.LocalDateTime.toEpochMilli() = atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
