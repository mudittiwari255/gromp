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
import androidx.compose.runtime.setValue
import app.gromp.container
import app.gromp.ui.theme.GrompTheme

enum class LockMode { Pause, Limit, Focus }

data class LockRequest(
    val pkg: String,
    val mode: LockMode,
    val limitMin: Int,
    val usedMs: Long,
    val focusEnd: Int,
    /** Set when a shared category limit (rather than the app's own) was reached. */
    val category: String? = null,
)

/** Full-screen pause / limit / focus screen shown on top of a locked app. */
class LockActivity : ComponentActivity() {
    private var request by mutableStateOf<LockRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        request = parse(intent)
        if (request == null) { finish(); return }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        setContent {
            GrompTheme {
                val r = request ?: return@GrompTheme
                val c = container
                LockScreen(
                    request = r,
                    label = c.catalog.label(r.pkg),
                    icon = c.catalog.icon(r.pkg),
                    snoozesToday = c.grants.snoozes(r.category?.let { "cat:$it" } ?: r.pkg),
                    onLeave = ::leave,
                    onContinue = { proceed(r) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parse(intent)?.let { request = it }
    }

    private fun proceed(r: LockRequest) {
        val grants = container.grants
        when (r.mode) {
            LockMode.Pause -> grants.passPause(r.pkg)
            LockMode.Limit -> if (r.category != null) grants.snoozeCategory(r.category) else grants.snoozeLimit(r.pkg)
            LockMode.Focus -> grants.snoozeFocus(r.pkg)
        }
        packageManager.getLaunchIntentForPackage(r.pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(it) }
        }
        finish()
    }

    /** Close the locked app by going to the home screen. */
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

        fun intent(
            context: Context, pkg: String, mode: LockMode,
            limitMin: Int = 0, usedMs: Long = 0, focusEnd: Int = 0, category: String? = null,
        ): Intent =
            Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_LIMIT, limitMin)
                .putExtra(EXTRA_USED, usedMs)
                .putExtra(EXTRA_FOCUS_END, focusEnd)
                .putExtra(EXTRA_CATEGORY, category)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

        private fun parse(i: Intent?): LockRequest? {
            val pkg = i?.getStringExtra(EXTRA_PKG) ?: return null
            val mode = runCatching { LockMode.valueOf(i.getStringExtra(EXTRA_MODE) ?: "") }.getOrNull() ?: return null
            return LockRequest(pkg, mode, i.getIntExtra(EXTRA_LIMIT, 0), i.getLongExtra(EXTRA_USED, 0), i.getIntExtra(EXTRA_FOCUS_END, 0), i.getStringExtra(EXTRA_CATEGORY))
        }
    }
}
