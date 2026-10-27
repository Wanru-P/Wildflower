package com.floralens.data.photos

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class CaptureCleanupWorker @AssistedInject constructor(@Assisted context: Context, @Assisted parameters: WorkerParameters,
    private val archive: ArchiveService, private val captures: CaptureStore, private val input: PhotoInput) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try { archive.recover(); captures.cleanup(); input.cleanupCamera(); Result.success() } catch (_: Exception) { Result.retry() }
    companion object {
        fun schedule(context: Context) = WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "wildflower-capture-cleanup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CaptureCleanupWorker>(12, TimeUnit.HOURS).build())
    }
}
