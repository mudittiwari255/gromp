package app.gromp

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.gromp.data.AppCatalog
import app.gromp.data.Backup
import app.gromp.data.Prefs
import app.gromp.data.Notifier
import app.gromp.data.NudgeWorker
import app.gromp.data.Repository
import app.gromp.data.SmartRepository
import app.gromp.lock.NudgeState
import app.gromp.data.SnapshotWorker
import app.gromp.data.db.GrompDatabase
import app.gromp.lock.LockGrants

/** Hand-wired dependencies. Small enough that a DI framework would only add weight. */
class AppContainer(context: Context) {
    val prefs = Prefs(context)
    val db = GrompDatabase.create(context)
    val catalog = AppCatalog(context)
    val repository = Repository(context, db.dao(), catalog, prefs)
    val backup = Backup(db.dao(), prefs)
    val grants = LockGrants()
    val smart = SmartRepository(db.dao(), repository, catalog, prefs)
    val nudges = NudgeState()
    val notifier = Notifier(context)
    /** For small writes that must finish even if the screen that started them closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class GrompApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.prefs.installedAt // stamp first launch
        SnapshotWorker.schedule(this)
        container.notifier.createChannels()
        NudgeWorker.schedule(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as GrompApp).container

object BuildConfigInfo {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
}
