package app.gromp.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

enum class Category(val label: String) {
    Social("Social"), Video("Video"), Messaging("Messaging"), Browsing("Browsing"),
    Games("Games"), Music("Music & audio"), Productivity("Productivity"), Other("Other")
}

data class AppEntry(val pkg: String, val label: String)

/** Names, icons and categories for installed apps. All lookups are local. */
class AppCatalog(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    fun label(pkg: String): String = labels.getOrPut(pkg) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun icon(pkg: String, sizePx: Int = 96): ImageBitmap? = icons[pkg] ?: try {
        pm.getApplicationIcon(pkg).toBitmap(sizePx, sizePx).asImageBitmap().also { icons[pkg] = it }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun category(pkg: String): Category {
        OVERRIDES.entries.firstOrNull { pkg.startsWith(it.key) }?.let { return it.value }
        val info = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { return Category.Other }
        return when (info.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> Category.Social
            ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> Category.Video
            ApplicationInfo.CATEGORY_GAME -> Category.Games
            ApplicationInfo.CATEGORY_AUDIO -> Category.Music
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> Category.Productivity
            ApplicationInfo.CATEGORY_NEWS -> Category.Browsing
            else -> Category.Other
        }
    }

    /** Launchable apps the user could put locks on, excluding Gromp, launchers and the dialer. */
    fun lockableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val never = neverLock()
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it !in never }
            .map { AppEntry(it, label(it)) }
            .sortedBy { it.label.lowercase() }
    }

    /** Packages that should never be recorded as "app time": home screens, Gromp, system UI. */
    fun ignoredForStats(): Set<String> = launchers() + setOf(context.packageName, "com.android.systemui")

    /** Packages that must never be locked, so nobody is cut off from calls or the keyboard. */
    fun neverLock(): Set<String> {
        val out = mutableSetOf(context.packageName, "com.android.systemui", "com.android.settings")
        out += launchers()
        (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage?.let { out += it }
        out += keyboards()
        out += setOf("com.android.phone", "com.android.server.telecom", "com.google.android.dialer", "com.android.emergency")
        return out
    }

    /** Installed keyboards. Their windows pop up inside other apps and must not count as switching apps. */
    fun keyboards(): Set<String> =
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()

    private fun launchers(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet()
    }

    private companion object {
        val OVERRIDES = linkedMapOf(
            "com.whatsapp" to Category.Messaging,
            "org.telegram" to Category.Messaging,
            "org.thoughtcrime.securesms" to Category.Messaging,
            "com.google.android.apps.messaging" to Category.Messaging,
            "com.facebook.orca" to Category.Messaging,
            "com.discord" to Category.Messaging,
            "com.snapchat" to Category.Social,
            "com.instagram" to Category.Social,
            "com.twitter" to Category.Social,
            "com.reddit" to Category.Social,
            "com.facebook.katana" to Category.Social,
            "com.linkedin" to Category.Social,
            "com.google.android.youtube" to Category.Video,
            "com.netflix" to Category.Video,
            "in.startv.hotstar" to Category.Video,
            "com.amazon.avod" to Category.Video,
            "com.zhiliaoapp.musically" to Category.Video,
            "com.android.chrome" to Category.Browsing,
            "org.mozilla" to Category.Browsing,
            "com.brave.browser" to Category.Browsing,
            "com.opera" to Category.Browsing,
            "com.sec.android.app.sbrowser" to Category.Browsing,
            "com.spotify" to Category.Music,
        )
    }
}
