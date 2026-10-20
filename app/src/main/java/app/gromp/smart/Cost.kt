package app.gromp.smart

import app.gromp.util.Fmt

/**
 * Turns screen time into what it cost you, in terms of things you said you'd rather do.
 * Pure and deterministic: the same inputs always give the same sentence.
 */
object Cost {
    private const val MIN = 60_000L
    /** Hours awake in a typical day, used for "days of your waking life". */
    const val WAKING_HOURS = 16

    fun units(ms: Long, trade: Trade): Int = (ms / MIN / trade.minutesPerUnit).toInt()

    fun phrase(ms: Long, trade: Trade): String? {
        val n = units(ms, trade)
        return when {
            n < 1 -> null
            n == 1 -> "a ${trade.one}".replace("a hour", "an hour")
            else -> "$n ${trade.many}"
        }
    }

    /**
     * Picks one of [trades] that gives a natural count (1 to 12 units), rotating by [seed]
     * so the same trade isn't used every time. Null if the time is too short for any of them.
     */
    fun pick(ms: Long, trades: List<Trade>, seed: Int = 0): Pair<Trade, String>? {
        val pool = trades.ifEmpty { Trade.Defaults }
        val natural = pool.filter { units(ms, it) in 1..12 }
        val candidates = natural.ifEmpty { pool.filter { units(ms, it) >= 1 } }
        if (candidates.isEmpty()) return null
        val t = candidates[Math.floorMod(seed, candidates.size)]
        return t to phrase(ms, t)!!
    }

    /** One line about today's time, e.g. on the limit screen or the Today card. */
    fun todayLine(ms: Long, trades: List<Trade>, voice: Voice, seed: Int = 0, app: String? = null): String {
        val time = Fmt.duration(ms)
        val where = app ?: "your phone"
        val p = pick(ms, trades, seed)?.second
            ?: return when (voice) {
                Voice.Gentle -> "$time on $where so far. Plenty of day left."
                Voice.Honest -> "$time on $where today."
                Voice.Tough -> "$time on $where already. It adds up faster than you think."
            }
        return when (voice) {
            Voice.Gentle -> "$time on $where so far. That's $p, and there's still time to get some back."
            Voice.Honest -> "$time on $where today. That's $p."
            Voice.Tough -> "$time on $where. You traded $p for this."
        }
    }

    /** Waking days per year at an average daily screen time. */
    fun yearDays(avgDailyMs: Long): Int = (avgDailyMs * 365 / (WAKING_HOURS * 3_600_000L)).toInt()

    fun yearLine(avgDailyMs: Long, voice: Voice): String {
        val days = yearDays(avgDailyMs)
        val avg = Fmt.duration(avgDailyMs)
        return when (voice) {
            Voice.Gentle -> "At $avg a day, that's about $days waking days a year. Every hour you win back counts."
            Voice.Honest -> "At $avg a day, you'll spend $days waking days this year on your phone."
            Voice.Tough -> "$days waking days a year. That's how much of your life $avg a day costs."
        }
    }

    /** "26h this week: 1 full waking day and 10h." */
    fun weekLine(weekMs: Long, voice: Voice): String {
        val hours = weekMs / 3_600_000L
        val days = hours / WAKING_HOURS
        val rest = hours % WAKING_HOURS
        val time = Fmt.duration(weekMs)
        val span = when {
            days == 0L -> null
            rest == 0L -> "${if (days == 1L) "a full waking day" else "$days full waking days"}"
            else -> "${if (days == 1L) "a full waking day" else "$days full waking days"} and ${rest}h"
        }
        return when {
            span == null -> when (voice) {
                Voice.Gentle -> "$time this week. Not even a full day. Nice."
                Voice.Honest -> "$time on your phone this week."
                Voice.Tough -> "$time this week. Keep it under a waking day."
            }
            voice == Voice.Gentle -> "$time this week, about $span. You can bring that down."
            voice == Voice.Honest -> "$time this week. That's $span."
            else -> "$time this week. You handed your phone $span."
        }
    }
}
