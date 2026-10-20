package app.gromp.data

import app.gromp.data.db.GrompDao
import app.gromp.data.db.NudgeEvent
import app.gromp.data.db.SessionRating
import app.gromp.smart.AppHourRow
import app.gromp.smart.CheckInEvent
import app.gromp.smart.Cost
import app.gromp.smart.DayTotal
import app.gromp.smart.LongSession
import app.gromp.smart.Rating
import app.gromp.smart.Review
import app.gromp.smart.ReviewInput
import app.gromp.smart.Taper
import app.gromp.smart.TaperDecision
import app.gromp.smart.WeakSpot
import app.gromp.smart.WeakSpots
import app.gromp.smart.WeeklyReview
import app.gromp.smart.Worth
import app.gromp.smart.WorthScore
import app.gromp.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Event kinds stored in nudge_event. */
object Ev {
    const val INTENTION = "Intention"
    const val DRIFT = "Drift"
    const val BINGE = "Binge"
    const val LOOP = "Loop"
    const val HEADS_UP = "HeadsUp"
    const val WORTH_ASKED = "WorthAsked"
    const val CLOSED = "closed"
    const val CONTINUED = "continued"
}

data class TodaySmart(
    val costLine: String,
    val yearLine: String?,
    val taperLine: String?,
    val toRate: List<LongSession>,
    val tradesChosen: Boolean,
)

/** Connects the pure smart logic to stored history and settings. */
class SmartRepository(
    private val dao: GrompDao,
    private val repo: Repository,
    private val catalog: AppCatalog,
    private val prefs: Prefs,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val ignored by lazy { catalog.ignoredForStats() }

    private fun key(d: LocalDate) = d.toString()
    private fun dayOf(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    /** Apps that get intentions, binge and loop check-ins, and worth-it questions. */
    fun isPull(pkg: String): Boolean = pkg !in ignored && catalog.category(pkg) in prefs.smart.value.pullCategories

    // ---------- Ratings and events ----------

    suspend fun rate(pkg: String, start: Long, end: Long, worth: Boolean) = withContext(Dispatchers.IO) {
        dao.upsertRating(SessionRating(pkg, start, end, key(dayOf(start)), worth, System.currentTimeMillis()))
    }

    suspend fun log(pkg: String, kind: String, detail: String? = null, outcome: String? = null): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.insertEvent(NudgeEvent(at = now, day = key(dayOf(now)), pkg = pkg, kind = kind, detail = detail, outcome = outcome))
    }

    suspend fun setOutcome(id: Long, outcome: String) = withContext(Dispatchers.IO) { dao.setOutcome(id, outcome) }

    /** Today's sessions, with quick returns to the same app stitched together. */
    suspend fun todaySessions(): List<LongSession> {
        val raw = repo.todayLive().parsed.sessions.map { LongSession(it.pkg, it.start, it.end) }
        return Worth.merge(raw)
    }

    /**
     * Long pull-app sessions from today that haven't been rated.
     * @param skipAsked also leave out sessions already offered in a notification
     */
    suspend fun toRateToday(skipAsked: Boolean = false): List<LongSession> = withContext(Dispatchers.IO) {
        if (!prefs.smart.value.worthPrompts) return@withContext emptyList()
        val t = repo.today()
        val handled = dao.ratings(key(t), key(t)).map { it.pkg to it.start }.toMutableSet()
        if (skipAsked) {
            dao.eventsOfKind(Ev.WORTH_ASKED, key(t), key(t)).forEach { e -> e.detail?.toLongOrNull()?.let { handled += e.pkg to it } }
        }
        Worth.toAsk(todaySessions(), ::isPull, handled, System.currentTimeMillis())
    }

    // ---------- Patterns ----------

    suspend fun weakSpots(): List<WeakSpot> = withContext(Dispatchers.IO) {
        val t = repo.today()
        val rows = dao.appHours(key(t.minusDays(28)), key(t.minusDays(1)))
            .map { AppHourRow(LocalDate.parse(it.day), it.pkg, it.hour, it.ms) }
        WeakSpots.find(rows, ::isPull, t)
    }

    suspend fun worthScores(days: Long = 30): List<WorthScore> = withContext(Dispatchers.IO) {
        val t = repo.today()
        Worth.scores(dao.ratings(key(t.minusDays(days)), key(t)).map { Rating(it.pkg, it.start, it.end, it.worth) })
    }

    // ---------- Today ----------

    suspend fun today(totalMs: Long): TodaySmart = withContext(Dispatchers.IO) {
        val s = prefs.smart.value
        val t = repo.today()
        val week = dao.summaries(key(t.minusDays(7)), key(t.minusDays(1))).filter { it.totalMs > 0 }
        val avg = week.takeIf { it.size >= 3 }?.let { w -> w.sumOf { it.totalMs } / w.size }
        val taperLine = prefs.lastTaperChange?.takeIf { s.taper && prefs.lastTaperWeek == Taper.weekKey(t) }
        TodaySmart(
            costLine = Cost.todayLine(totalMs, s.trades, s.voice, seed = t.dayOfYear),
            yearLine = avg?.let { Cost.yearLine(it, s.voice) },
            taperLine = taperLine,
            toRate = toRateToday(),
            tradesChosen = s.trades.isNotEmpty(),
        )
    }

    // ---------- Taper ----------

    /** Once per week, judges last week and lowers the limit if it was a good one. */
    suspend fun runTaperIfDue(): TaperDecision? = withContext(Dispatchers.IO) {
        val s = prefs.smart.value
        val t = repo.today()
        val thisWeek = Taper.weekKey(t)
        if (!s.taper || prefs.lastTaperWeek == thisWeek) return@withContext null
        val days = Taper.previousWeek(t)
        val sums = dao.summaries(key(days.first()), key(days.last())).associateBy { it.day }
        val goal = prefs.dailyGoalMin.value
        val d = Taper.evaluate(goal, s.taperFloorMin, days.map { sums[key(it)]?.totalMs })
        if (d.changed) {
            prefs.setDailyGoal(d.newGoalMin)
            prefs.lastTaperChange = "${Fmt.duration(d.oldGoalMin * 60_000L)} → ${Fmt.duration(d.newGoalMin * 60_000L)}"
        } else {
            prefs.lastTaperChange = null
        }
        prefs.lastTaperWeek = thisWeek
        d
    }

    // ---------- Weekly review ----------

    suspend fun review(weekStart: LocalDate): Review = withContext(Dispatchers.IO) {
        if (weekStart == Taper.mondayOf(repo.today())) repo.todayLive()
        val s = prefs.smart.value
        val end = weekStart.plusDays(6)
        val prevStart = weekStart.minusDays(7)
        val sums = dao.summaries(key(weekStart), key(end))
        val prev = dao.summaries(key(prevStart), key(weekStart.minusDays(1))).filter { it.totalMs > 0 }
        val apps = dao.appDays(key(weekStart), key(end)).groupBy { it.pkg }.mapValues { (_, r) -> r.sumOf { it.ms } }
        val appsPrev = dao.appDays(key(prevStart), key(weekStart.minusDays(1))).groupBy { it.pkg }.mapValues { (_, r) -> r.sumOf { it.ms } }
        val notif = dao.notifs(key(weekStart), key(end)).groupBy { it.pkg }.mapValues { (_, r) -> r.sumOf { it.count } }
            .maxByOrNull { it.value }?.toPair()
        val goal = prefs.dailyGoalMin.value
        val byDay = sums.associateBy { it.day }
        val taper = if (s.taper) Taper.evaluate(goal, s.taperFloorMin, (0..6).map { byDay[key(weekStart.plusDays(it.toLong()))]?.totalMs }) else null

        WeeklyReview.build(
            ReviewInput(
                weekStart = weekStart,
                days = sums.map { DayTotal(LocalDate.parse(it.day), it.totalMs, it.pickups, it.shortSessions, it.sessions, it.firstPickupMinute) },
                prevWeekMs = prev.takeIf { it.isNotEmpty() }?.sumOf { it.totalMs },
                appsThis = apps,
                appsPrev = appsPrev,
                ratings = dao.ratings(key(weekStart), key(end)).map { Rating(it.pkg, it.start, it.end, it.worth) },
                checkIns = dao.events(key(weekStart), key(end)).map { CheckInEvent(it.kind, it.pkg, it.outcome) },
                goalMin = goal,
                taper = taper,
                weakSpots = weakSpots(),
                topNotif = notif,
                trades = s.trades,
                voice = s.voice,
                label = catalog::label,
            ),
        )
    }
}
