package com.floralens

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.floralens.data.seed.DemoSeeder
import com.floralens.reminders.WateringWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.floralens.BuildConfig
import javax.inject.Inject

@HiltAndroidApp
class FloraLensApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var demoSeeder: DemoSeeder
    @Inject lateinit var archive: com.floralens.data.photos.ArchiveService
    @Inject lateinit var captures: com.floralens.data.photos.CaptureStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch { runCatching { archive.recover(); captures.cleanup() }.onFailure { android.util.Log.e("Wildflower", "照片恢复未完成，将在下一次操作/后台任务重试", it) } }
        com.floralens.data.photos.CaptureCleanupWorker.schedule(this)
        if (BuildConfig.DEBUG && BuildConfig.SEED_DEMO) {
            appScope.launch { demoSeeder.seedIfEmpty() } // Marlowe & friends
        }
        WateringWorker.schedule(this)
    }
}
