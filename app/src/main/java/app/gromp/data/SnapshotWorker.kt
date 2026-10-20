package app.gromp.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.gromp.GrompApp
import java.util.concurrent.TimeUnit

/**
 * Android keeps detailed usage events for only a few days, so Gromp copies them
 * into its own database every hour. This is what makes month and year views possible.
 */
class SnapshotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as GrompApp).container.repository
        return runCatching { repo.refresh(daysBack = 2) }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val NAME = "gromp-snapshot"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SnapshotWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
