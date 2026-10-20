package app.gromp.data

import android.content.Context
import app.gromp.data.db.AppDay
import app.gromp.data.db.AppHour
import app.gromp.data.db.CategoryRule
import app.gromp.data.db.DaySummary
import app.gromp.data.db.GrompDao
import app.gromp.data.db.HourUsage
import app.gromp.data.db.LockRule
import app.gromp.data.usage.Aggregator
import app.gromp.data.usage.AppTotals
import app.gromp.data.usage.ParsedRange
import app.gromp.data.usage.UsageReader
import app.gromp.util.Fmt
import app.gromp.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class DaySnapshot(
    val day: LocalDate,
    val parsed: ParsedRange,
    val apps: List<AppTotals>,
    val hours: LongArray,
) {
    val totalMs: Long get() = apps.sumOf { it.ms }
}

// ---------- Screen models ----------

data class AppRow(val pkg: String, val ms: Long, val usualMs: Long?)

data class TodayData(
    val date: LocalDate,
    val nowMinute: Int,
    val totalMs: Long,
    val goalMs: Long,
    val usualSoFarMs: Long?,
    val usualLabel: String,
    val pickups: Int,
    val avgPickups: Int?,
    val firstPickupMinute: Int?,
    val usualFirstPickupMinute: Int?,
    val longestMs: Long,
    val longestPkg: String?,
    val longestStart: Long?,
    val notifications: Int?,
    val topNotifPkg: String?,
    val topNotifCount: Int,
    val hourly: LongArray,
    val apps: List<AppRow>,
    val insight: Insight?,
)

enum class TrendRange { Week, Month, Year }

data class TrendBar(val label: String, val ms: Long, val highlight: Boolean, val hasData: Boolean)
data class HeatRow(val label: String, val hours: LongArray, val futureFrom: Int?)
data class CategoryShare(val category: Category, val pct: Int, val msPerDay: Long)

data class TrendsData(
    val range: TrendRange,
    val bars: List<TrendBar>,
    val avgMs: Long,
    val prevAvgMs: Long?,
    val calmest: Pair<String, Long>?,
    val heaviest: Pair<String, Long>?,
    val avgPickups: Int,
    val prevAvgPickups: Int?,
    val heat: List<HeatRow>,
    val categories: List<CategoryShare>,
    val daysWithData: Int,
)

data class AppDetailData(
    val pkg: String,
    val category: Category,
    val todayMs: Long,
    val shareOfToday: Int,
    val usualMs: Long?,
    val opens: Int,
    val avgSessionMs: Long,
    val longestMs: Long,
    val buckets: IntArray,
    val series: List<Long>,
    val seriesStart: LocalDate,
    val avgMs: Long,
    val rule: LockRule?,
)

/** The single place that turns Android usage events into stored, local history. */
class Repository(
    private val context: Context,
    private val dao: GrompDao,
    private val catalog: AppCatalog,
    private val prefs: Prefs,
) {
    private val reader = UsageReader(context)
    private val mutex = Mutex()
    @Volatile private var todayCache: Pair<Long, DaySnapshot>? = null

    private val zone: ZoneId get() = ZoneId.systemDefault()
    fun today(): LocalDate = LocalDate.now(zone)
    private fun key(d: LocalDate) = d.toString()
    private fun startOf(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    // ---------- Snapshots ----------

    /** Reads one day from Android and stores it. Past days are only overwritten with fuller data. */
    suspend fun snapshot(day: LocalDate): DaySnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val start = startOf(day)
            val end = minOf(startOf(day.plusDays(1)), System.currentTimeMillis())
            val parsed = reader.read(start, end, catalog.ignoredForStats())
            val apps = Aggregator.byApp(parsed.sessions)
            val hours = Aggregator.byHour(parsed.sessions, start)
            val snap = DaySnapshot(day, parsed, apps, hours)

            val isToday = day == today()
            val existing = dao.summaries(key(day), key(day)).firstOrNull()
            val fuller = existing == null || snap.totalMs >= existing.totalMs
            if (Permissions.hasUsageAccess(context) && (isToday || (snap.totalMs > 0 && fuller))) {
                store(snap)
            }
            if (isToday) todayCache = System.currentTimeMillis() to snap
            snap
        }
    }

    private suspend fun store(s: DaySnapshot) {
        val k = key(s.day)
        val longest = s.parsed.sessions.maxByOrNull { it.duration }
        val first = s.parsed.firstPickup?.let {
            val t = Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
            t.hour * 60 + t.minute
        }
        dao.replaceDay(
            DaySummary(
                day = k,
                totalMs = s.totalMs,
                pickups = s.parsed.pickups,
                firstPickupMinute = first,
                longestSessionMs = longest?.duration ?: 0,
                longestPkg = longest?.pkg,
                longestStart = longest?.start,
                shortSessions = s.parsed.sessions.count { it.duration < 60_000 },
                sessions = s.parsed.sessions.size,
                updatedAt = System.currentTimeMillis(),
            ),
            s.apps.map { AppDay(k, it.pkg, it.ms, it.opens, it.longestMs, it.under1, it.m1to5, it.m5to15, it.over15) },
            s.hours.mapIndexed { h, ms -> HourUsage(k, h, ms) }.filter { it.ms > 0 },
            Aggregator.byAppHour(s.parsed.sessions, startOf(s.day)).flatMap { (pkg, hours) ->
                hours.mapIndexed { h, ms -> AppHour(k, pkg, h, ms) }.filter { it.ms > 0 }
            },
        )
    }

    /** Refresh today plus [daysBack] earlier days (Android only keeps detailed events for about a week). */
    suspend fun refresh(daysBack: Int = 1) {
        if (!Permissions.hasUsageAccess(context)) return
        val t = today()
        for (i in daysBack downTo 0) snapshot(t.minusDays(i.toLong()))
    }

    suspend fun todayLive(maxAgeMs: Long = 15_000): DaySnapshot {
        val cached = todayCache
        if (cached != null && System.currentTimeMillis() - cached.first < maxAgeMs && cached.second.day == today()) {
            return cached.second
        }
        return snapshot(today())
    }

    /** Milliseconds spent in [pkg] today, fresh enough for lock decisions. */
    suspend fun todayMsFor(pkg: String): Long = todayLive(maxAgeMs = 10_000).apps.firstOrNull { it.pkg == pkg }?.ms ?: 0

    // ---------- Today ----------

    suspend fun loadToday(): TodayData = withContext(Dispatchers.IO) {
        val snap = todayLive()
        val day = snap.day
        val now = LocalDateTime.now(zone)
        val nowMinute = now.hour * 60 + now.minute

        val histFrom = day.minusDays(28)
        val histTo = day.minusDays(1)
        val summaries = dao.summaries(key(histFrom), key(histTo)).filter { it.totalMs > 0 }
        val hours = dao.hours(key(histFrom), key(histTo)).groupBy { it.day }

        val sameWeekday = summaries.filter { LocalDate.parse(it.day).dayOfWeek == day.dayOfWeek }
        val last7 = summaries.filter { LocalDate.parse(it.day) >= day.minusDays(7) }
        val base = sameWeekday.ifEmpty { last7 }
        val usualLabel = if (sameWeekday.isNotEmpty()) "usual ${Fmt.weekdayLong(day)}" else "usual day"

        fun soFar(d: String): Long {
            val byHour = hours[d].orEmpty().associate { it.hour to it.ms }
            var sum = 0L
            for (h in 0 until now.hour) sum += byHour[h] ?: 0
            sum += ((byHour[now.hour] ?: 0) * (now.minute / 60.0)).toLong()
            return sum
        }
        val usualSoFar = base.takeIf { it.isNotEmpty() }?.map { soFar(it.day) }?.average()?.toLong()
        val usualFull = base.takeIf { it.isNotEmpty() }?.map { it.totalMs }?.average()
        val ratio = if (usualFull != null && usualFull > 0 && usualSoFar != null) usualSoFar / usualFull else null

        val appHistory = dao.appDays(key(day.minusDays(7)), key(histTo))
        val histDays = last7.size.coerceAtLeast(1)
        val appUsual = appHistory.groupBy { it.pkg }.mapValues { (_, rows) -> rows.sumOf { it.ms } / histDays }

        val notifs = dao.notifs(key(day), key(day))
        val top = notifs.maxByOrNull { it.count }
        val longest = snap.parsed.sessions.maxByOrNull { it.duration }

        TodayData(
            date = day,
            nowMinute = nowMinute,
            totalMs = snap.totalMs,
            goalMs = prefs.dailyGoalMin.value * 60_000L,
            usualSoFarMs = usualSoFar,
            usualLabel = usualLabel,
            pickups = snap.parsed.pickups,
            avgPickups = last7.takeIf { it.isNotEmpty() }?.map { it.pickups }?.average()?.toInt(),
            firstPickupMinute = snap.parsed.firstPickup?.let {
                val t = Instant.ofEpochMilli(it).atZone(zone).toLocalTime(); t.hour * 60 + t.minute
            },
            usualFirstPickupMinute = last7.mapNotNull { it.firstPickupMinute }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            longestMs = longest?.duration ?: 0,
            longestPkg = longest?.pkg,
            longestStart = longest?.start,
            notifications = if (Permissions.hasNotificationAccess(context)) notifs.sumOf { it.count } else null,
            topNotifPkg = top?.pkg,
            topNotifCount = top?.count ?: 0,
            hourly = snap.hours,
            apps = snap.apps.take(5).map { a ->
                AppRow(a.pkg, a.ms, appUsual[a.pkg]?.let { u -> ratio?.let { (u * it).toLong() } })
            },
            insight = Insights.forToday(snap, usualSoFar, last7),
        )
    }

    // ---------- Trends ----------

    suspend fun loadTrends(range: TrendRange): TrendsData = withContext(Dispatchers.IO) {
        todayLive()
        val t = today()
        val days = when (range) {
            TrendRange.Week -> 7
            TrendRange.Month -> 30
            TrendRange.Year -> 365
        }
        val from = t.minusDays(days - 1L)
        val prevFrom = from.minusDays(days.toLong())
        val sums = dao.summaries(key(from), key(t)).associateBy { it.day }
        val prev = dao.summaries(key(prevFrom), key(from.minusDays(1))).filter { it.totalMs > 0 }
        val withData = sums.values.filter { it.totalMs > 0 }

        val bars = when (range) {
            TrendRange.Year -> (11 downTo 0).map { back ->
                val m = t.withDayOfMonth(1).minusMonths(back.toLong())
                val inMonth = withData.filter { LocalDate.parse(it.day).let { d -> d.year == m.year && d.month == m.month } }
                val avg = if (inMonth.isEmpty()) 0L else inMonth.sumOf { it.totalMs } / inMonth.size
                TrendBar(Fmt.month(m).take(1), avg, back == 0, inMonth.isNotEmpty())
            }
            else -> (0 until days).map { i ->
                val d = from.plusDays(i.toLong())
                val s = sums[key(d)]
                val label = when {
                    range == TrendRange.Week -> if (d == t) "Today" else Fmt.weekday(d)
                    i % 7 == 0 || d == t -> d.dayOfMonth.toString()
                    else -> ""
                }
                TrendBar(label, s?.totalMs ?: 0, d == t, s != null && s.totalMs > 0)
            }
        }

        val avg = if (withData.isEmpty()) 0L else withData.sumOf { it.totalMs } / withData.size
        val calm = withData.filter { it.day != key(t) }.minByOrNull { it.totalMs }
        val heavy = withData.maxByOrNull { it.totalMs }
        fun name(s: DaySummary) = LocalDate.parse(s.day).let {
            if (range == TrendRange.Week) Fmt.weekday(it) else Fmt.shortDate(it)
        }

        // Heatmap is always the last 7 days.
        val heatFrom = t.minusDays(6)
        val heatHours = dao.hours(key(heatFrom), key(t)).groupBy { it.day }
        val nowHour = LocalDateTime.now(zone).hour
        val heat = (0..6).map { i ->
            val d = heatFrom.plusDays(i.toLong())
            val arr = LongArray(24)
            heatHours[key(d)].orEmpty().forEach { arr[it.hour] = it.ms }
            HeatRow(Fmt.weekday(d), arr, if (d == t) nowHour + 1 else null)
        }

        val apps = dao.appDays(key(from), key(t))
        val byCat = apps.groupBy { catalog.category(it.pkg) }.mapValues { (_, r) -> r.sumOf { it.ms } }
        val catTotal = byCat.values.sum().coerceAtLeast(1L)
        val nDays = withData.size.coerceAtLeast(1)
        val cats = byCat.entries.sortedByDescending { it.value }.map { (c, ms) ->
            CategoryShare(c, ((ms * 100) / catTotal).toInt(), ms / nDays)
        }

        TrendsData(
            range = range,
            bars = bars,
            avgMs = avg,
            prevAvgMs = prev.takeIf { it.isNotEmpty() }?.let { p -> p.sumOf { it.totalMs } / p.size },
            calmest = calm?.let { name(it) to it.totalMs },
            heaviest = heavy?.let { name(it) to it.totalMs },
            avgPickups = if (withData.isEmpty()) 0 else withData.sumOf { it.pickups } / withData.size,
            prevAvgPickups = prev.takeIf { it.isNotEmpty() }?.let { p -> p.sumOf { it.pickups } / p.size },
            heat = heat,
            categories = cats,
            daysWithData = withData.size,
        )
    }

    // ---------- App detail ----------

    suspend fun loadApp(pkg: String): AppDetailData = withContext(Dispatchers.IO) {
        val snap = todayLive()
        val t = snap.day
        val a = snap.apps.firstOrNull { it.pkg == pkg }
        val from = t.minusDays(29)
        val series = dao.appSeries(pkg, key(from), key(t)).associateBy { it.day }
        val values = (0..29).map { i -> series[key(from.plusDays(i.toLong()))]?.ms ?: 0L }
        val hist = dao.summaries(key(from), key(t.minusDays(1))).filter { it.totalMs > 0 }.map { it.day }.toSet()
        val histValues = hist.map { series[it]?.ms ?: 0L }
        val avg = if (histValues.isEmpty()) values.last() else histValues.average().toLong()
        AppDetailData(
            pkg = pkg,
            category = catalog.category(pkg),
            todayMs = a?.ms ?: 0,
            shareOfToday = if (snap.totalMs > 0) (((a?.ms ?: 0) * 100) / snap.totalMs).toInt() else 0,
            usualMs = histValues.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            opens = a?.opens ?: 0,
            avgSessionMs = if (a == null || a.opens == 0) 0 else a.ms / a.opens,
            longestMs = a?.longestMs ?: 0,
            buckets = intArrayOf(a?.under1 ?: 0, a?.m1to5 ?: 0, a?.m5to15 ?: 0, a?.over15 ?: 0),
            series = values,
            seriesStart = from,
            avgMs = avg,
            rule = dao.rule(pkg),
        )
    }

    // ---------- Insights ----------

    suspend fun loadInsights(): InsightsData = withContext(Dispatchers.IO) {
        val snap = todayLive()
        val t = snap.day
        val from = t.minusDays(29)
        Insights.build(
            today = snap,
            summaries = dao.summaries(key(from), key(t)),
            hours = dao.hours(key(t.minusDays(6)), key(t)),
            appDays = dao.appDays(key(t.minusDays(6)), key(t)),
            notifsToday = dao.notifs(key(t), key(t)),
            goalMs = prefs.dailyGoalMin.value * 60_000L,
            label = catalog::label,
        )
    }

    // ---------- Rules ----------

    fun rulesFlow(): Flow<List<LockRule>> = dao.rulesFlow()
    suspend fun rules(): List<LockRule> = dao.rules()

    suspend fun saveRule(rule: LockRule) {
        if (rule.dailyLimitMin == null && !rule.pauseEnabled && !rule.focusBlocked) dao.deleteRule(rule.pkg)
        else dao.upsertRule(rule)
    }

    // ---------- Category limits ----------

    fun categoryRulesFlow(): Flow<List<CategoryRule>> = dao.categoryRulesFlow()

    suspend fun setCategoryLimit(category: Category, limitMin: Int?) {
        if (limitMin == null) dao.deleteCategoryRule(category.name)
        else dao.upsertCategoryRule(CategoryRule(category.name, limitMin))
    }

    /** Milliseconds spent today across every app in [category]. */
    suspend fun todayMsForCategory(category: Category): Long =
        todayLive(maxAgeMs = 10_000).apps.filter { catalog.category(it.pkg) == category }.sumOf { it.ms }

    /** Today's time per category, with the apps that make it up, for the category limits screen. */
    suspend fun categoryToday(): Map<Category, List<Pair<String, Long>>> = withContext(Dispatchers.IO) {
        todayLive().apps.groupBy { catalog.category(it.pkg) }.mapValues { (_, list) -> list.map { it.pkg to it.ms } }
    }

    suspend fun countNotification(pkg: String) {
        dao.countNotification(key(today()), pkg)
    }

    suspend fun wipe() {
        dao.clearCategoryRules()
        dao.clearAll()
        prefs.clear()
        todayCache = null
    }

    /** Days since the first stored day, for "Gromp is still learning" states. */
    suspend fun historyDays(): Int {
        val first = dao.allSummaries().firstOrNull { it.totalMs > 0 }?.day ?: return 0
        return ChronoUnit.DAYS.between(LocalDate.parse(first), today()).toInt()
    }
}

