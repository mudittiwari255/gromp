package app.gromp.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusScheduleTest {
    private fun at(h: Int, m: Int = 0) = h * 60 + m

    @Test
    fun `daytime window`() {
        val f = FocusSchedule(enabled = true, startMinute = at(9), endMinute = at(17))
        assertTrue(f.isActive(2, at(10), 1))
        assertFalse(f.isActive(2, at(17), 1))
        assertFalse(f.isActive(2, at(8, 59), 1))
    }

    @Test
    fun `overnight window spans midnight`() {
        val f = FocusSchedule(enabled = true, startMinute = at(22, 30), endMinute = at(7))
        assertTrue(f.isActive(2, at(23), 1))
        assertTrue(f.isActive(3, at(6, 30), 2))
        assertFalse(f.isActive(3, at(7, 30), 2))
        assertFalse(f.isActive(3, at(21), 2))
    }

    @Test
    fun `overnight morning belongs to the previous day's schedule`() {
        // Only Monday (bit 0) is on: Monday 23:00 and Tuesday 06:00 are active, Monday 06:00 is not.
        val f = FocusSchedule(enabled = true, startMinute = at(22), endMinute = at(7), days = 0b0000001)
        assertTrue(f.isActive(1, at(23), 7))
        assertTrue(f.isActive(2, at(6), 1))
        assertFalse(f.isActive(1, at(6), 7))
    }

    @Test
    fun `disabled schedule is never active`() {
        assertFalse(FocusSchedule(enabled = false).isActive(1, at(23), 7))
    }
}
