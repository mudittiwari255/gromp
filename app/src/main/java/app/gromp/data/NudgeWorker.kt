package app.gromp.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.gromp.GrompApp
import app.gromp.smart.Schedule
import app.gromp.smart.Taper
import app.gromp.smart.WeakSpots
import app.gromp.util.Permissions
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs about every 15 minutes: lowers the limit after a good week, asks "worth it?" about a long
 * session that just ended, warns before a usual weak spot, and sends the Sunday review.
 */
class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as GrompApp).container
        if (!Permissions.hasUsageAccess(applicationContext)) return Result.success()
        val s = c.prefs.smart.value
        val now = LocalDateTime.now()

        runCatching { c.smart.runTaperIfDue() }

        if (!c.notifier.canPost()) return Result.success()

        if (s.worthPrompts && !Schedule.quietHours(now)) {
            runCatching {
                c.smart.toRateToday(skipAsked = true).firstOrNull()?.let { session ->
                    c.notifier.worthPrompt(session, c.catalog.label(session.pkg), s.voice)
                    c.smart.log(session.pkg, Ev.WORTH_ASKED, detail = session.start.toString())
                }
            }
        }

        if (s.headsUps) {
            runCatching {
                WeakSpots.upcoming(c.smart.weakSpots(), now)?.let { spot ->
                    val key = Schedule.headsUpKey(now, spot)
                    if (c.prefs.lastHeadsUp != key) {
                        c.notifier.headsUp(spot, c.catalog.label(spot.pkg), s.voice)
                        c.prefs.lastHeadsUp = key
                        c.smart.log(spot.pkg, Ev.HEADS_UP, detail = spot.hour.toString())
                    }
                }
            }
        }

        if (s.weeklyReview && Schedule.reviewDue(now, c.prefs.lastReviewNotified)) {
            runCatching {
                val review = c.smart.review(Taper.mondayOf(now.toLocalDate()))
                if (review.totalMs > 0) c.notifier.review(review)
                c.prefs.lastReviewNotified = Taper.weekKey(now.toLocalDate())
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "gromp-nudges"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<NudgeWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
