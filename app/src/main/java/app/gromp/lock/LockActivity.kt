package app.gromp.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import app.gromp.container
import app.gromp.data.Ev
import app.gromp.smart.IntentKind
import app.gromp.ui.theme.GrompTheme
import kotlinx.coroutines.launch

enum class LockMode { Pause, Limit, Focus, Intention, Drift, Binge, Loop }

data class LockRequest(
    val pkg: String,
    val mode: LockMode,
    val limitMin: Int = 0,
    val usedMs: Long = 0,
    val focusEnd: Int = 0,
    /** Set when a shared category limit (rather than the app's own) was reached. */
    val category: String? = null,
    /** The nudge_event row for a check-in, so the outcome can be recorded. */
    val eventId: Long = -1,
    /** Minutes in the session (binge) or since the intention (drift). */
    val minutes: Int = 0,
    val intent: IntentKind? = null,
    val cost: String? = null,
    val loopApps: Int = 0,
    val loopSpanMs: Long = 0,
)

/** Full-screen lock or check-in shown on top of an app. */
class LockActivity : ComponentActivity() {
    private var request by mutableStateOf<LockRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        request = parse(intent)
        if (request == null) { finish(); return }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { request?.let { close(it) } ?: leave() }
        })

        setContent {
            GrompTheme {
                val r = request ?: return@GrompTheme
                val c = container
                val smart = c.prefs.smart.value
                val todayMs by produceState(0L, r.pkg) { value = runCatching { c.repository.todayMsFor(r.pkg) }.getOrDefault(0L) }
                LockScreen(
                    request = r,
                    label = c.catalog.label(r.pkg),
                    icon = c.catalog.icon(r.pkg),
                    snoozesToday = c.grants.snoozes(r.category?.let { "cat:$it" } ?: r.pkg),
                    voice = smart.voice,
                    trades = smart.trades,
                    todayMs = todayMs,
                    onLeave = { close(r) },
                    onContinue = { proceed(r) },
                    onIntention = { kind -> chooseIntention(r, kind) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parse(intent)?.let { request = it }
    }

    private fun record(block: suspend () -> Unit) {
        container.appScope.launch { runCatching { block() } }
    }

    /** The user chose to stop. */
    private fun close(r: LockRequest) {
        val c = container
        when (r.mode) {
            LockMode.Intention -> {
                c.nudges.recordIntention(r.pkg, null)
                record { c.smart.log(r.pkg, Ev.INTENTION, detail = "none", outcome = Ev.CLOSED) }
            }
            LockMode.Drift, LockMode.Binge, LockMode.Loop -> if (r.eventId > 0) record { c.smart.setOutcome(r.eventId, Ev.CLOSED) }
            else -> Unit
        }
        leave()
    }

    /** The user chose to go on into the app. */
    private fun proceed(r: LockRequest) {
        val c = container
        val grants = c.grants
        when (r.mode) {
            LockMode.Pause -> grants.passPause(r.pkg)
            LockMode.Limit -> if (r.category != null) grants.snoozeCategory(r.category) else grants.snoozeLimit(r.pkg)
            LockMode.Focus -> grants.snoozeFocus(r.pkg)
            LockMode.Intention -> c.nudges.recordIntention(r.pkg, null)
            LockMode.Binge -> {
                c.nudges.snoozeBinge(r.pkg, System.currentTimeMillis() + c.prefs.smart.value.voice.bingeRepeatMin * 60_000L)
                if (r.eventId > 0) record { c.smart.setOutcome(r.eventId, Ev.CONTINUED) }
            }
            LockMode.Drift, LockMode.Loop -> if (r.eventId > 0) record { c.smart.setOutcome(r.eventId, Ev.CONTINUED) }
        }
        openApp(r.pkg)
    }

    private fun chooseIntention(r: LockRequest, kind: IntentKind) {
        val c = container
        c.nudges.recordIntention(r.pkg, kind)
        record { c.smart.log(r.pkg, Ev.INTENTION, detail = kind.name) }
        openApp(r.pkg)
    }

    private fun openApp(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(it) }
        }
        finish()
    }

    /** Close the app underneath by going to the home screen. */
    private fun leave() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
        finish()
    }

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_LIMIT = "limit"
        private const val EXTRA_USED = "used"
        private const val EXTRA_FOCUS_END = "focus_end"
        private const val EXTRA_CATEGORY = "category"
        private const val EXTRA_EVENT = "event"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_INTENT = "intent"
        private const val EXTRA_COST = "cost"
        private const val EXTRA_LOOP_APPS = "loop_apps"
        private const val EXTRA_LOOP_SPAN = "loop_span"

        fun intent(
            context: Context, pkg: String, mode: LockMode,
            limitMin: Int = 0, usedMs: Long = 0, focusEnd: Int = 0, category: String? = null,
            eventId: Long = -1, minutes: Int = 0, intent: String? = null, cost: String? = null,
            loopApps: Int = 0, loopSpanMs: Long = 0,
        ): Intent =
            Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_LIMIT, limitMin)
                .putExtra(EXTRA_USED, usedMs)
                .putExtra(EXTRA_FOCUS_END, focusEnd)
                .putExtra(EXTRA_CATEGORY, category)
                .putExtra(EXTRA_EVENT, eventId)
                .putExtra(EXTRA_MINUTES, minutes)
                .putExtra(EXTRA_INTENT, intent)
                .putExtra(EXTRA_COST, cost)
                .putExtra(EXTRA_LOOP_APPS, loopApps)
                .putExtra(EXTRA_LOOP_SPAN, loopSpanMs)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

        private fun parse(i: Intent?): LockRequest? {
            val pkg = i?.getStringExtra(EXTRA_PKG) ?: return null
            val mode = runCatching { LockMode.valueOf(i.getStringExtra(EXTRA_MODE) ?: "") }.getOrNull() ?: return null
            return LockRequest(
                pkg = pkg,
                mode = mode,
                limitMin = i.getIntExtra(EXTRA_LIMIT, 0),
                usedMs = i.getLongExtra(EXTRA_USED, 0),
                focusEnd = i.getIntExtra(EXTRA_FOCUS_END, 0),
                category = i.getStringExtra(EXTRA_CATEGORY),
                eventId = i.getLongExtra(EXTRA_EVENT, -1),
                minutes = i.getIntExtra(EXTRA_MINUTES, 0),
                intent = IntentKind.from(i.getStringExtra(EXTRA_INTENT)),
                cost = i.getStringExtra(EXTRA_COST),
                loopApps = i.getIntExtra(EXTRA_LOOP_APPS, 0),
                loopSpanMs = i.getLongExtra(EXTRA_LOOP_SPAN, 0),
            )
        }
    }
}
