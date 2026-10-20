package app.gromp.lock

import app.gromp.data.db.LockRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockDeciderTest {
    private val now = 1_000_000_000L
    private val min = 60_000L

    private fun decide(
        rule: LockRule?, used: Long = 0, focus: Boolean = false,
        grants: GrantView = GrantView(), onOpen: Boolean = true,
    ) = LockDecider.decide(rule, used, focus, 420, grants, now, onOpen)

    @Test
    fun `no rule always allows`() {
        assertEquals(Decision.Allow(null), decide(null, used = 999 * min, focus = true))
    }

    @Test
    fun `pause shows on open only`() {
        val r = LockRule("a", pauseEnabled = true)
        assertEquals(Decision.Pause, decide(r))
        assertTrue(decide(r, onOpen = false) is Decision.Allow)
    }

    @Test
    fun `pause is skipped right after passing it`() {
        val r = LockRule("a", pauseEnabled = true)
        assertTrue(decide(r, grants = GrantView(pausePassedUntil = now + min)) is Decision.Allow)
    }

    @Test
    fun `limit reached blocks, and recheck fires when it would be reached`() {
        val r = LockRule("a", dailyLimitMin = 30)
        assertEquals(Decision.Limit(30, 31 * min), decide(r, used = 31 * min))
        assertEquals(Decision.Allow(10 * min), decide(r, used = 20 * min))
    }

    @Test
    fun `snoozed limit allows until the snooze ends`() {
        val r = LockRule("a", dailyLimitMin = 30, pauseEnabled = true)
        val d = decide(r, used = 40 * min, grants = GrantView(limitSnoozeUntil = now + 5 * min))
        assertEquals(Decision.Allow(5 * min), d)
    }

    @Test
    fun `focus beats limit and pause`() {
        val r = LockRule("a", dailyLimitMin = 30, pauseEnabled = true, focusBlocked = true)
        assertEquals(Decision.Focus(420), decide(r, used = 40 * min, focus = true))
    }

    @Test
    fun `focus rule rechecks every minute outside focus hours`() {
        val r = LockRule("a", focusBlocked = true)
        assertEquals(Decision.Allow(LockDecider.RECHECK_MS), decide(r, focus = false))
    }

    @Test
    fun `category limit applies even without an app rule`() {
        val cat = CategoryLimit("Social", 60, 61 * min)
        assertEquals(Decision.Limit(60, 61 * min, "Social"), LockDecider.decide(null, 0, false, 420, GrantView(), now, true, cat))
    }

    @Test
    fun `category limit rechecks when the shared time would run out`() {
        val cat = CategoryLimit("Social", 60, 45 * min)
        assertEquals(Decision.Allow(15 * min), LockDecider.decide(null, 0, false, 420, GrantView(), now, true, cat))
    }

    @Test
    fun `snoozed category lets the app through and skips the pause`() {
        val cat = CategoryLimit("Social", 60, 70 * min, snoozeUntil = now + 5 * min)
        val r = LockRule("a", pauseEnabled = true)
        assertEquals(Decision.Allow(5 * min), LockDecider.decide(r, 0, false, 420, GrantView(), now, true, cat))
    }

    @Test
    fun `app's own limit is reported before the category limit`() {
        val cat = CategoryLimit("Social", 60, 70 * min)
        val r = LockRule("a", dailyLimitMin = 30)
        assertEquals(Decision.Limit(30, 40 * min), LockDecider.decide(r, 40 * min, false, 420, GrantView(), now, true, cat))
    }
}
