package app.gromp.data

import android.content.Context
import androidx.core.content.edit
import app.gromp.smart.Trade
import app.gromp.smart.Voice
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

/** Everything the smart features can be tuned with, in one place. */
data class SmartSettings(
    val voice: Voice = Voice.Honest,
    /** Things you'd rather do; empty until chosen. */
    val trades: List<Trade> = emptyList(),
    /** Categories whose apps count as "pull" apps for check-ins. */
    val pullCategories: Set<Category> = setOf(Category.Social, Category.Video, Category.Games),
    val intentions: Boolean = true,
    val bingeChecks: Boolean = true,
    val loopChecks: Boolean = true,
    val worthPrompts: Boolean = true,
    val headsUps: Boolean = true,
    val weeklyReview: Boolean = true,
    val taper: Boolean = false,
    /** Taper never goes below this, in minutes. */
    val taperFloorMin: Int = 90,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("gromp", Context.MODE_PRIVATE)

    private val _goal = MutableStateFlow(sp.getInt(K_GOAL, 240))
    val dailyGoalMin: StateFlow<Int> = _goal.asStateFlow()

    private val _focus = MutableStateFlow(readFocus())
    val focus: StateFlow<FocusSchedule> = _focus.asStateFlow()

    private val _smart = MutableStateFlow(readSmart())
    val smart: StateFlow<SmartSettings> = _smart.asStateFlow()

    fun updateSmart(change: (SmartSettings) -> SmartSettings) {
        val next = change(_smart.value)
        sp.edit {
            putString(K_VOICE, next.voice.name)
            putStringSet(K_TRADES, next.trades.map { it.name }.toSet())
            putString(K_TRADE_ORDER, next.trades.joinToString(",") { it.name })
            putStringSet(K_PULL, next.pullCategories.map { it.name }.toSet())
            putBoolean(K_INTENT, next.intentions)
            putBoolean(K_BINGE, next.bingeChecks)
            putBoolean(K_LOOP, next.loopChecks)
            putBoolean(K_WORTH, next.worthPrompts)
            putBoolean(K_HEADS, next.headsUps)
            putBoolean(K_REVIEW, next.weeklyReview)
            putBoolean(K_TAPER, next.taper)
            putInt(K_TAPER_FLOOR, next.taperFloorMin)
        }
        // Turning taper on starts judging from next week, not the week already under way.
        if (next.taper && !_smart.value.taper) lastTaperWeek = app.gromp.smart.Taper.weekKey(java.time.LocalDate.now())
        _smart.value = next
    }

    private fun readSmart(): SmartSettings {
        val d = SmartSettings()
        val order = sp.getString(K_TRADE_ORDER, null)?.split(',')?.filter { it.isNotBlank() }
        return SmartSettings(
            voice = Voice.from(sp.getString(K_VOICE, null)),
            trades = Trade.parse(order ?: sp.getStringSet(K_TRADES, emptySet()).orEmpty().toList()),
            pullCategories = sp.getStringSet(K_PULL, null)?.mapNotNull { n -> Category.entries.firstOrNull { it.name == n } }?.toSet()
                ?: d.pullCategories,
            intentions = sp.getBoolean(K_INTENT, d.intentions),
            bingeChecks = sp.getBoolean(K_BINGE, d.bingeChecks),
            loopChecks = sp.getBoolean(K_LOOP, d.loopChecks),
            worthPrompts = sp.getBoolean(K_WORTH, d.worthPrompts),
            headsUps = sp.getBoolean(K_HEADS, d.headsUps),
            weeklyReview = sp.getBoolean(K_REVIEW, d.weeklyReview),
            taper = sp.getBoolean(K_TAPER, d.taper),
            taperFloorMin = sp.getInt(K_TAPER_FLOOR, d.taperFloorMin),
        )
    }

    /** Last ISO week the taper was evaluated for, e.g. "2026-W43". */
    var lastTaperWeek: String?
        get() = sp.getString(K_TAPER_WEEK, null)
        set(v) = sp.edit { putString(K_TAPER_WEEK, v) }

    /** The last taper change, shown on Today: "6h → 5h 35m". */
    var lastTaperChange: String?
        get() = sp.getString(K_TAPER_CHANGE, null)
        set(v) = sp.edit { putString(K_TAPER_CHANGE, v) }

    /** Week key of the last Sunday review notification. */
    var lastReviewNotified: String?
        get() = sp.getString(K_REVIEW_WEEK, null)
        set(v) = sp.edit { putString(K_REVIEW_WEEK, v) }

    /** "2026-10-20:22" style key of the last heads-up, so each weak spot is announced once a day. */
    var lastHeadsUp: String?
        get() = sp.getString(K_HEADS_KEY, null)
        set(v) = sp.edit { putString(K_HEADS_KEY, v) }

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
        _smart.value = SmartSettings()
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
        const val K_VOICE = "voice"
        const val K_TRADES = "trades"
        const val K_TRADE_ORDER = "trade_order"
        const val K_PULL = "pull_categories"
        const val K_INTENT = "intentions"
        const val K_BINGE = "binge_checks"
        const val K_LOOP = "loop_checks"
        const val K_WORTH = "worth_prompts"
        const val K_HEADS = "heads_ups"
        const val K_REVIEW = "weekly_review"
        const val K_TAPER = "taper"
        const val K_TAPER_FLOOR = "taper_floor"
        const val K_TAPER_WEEK = "taper_week"
        const val K_TAPER_CHANGE = "taper_change"
        const val K_REVIEW_WEEK = "review_week"
        const val K_HEADS_KEY = "heads_up_key"
    }
}
