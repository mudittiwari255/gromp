package app.gromp.lock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import app.gromp.container
import app.gromp.data.db.LockRule
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
 * Watches only which app comes to the front (window-state events, no screen content)
 * and shows Gromp's lock screen when one of the user's rules applies.
 */
class GrompAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var rules: Map<String, LockRule> = emptyMap()
    private var ignored: Set<String> = emptySet()
    private var transient: Set<String> = TRANSIENT
    private var currentPkg: String? = null
    private var recheck: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val c = container
        transient = TRANSIENT + c.catalog.keyboards()
        ignored = c.catalog.neverLock() + packageName - transient
        scope.launch { c.repository.rulesFlow().collectLatest { list -> rules = list.associateBy { it.pkg } } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return                 // our own lock screen
        if (pkg == currentPkg) return                  // moving between screens of the same app
        if (pkg in transient) return                   // notification shade, keyboard, etc.

        if (pkg in ignored) {
            // Home screen, dialer and the like: never locked, but they do mean the previous app left.
            currentPkg = pkg
            recheck?.cancel()
            return
        }
        currentPkg = pkg
        recheck?.cancel()
        recheck = scope.launch { evaluate(pkg, onOpen = true) }
    }

    private suspend fun evaluate(pkg: String, onOpen: Boolean) {
        val rule = rules[pkg] ?: return
        val c = container
        val used = if (rule.dailyLimitMin != null) runCatching { c.repository.todayMsFor(pkg) }.getOrDefault(0L) else 0L

        val focus = c.prefs.focus.value
        val now = LocalDateTime.now()
        val dow = now.dayOfWeek.value
        val prevDow = if (dow == 1) 7 else dow - 1
        val focusActive = focus.isActive(dow, now.hour * 60 + now.minute, prevDow)

        val decision = LockDecider.decide(
            rule, used, focusActive, focus.endMinute, c.grants.view(pkg), System.currentTimeMillis(), onOpen,
        )
        if (currentPkg != pkg) return

        when (decision) {
            is Decision.Allow -> decision.recheckInMs?.let { wait ->
                recheck = scope.launch {
                    delay(wait)
                    if (currentPkg == pkg) evaluate(pkg, onOpen = false)
                }
            }
            Decision.Pause -> show(LockActivity.intent(this, pkg, LockMode.Pause))
            is Decision.Limit -> show(LockActivity.intent(this, pkg, LockMode.Limit, limitMin = decision.limitMin, usedMs = decision.usedMs))
            is Decision.Focus -> show(LockActivity.intent(this, pkg, LockMode.Focus, focusEnd = decision.endMinute))
        }
    }

    private fun show(intent: android.content.Intent) {
        // Forget the current app so coming back to it (e.g. via Recents) is checked again.
        currentPkg = null
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        val TRANSIENT = setOf("com.android.systemui", "android")
    }
}
