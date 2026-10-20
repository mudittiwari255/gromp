package app.gromp.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gromp.data.db.AppDay
import app.gromp.data.db.AppHour
import app.gromp.data.db.CategoryRule
import app.gromp.data.db.DaySummary
import app.gromp.data.db.GrompDatabase
import app.gromp.data.db.HourUsage
import app.gromp.data.db.LockRule
import app.gromp.data.db.NudgeEvent
import app.gromp.data.db.SessionRating
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Room migrations, DAO queries and backup round-trips, run on the JVM with Robolectric. */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun cleanup() {
        context.deleteDatabase(name)
    }

    /** The v1 (0.2.x) or v2 (0.3.x) schema exactly as Room created it, with a little data. */
    private fun createV1(version: Int = 1) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("CREATE TABLE IF NOT EXISTS `day_summary` (`day` TEXT NOT NULL, `totalMs` INTEGER NOT NULL, `pickups` INTEGER NOT NULL, `firstPickupMinute` INTEGER, `longestSessionMs` INTEGER NOT NULL, `longestPkg` TEXT, `longestStart` INTEGER, `shortSessions` INTEGER NOT NULL, `sessions` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`day`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `app_day` (`day` TEXT NOT NULL, `pkg` TEXT NOT NULL, `ms` INTEGER NOT NULL, `opens` INTEGER NOT NULL, `longestMs` INTEGER NOT NULL, `under1` INTEGER NOT NULL, `m1to5` INTEGER NOT NULL, `m5to15` INTEGER NOT NULL, `over15` INTEGER NOT NULL, PRIMARY KEY(`day`, `pkg`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `hour_usage` (`day` TEXT NOT NULL, `hour` INTEGER NOT NULL, `ms` INTEGER NOT NULL, PRIMARY KEY(`day`, `hour`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `notif_day` (`day` TEXT NOT NULL, `pkg` TEXT NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`day`, `pkg`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `lock_rule` (`pkg` TEXT NOT NULL, `dailyLimitMin` INTEGER, `pauseEnabled` INTEGER NOT NULL, `focusBlocked` INTEGER NOT NULL, PRIMARY KEY(`pkg`))")
        if (version >= 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `category_rule` (`category` TEXT NOT NULL, `dailyLimitMin` INTEGER NOT NULL, PRIMARY KEY(`category`))")
        }
        db.execSQL("INSERT INTO day_summary VALUES ('2026-10-19', 14400000, 70, 430, 2880000, 'com.instagram.android', 1, 30, 90, 2)")
        db.execSQL("INSERT INTO lock_rule VALUES ('com.instagram.android', 30, 1, 0)")
        db.version = version
        db.close()
    }

    private fun open() = Room.databaseBuilder(context, GrompDatabase::class.java, name)
        .addMigrations(GrompDatabase.MIGRATION_1_2, GrompDatabase.MIGRATION_2_3)
        .allowMainThreadQueries()
        .build()

    @Test
    fun `migrates v1 to v3 and keeps data`() = runTest {
        createV1(1)
        val db = open()
        val dao = db.dao()
        // Opening validates every table against the v3 schema; a mismatch throws here.
        val s = dao.allSummaries()
        assertEquals(1, s.size)
        assertEquals(14_400_000L, s.first().totalMs)
        assertEquals(LockRule("com.instagram.android", 30, pauseEnabled = true, focusBlocked = false), dao.rules().first())
        // New tables work.
        dao.upsertCategoryRule(CategoryRule("Social", 60))
        dao.insertAppHours(listOf(AppHour("2026-10-19", "a", 22, 1000)))
        dao.upsertRating(SessionRating("a", 1, 2, "2026-10-19", true, 3))
        dao.insertEvent(NudgeEvent(at = 1, day = "2026-10-19", pkg = "a", kind = Ev.BINGE))
        assertEquals(1, dao.categoryRules().size)
        assertEquals(1, dao.allAppHours().size)
        assertEquals(1, dao.allRatings().size)
        assertEquals(1, dao.allEvents().size)
        db.close()
    }

    @Test
    fun `migrates v2 to v3`() = runTest {
        createV1(2)
        val db = open()
        assertEquals(0, db.dao().categoryRules().size)
        assertEquals(1, db.dao().allSummaries().size)
        db.close()
    }

    @Test
    fun `replaceDay swaps app hours and events record outcomes`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, GrompDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.dao()
        val sum = DaySummary("2026-10-20", 1000, 1, 1, 1, "a", 1, 0, 1, 1)
        dao.replaceDay(sum, listOf(AppDay("2026-10-20", "a", 1000, 1, 1000, 0, 0, 0, 0)), listOf(HourUsage("2026-10-20", 9, 1000)), listOf(AppHour("2026-10-20", "a", 9, 1000)))
        dao.replaceDay(sum, emptyList(), emptyList(), listOf(AppHour("2026-10-20", "b", 10, 500)))
        assertEquals(listOf(AppHour("2026-10-20", "b", 10, 500)), dao.appHours("2026-10-20", "2026-10-20"))

        val id = dao.insertEvent(NudgeEvent(at = 5, day = "2026-10-20", pkg = "a", kind = Ev.BINGE))
        dao.setOutcome(id, Ev.CLOSED)
        assertEquals(Ev.CLOSED, dao.events("2026-10-20", "2026-10-20").single().outcome)
        assertEquals(1, dao.eventsOfKind(Ev.BINGE, "2026-10-01", "2026-10-31").size)

        // Rating the same session twice keeps one answer, the latest.
        dao.upsertRating(SessionRating("a", 1, 2, "2026-10-20", false, 3))
        dao.upsertRating(SessionRating("a", 1, 2, "2026-10-20", true, 4))
        assertTrue(dao.ratings("2026-10-20", "2026-10-20").single().worth)

        dao.clearAll()
        assertTrue(dao.allAppHours().isEmpty() && dao.allEvents().isEmpty() && dao.allRatings().isEmpty())
        db.close()
    }

    @Test
    fun `backup round trip keeps everything`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, GrompDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.dao()
        val prefs = Prefs(context)
        prefs.setDailyGoal(300)
        val day = "2026-10-20"
        dao.replaceDay(
            DaySummary(day, 7_200_000, 40, 420, 1_800_000, "a", 5, 10, 30, 9),
            listOf(AppDay(day, "a", 7_200_000, 12, 1_800_000, 5, 4, 2, 1)),
            listOf(HourUsage(day, 22, 3_600_000)),
            listOf(AppHour(day, "a", 22, 3_600_000)),
        )
        dao.countNotification(day, "wa")
        dao.upsertRule(LockRule("a", 30, true, true))
        dao.upsertCategoryRule(CategoryRule("Social", 60))
        dao.upsertRating(SessionRating("a", 10, 20, day, false, 30))
        dao.insertEvent(NudgeEvent(at = 40, day = day, pkg = "a", kind = Ev.INTENTION, detail = "Reply"))

        val out = ByteArrayOutputStream()
        Backup(dao, prefs).export(out)
        dao.clearAll()
        prefs.setDailyGoal(240)
        Backup(dao, prefs).import(ByteArrayInputStream(out.toByteArray()))

        assertEquals(300, prefs.dailyGoalMin.value)
        assertEquals(7_200_000L, dao.allSummaries().single().totalMs)
        assertEquals(1, dao.allAppDays().size)
        assertEquals(1, dao.allHours().size)
        assertEquals(1, dao.allAppHours().size)
        assertEquals(1, dao.allNotifs().single().count)
        assertEquals(LockRule("a", 30, true, true), dao.rules().single())
        assertEquals(CategoryRule("Social", 60), dao.categoryRules().single())
        assertEquals(false, dao.allRatings().single().worth)
        assertEquals("Reply", dao.allEvents().single().detail)
        db.close()
    }

    @Test
    fun `import rejects files that aren't Gromp backups`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, GrompDatabase::class.java).allowMainThreadQueries().build()
        val err = runCatching { Backup(db.dao(), Prefs(context)).import(ByteArrayInputStream("{\"format\":\"other\"}".toByteArray())) }
        assertTrue(err.isFailure)
        db.close()
    }

    @Test
    fun `smart settings persist`() {
        val p = Prefs(context)
        p.updateSmart { it.copy(voice = app.gromp.smart.Voice.Tough, trades = listOf(app.gromp.smart.Trade.Exercise, app.gromp.smart.Trade.Reading), intentions = false) }
        val again = Prefs(context).smart.value
        assertEquals(app.gromp.smart.Voice.Tough, again.voice)
        assertEquals(listOf(app.gromp.smart.Trade.Exercise, app.gromp.smart.Trade.Reading), again.trades)
        assertEquals(false, again.intentions)
    }
}
