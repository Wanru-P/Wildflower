package com.floralens.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.floralens.data.repo.PlantRepository
import com.floralens.domain.model.CareStatus
import com.floralens.domain.usecase.ComputeCareStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Daily check: which plants are due/overdue → one gentle notification.
 * Deferrable + battery-friendly by design (WorkManager).
 */
@HiltWorker
class WateringWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val plants: PlantRepository,
    private val computeCareStatus: ComputeCareStatus,
    private val notifications: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val due = plants.observeAll().first()
            .filter { it.remindersEnabled }
            .filter { computeCareStatus(it) in setOf(CareStatus.DUE_TODAY, CareStatus.OVERDUE) }

        if (due.isNotEmpty()) {
            val first = due.first().nickname
            val body = if (due.size == 1) "今天，记得看看 $first 是否需要浇水。"
            else "$first 等 ${due.size} 株植物到了养护时间。"
            notifications.showCareReminder("草木知时 · 养护提醒", body)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "watering_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WateringWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
