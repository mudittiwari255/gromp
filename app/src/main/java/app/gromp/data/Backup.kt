package app.gromp.data

import app.gromp.data.db.AppDay
import app.gromp.data.db.DaySummary
import app.gromp.data.db.GrompDao
import app.gromp.data.db.HourUsage
import app.gromp.data.db.LockRule
import app.gromp.data.db.NotifDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/** Local backup as a plain JSON file the user saves wherever they like. Nothing is uploaded. */
class Backup(private val dao: GrompDao, private val prefs: Prefs) {

    suspend fun export(out: OutputStream) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("format", "gromp-backup")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("dailyGoalMin", prefs.dailyGoalMin.value)
        prefs.focus.value.let {
            root.put("focus", JSONObject().put("enabled", it.enabled).put("start", it.startMinute).put("end", it.endMinute).put("days", it.days))
        }
        root.put("summaries", JSONArray().apply {
            dao.allSummaries().forEach {
                put(JSONObject().put("day", it.day).put("totalMs", it.totalMs).put("pickups", it.pickups)
                    .put("firstPickupMinute", it.firstPickupMinute ?: JSONObject.NULL)
                    .put("longestSessionMs", it.longestSessionMs).put("longestPkg", it.longestPkg ?: JSONObject.NULL)
                    .put("longestStart", it.longestStart ?: JSONObject.NULL)
                    .put("shortSessions", it.shortSessions).put("sessions", it.sessions).put("updatedAt", it.updatedAt))
            }
        })
        root.put("apps", JSONArray().apply {
            dao.allAppDays().forEach {
                put(JSONObject().put("day", it.day).put("pkg", it.pkg).put("ms", it.ms).put("opens", it.opens)
                    .put("longestMs", it.longestMs).put("u1", it.under1).put("m1", it.m1to5).put("m5", it.m5to15).put("m15", it.over15))
            }
        })
        root.put("hours", JSONArray().apply {
            dao.allHours().forEach { put(JSONObject().put("day", it.day).put("hour", it.hour).put("ms", it.ms)) }
        })
        root.put("notifs", JSONArray().apply {
            dao.allNotifs().forEach { put(JSONObject().put("day", it.day).put("pkg", it.pkg).put("count", it.count)) }
        })
        root.put("rules", JSONArray().apply {
            dao.rules().forEach {
                put(JSONObject().put("pkg", it.pkg).put("limit", it.dailyLimitMin ?: JSONObject.NULL)
                    .put("pause", it.pauseEnabled).put("focus", it.focusBlocked))
            }
        })
        out.bufferedWriter().use { it.write(root.toString()) }
    }

    /** Replaces all local data with the backup. Throws if the file isn't a Gromp backup. */
    suspend fun import(input: InputStream) = withContext(Dispatchers.IO) {
        val root = JSONObject(input.bufferedReader().use { it.readText() })
        require(root.optString("format") == "gromp-backup") { "This file isn't a Gromp backup." }

        fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        fun JSONObject.optLongOrNull(k: String) = if (isNull(k)) null else getLong(k)
        fun JSONObject.optIntOrNull(k: String) = if (isNull(k)) null else getInt(k)
        fun JSONObject.optStringOrNull(k: String) = if (isNull(k)) null else getString(k)

        val summaries = root.getJSONArray("summaries").objects().map {
            DaySummary(it.getString("day"), it.getLong("totalMs"), it.getInt("pickups"), it.optIntOrNull("firstPickupMinute"),
                it.getLong("longestSessionMs"), it.optStringOrNull("longestPkg"), it.optLongOrNull("longestStart"),
                it.getInt("shortSessions"), it.getInt("sessions"), it.getLong("updatedAt"))
        }
        val apps = root.getJSONArray("apps").objects().map {
            AppDay(it.getString("day"), it.getString("pkg"), it.getLong("ms"), it.getInt("opens"), it.getLong("longestMs"),
                it.getInt("u1"), it.getInt("m1"), it.getInt("m5"), it.getInt("m15"))
        }
        val hours = root.getJSONArray("hours").objects().map { HourUsage(it.getString("day"), it.getInt("hour"), it.getLong("ms")) }
        val notifs = root.getJSONArray("notifs").objects().map { NotifDay(it.getString("day"), it.getString("pkg"), it.getInt("count")) }
        val rules = root.getJSONArray("rules").objects().map {
            LockRule(it.getString("pkg"), it.optIntOrNull("limit"), it.getBoolean("pause"), it.getBoolean("focus"))
        }
        dao.restore(summaries, apps, hours, notifs, rules)
        prefs.setDailyGoal(root.optInt("dailyGoalMin", 240))
        root.optJSONObject("focus")?.let {
            prefs.setFocus(FocusSchedule(it.getBoolean("enabled"), it.getInt("start"), it.getInt("end"), it.getInt("days")))
        }
    }
}
