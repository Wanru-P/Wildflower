package com.floralens.data.photos

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Photo Picker and system-camera inputs are copied, never stored as external URI references. */
@Singleton
class PhotoInput @Inject constructor(@param:ApplicationContext private val context: Context, private val captures: CaptureStore) {
    fun cameraOutput(): Uri {
        val dir = File(context.cacheDir, "observation-camera").apply { mkdirs() }
        return FileProvider.getUriForFile(context, "${context.packageName}.photos", File(dir, "${UUID.randomUUID()}.jpg"))
    }
    suspend fun importPhoto(uri: Uri): PendingCapture = withContext(Dispatchers.IO) {
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取所选照片，请重新选择" }
        val bytes = input.use { source ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(32 * 1024)
            while(true) {
                val read = source.read(buffer); if(read < 0) break
                require(output.size().toLong() + read <= 32L * 1024 * 1024) { "照片超过 32 MB，请选择较小的图片" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        captures.create(bytes)
    }
    fun releaseCamera(uri: Uri) {
        if(uri.authority != "${context.packageName}.photos") return
        val name = uri.lastPathSegment ?: return
        if(name.matches(Regex("[0-9a-f-]{36}\\.jpg"))) File(context.cacheDir, "observation-camera/$name").delete()
    }
    fun cleanupCamera() {
        File(context.cacheDir, "observation-camera").listFiles().orEmpty()
            .filter { it.name.matches(Regex("[0-9a-f-]{36}\\.jpg")) && System.currentTimeMillis() - it.lastModified() > PendingCaptureStore.TTL }
            .forEach { it.delete() }
    }
}
