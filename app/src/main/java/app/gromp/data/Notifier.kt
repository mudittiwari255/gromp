package app.gromp.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.gromp.MainActivity
import app.gromp.R
import app.gromp.container
import app.gromp.smart.Copy
import app.gromp.smart.LongSession
import app.gromp.smart.Review
import app.gromp.smart.Voice
import app.gromp.smart.WeakSpot
import kotlinx.coroutines.launch

/** Gromp's few notifications: worth-it questions, weak-spot heads-ups and the Sunday review. */
class Notifier(private val context: Context) {

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_NUDGES, "Check-ins", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Worth-it questions and heads-ups before your usual weak spots"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REVIEW, "Weekly review", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your Sunday evening review"
            },
        )
    }

    fun worthPrompt(s: LongSession, label: String, voice: Voice) {
        val (title, body) = Copy.worthPrompt(label, s.durationMs, voice)
        val id = (s.pkg.hashCode() * 31 + s.start.hashCode())
        fun action(worth: Boolean) = PendingIntent.getBroadcast(
            context, id * 2 + if (worth) 1 else 0,
            Intent(context, RatingReceiver::class.java)
                .putExtra(EXTRA_PKG, s.pkg).putExtra(EXTRA_START, s.start).putExtra(EXTRA_END, s.end)
                .putExtra(EXTRA_WORTH, worth).putExtra(EXTRA_NOTIF, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            id,
            NotificationCompat.Builder(context, CH_NUDGES)
                .setSmallIcon(R.drawable.ic_stat_gromp)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(open(Open.TODAY, id))
                .setAutoCancel(true)
                .addAction(0, "Worth it", action(true))
                .addAction(0, "Not worth it", action(false))
                .build(),
        )
    }

    fun headsUp(spot: WeakSpot, label: String, voice: Voice) {
        val (title, body) = Copy.headsUp(spot, label, voice)
        post(
            ID_HEADS_UP,
            NotificationCompat.Builder(context, CH_NUDGES)
                .setSmallIcon(R.drawable.ic_stat_gromp)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open(Open.FOCUS, ID_HEADS_UP))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun review(r: Review) {
        post(
            ID_REVIEW,
            NotificationCompat.Builder(context, CH_REVIEW)
                .setSmallIcon(R.drawable.ic_stat_gromp)
                .setContentTitle("Your week in review")
                .setContentText(r.headline)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${r.headline}\n${r.costLine}\nTry next: ${r.experiment.title}"))
                .setContentIntent(open(Open.REVIEW, ID_REVIEW))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun open(where: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, where).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @Suppress("MissingPermission")
    private fun post(id: Int, n: android.app.Notification) {
        if (!canPost()) return
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)

    object Open {
        const val TODAY = "today"
        const val FOCUS = "focus"
        const val REVIEW = "review"
    }

    companion object {
        const val CH_NUDGES = "nudges"
        const val CH_REVIEW = "review"
        const val ID_HEADS_UP = 2001
        const val ID_REVIEW = 2002
        const val EXTRA_OPEN = "open"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_START = "start"
        const val EXTRA_END = "end"
        const val EXTRA_WORTH = "worth"
        const val EXTRA_NOTIF = "notif"
    }
}

/** Handles the "Worth it" / "Not worth it" buttons on a notification. */
class RatingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(Notifier.EXTRA_PKG) ?: return
        val start = intent.getLongExtra(Notifier.EXTRA_START, 0)
        val end = intent.getLongExtra(Notifier.EXTRA_END, 0)
        val worth = intent.getBooleanExtra(Notifier.EXTRA_WORTH, false)
        val c = context.container
        c.notifier.cancel(intent.getIntExtra(Notifier.EXTRA_NOTIF, 0))
        val pending = goAsync()
        c.appScope.launch {
            runCatching { c.smart.rate(pkg, start, end, worth) }
            pending.finish()
        }
    }
}
