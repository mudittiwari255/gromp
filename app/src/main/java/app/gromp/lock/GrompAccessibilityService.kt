package app.gromp.lock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import app.gromp.container
import app.gromp.data.Ev
import app.gromp.data.db.LockRule
import app.gromp.smart.Cost
import app.gromp.smart.LoopHit
import app.gromp.smart.NudgeKind
import app.gromp.smart.NudgePlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Watches only which app comes to the front (window-state events, no screen content).
 * Shows Gromp's lock screens when a rule applies, and its check-ins (why are you here,
 * drift, binge, loop) for pull apps.
 */
class GrompAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var rules: Map<String, LockRule> = emptyMap()
    @Volatile private var categoryLimits: Map<String, Int> = emptyMap()
    private var ignored: Set<String> = emptySet()
    private var transient: Set<String> = TRANSIENT
    private var currentPkg: String? = null
    private var recheck: Job? = null

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The phone went to sleep: whatever was on screen has ended.
            container.nudges.watch.onScreenOff()
            container.nudges.loops.reset()
            currentPkg = null
            recheck?.cancel()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val c = container
        transient = TRANSIENT + c.catalog.keyboards()
        ignored = c.catalog.neverLock() + packageName - transient
        scope.launch { c.repository.rulesFlow().collectLatest { list -> rules = list.associateBy { it.pkg } } }
        scope.launch {
            c.repository.categoryRulesFlow().collectLatest { list -> categoryLimits = list.associate { it.category to it.dailyLimitMin } }
        }
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return                 // our own lock and check-in screens
        if (pkg == currentPkg) return                  // moving between screens of the same app
        if (pkg in transient) return                   // notification shade, keyboard, etc.

        val c = container
        val now = System.currentTimeMillis()
        if (pkg in ignored) {
            // Home screen, dialer and the like: never locked, but they do mean the previous app left.
            currentPkg = pkg
            recheck?.cancel()
            c.nudges.watch.onLeave(now)
            return
        }
        currentPkg = pkg
        recheck?.cancel()

        val s = c.prefs.smart.value
        val pull = c.smart.isPull(pkg)
        val newSession = c.nudges.watch.onForeground(pkg, pull, now)
        var loop: LoopHit? = null
        if (pull && newSession) {
            c.nudges.onNewSession(pkg)
            if (s.loopChecks) loop = c.nudges.loops.onPullOpen(pkg, now)
        }
        recheck = scope.launch { evaluate(pkg, onOpen = true, loop = loop) }
    }

    private suspend fun evaluate(pkg: String, onOpen: Boolean, loop: LoopHit? = null) {
        val c = container
        val rule = rules[pkg]
        val category = c.catalog.category(pkg)
        val catLimitMin = categoryLimits[category.name]
        var lockWait: Long? = null

        if (rule != null || catLimitMin != null) {
            val used = if (rule?.dailyLimitMin != null) runCatching { c.repository.todayMsFor(pkg) }.getOrDefault(0L) else 0L
            val categoryLimit = catLimitMin?.let { limit ->
                val catUsed = runCatching { c.repository.todayMsForCategory(category) }.getOrDefault(0L)
                CategoryLimit(category.name, limit, catUsed, c.grants.categorySnoozeUntil(category.name))
            }
            val focus = c.prefs.focus.value
            val now = LocalDateTime.now()
            val dow = now.dayOfWeek.value
            val prevDow = if (dow == 1) 7 else dow - 1
            val focusActive = focus.isActive(dow, now.hour * 60 + now.minute, prevDow)

            val decision = LockDecider.decide(
                rule, used, focusActive, focus.endMinute, c.grants.view(pkg), System.currentTimeMillis(), onOpen, categoryLimit,
            )
            if (currentPkg != pkg) return
            when (decision) {
                is Decision.Allow -> lockWait = decision.recheckInMs
                Decision.Pause -> return show(LockActivity.intent(this, pkg, LockMode.Pause))
                is Decision.Limit -> return show(
                    LockActivity.intent(this, pkg, LockMode.Limit, limitMin = decision.limitMin, usedMs = decision.usedMs, category = decision.category),
                )
                is Decision.Focus -> return show(LockActivity.intent(this, pkg, LockMode.Focus, focusEnd = decision.endMinute))
            }
        }

        // Check-ins only for pull apps, and only while that app is still in front.
        if (!c.smart.isPull(pkg) || currentPkg != pkg) return scheduleRecheck(pkg, lockWait)
        val s = c.prefs.smart.value
        val now = System.currentTimeMillis()

        if (onOpen && loop != null) {
            val id = c.smart.log(pkg, Ev.LOOP, detail = "${loop.apps}")
            return show(LockActivity.intent(this, pkg, LockMode.Loop, eventId = id, loopApps = loop.apps, loopSpanMs = loop.spanMs))
        }
        // Skip "what for?" right after the breathe screen; that already made you stop and think.
        val justPaused = c.grants.view(pkg).pausePassedUntil > now
        if (onOpen && s.intentions && !justPaused && c.nudges.needsIntention(pkg, now)) {
            return show(LockActivity.intent(this, pkg, LockMode.Intention))
        }

        val watch = c.nudges.watch.current?.takeIf { it.pkg == pkg }
        val due = NudgePlanner.next(
            watch = watch,
            intention = c.nudges.intention(pkg),
            driftHandled = c.nudges.isDriftHandled(pkg),
            bingeAfterMs = s.voice.bingeAfterMin * MIN,
            bingeSnoozeUntil = c.nudges.bingeSnoozeUntil(pkg),
            bingeEnabled = s.bingeChecks,
        )
        if (due != null && watch != null && due.at <= now) {
            when (due.kind) {
                NudgeKind.Drift -> {
                    val i = c.nudges.intention(pkg)
                    if (i != null) {
                        c.nudges.markDriftHandled(pkg)
                        val id = c.smart.log(pkg, Ev.DRIFT, detail = i.kind.name)
                        return show(
                            LockActivity.intent(this, pkg, LockMode.Drift, eventId = id, minutes = ((now - i.at) / MIN).toInt(), intent = i.kind.name),
                        )
                    }
                }
                NudgeKind.Binge -> {
                    val sessionMs = now - watch.start
                    val id = c.smart.log(pkg, Ev.BINGE, detail = "${sessionMs / MIN}")
                    val cost = Cost.pick(sessionMs, s.trades, (now / MIN).toInt())?.second
                    return show(LockActivity.intent(this, pkg, LockMode.Binge, eventId = id, minutes = (sessionMs / MIN).toInt(), cost = cost))
                }
            }
        }
        val nudgeWait = due?.let { (it.at - now).coerceAtLeast(1_000) }
        scheduleRecheck(pkg, listOfNotNull(lockWait, nudgeWait).minOrNull())
    }

    private fun scheduleRecheck(pkg: String, waitMs: Long?) {
        if (waitMs == null) return
        recheck = scope.launch {
            delay(waitMs)
            if (currentPkg == pkg) evaluate(pkg, onOpen = false)
        }
    }

    private fun show(intent: Intent) {
        // Forget the current app so coming back to it (e.g. via Recents) is checked again.
        currentPkg = null
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        val TRANSIENT = setOf("com.android.systemui", "android")
        const val MIN = 60_000L
    }
}
