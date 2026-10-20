package app.gromp.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Focus hours: chosen apps are blocked between [startMinute] and [endMinute] on [days]. */
data class FocusSchedule(
    val enabled: Boolean = false,
    /** Minutes after midnight. May be greater than [endMinute] for overnight windows (22:30 → 07:00). */
    val startMinute: Int = 22 * 60 + 30,
    val endMinute: Int = 7 * 60,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val days: Int = 0b1111111,
) {
    fun isActive(dayOfWeekIso: Int, minuteOfDay: Int, previousDayOfWeekIso: Int): Boolean {
        if (!enabled) return false
        fun on(d: Int) = days and (1 shl (d - 1)) != 0
        return if (startMinute <= endMinute) {
            on(dayOfWeekIso) && minuteOfDay in startMinute until endMinute
        } else {
            (on(dayOfWeekIso) && minuteOfDay >= startMinute) || (on(previousDayOfWeekIso) && minuteOfDay < endMinute)
        }
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("gromp", Context.MODE_PRIVATE)

    private val _goal = MutableStateFlow(sp.getInt(K_GOAL, 240))
    val dailyGoalMin: StateFlow<Int> = _goal.asStateFlow()

    private val _focus = MutableStateFlow(readFocus())
    val focus: StateFlow<FocusSchedule> = _focus.asStateFlow()

    var onboarded: Boolean
        get() = sp.getBoolean(K_ONBOARDED, false)
        set(v) = sp.edit { putBoolean(K_ONBOARDED, v) }

    var locksDisclosureAccepted: Boolean
        get() = sp.getBoolean(K_DISCLOSURE, false)
        set(v) = sp.edit { putBoolean(K_DISCLOSURE, v) }

    var installedAt: Long
        get() = sp.getLong(K_INSTALLED, 0L).takeIf { it > 0 } ?: System.currentTimeMillis().also { installedAt = it }
        set(v) = sp.edit { putLong(K_INSTALLED, v) }

    fun setDailyGoal(min: Int) {
        sp.edit { putInt(K_GOAL, min) }
        _goal.value = min
    }

    fun setFocus(f: FocusSchedule) {
        sp.edit {
            putBoolean(K_F_ON, f.enabled)
            putInt(K_F_START, f.startMinute)
            putInt(K_F_END, f.endMinute)
            putInt(K_F_DAYS, f.days)
        }
        _focus.value = f
    }

    private fun readFocus() = FocusSchedule(
        enabled = sp.getBoolean(K_F_ON, false),
        startMinute = sp.getInt(K_F_START, 22 * 60 + 30),
        endMinute = sp.getInt(K_F_END, 7 * 60),
        days = sp.getInt(K_F_DAYS, 0b1111111),
    )

    fun clear() {
        val onboardedNow = onboarded
        sp.edit { clear(); putBoolean(K_ONBOARDED, onboardedNow) }
        _goal.value = 240
        _focus.value = FocusSchedule()
    }

    private companion object {
        const val K_GOAL = "daily_goal_min"
        const val K_ONBOARDED = "onboarded"
        const val K_DISCLOSURE = "locks_disclosure"
        const val K_INSTALLED = "installed_at"
        const val K_F_ON = "focus_on"
        const val K_F_START = "focus_start"
        const val K_F_END = "focus_end"
        const val K_F_DAYS = "focus_days"
    }
}
