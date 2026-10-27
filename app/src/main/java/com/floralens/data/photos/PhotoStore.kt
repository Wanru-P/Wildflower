package com.floralens.data.photos

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import android.graphics.BitmapFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists captured plant photos. Extracted as an interface (same pattern as
 * CareDefaults) so IdentifyViewModel is unit-testable without a real
 * filesystem/Context — swap in a fake in tests.
 */
interface PhotoStore {
    /** Writes JPEG bytes and returns a persistable URI string. */
    suspend fun save(imageBytes: ByteArray): String

    /** Deletes a previously saved photo; safe to call with any URI string. */
    suspend fun delete(uriString: String)
}

/**
 * Real implementation — app-internal storage (filesDir/photos).
 * Internal storage keeps the privacy promise: no MediaStore, no shared
 * storage, no permissions — photos are only ever visible to FloraLens.
 */
@Singleton
class FilePhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoStore {
    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    override suspend fun save(imageBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val file = target(UUID.randomUUID().toString())
        write(file.toURI().toString(), imageBytes.inputStream())
        file.toURI().toString()
    }

    override suspend fun delete(uriString: String) {
        withContext(Dispatchers.IO) {
            owned(uriString)?.delete()
        }
    }

    fun target(operationId: String): File {
        require(operationId.matches(Regex("[a-zA-Z0-9-]{1,100}")))
        return File(photosDir, "photo_$operationId.jpg")
    }

    private fun owned(uri: String): File? = runCatching {
        File(java.net.URI(uri)).canonicalFile.takeIf { it.parentFile == photosDir.canonicalFile }
    }.getOrNull()

    /** Bounded copy + decoder validation + atomic rename BEFORE a database reference is committed. */
    suspend fun write(uri: String, input: java.io.InputStream) = withContext(Dispatchers.IO) {
        val file = requireNotNull(owned(uri)) { "照片目标不属于应用存储" }
        val temporary = File(file.parentFile, file.name + ".part")
        try {
            input.use { source -> FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(32 * 1024); var total = 0L
                while (true) {
                    val read = source.read(buffer); if (read < 0) break
                    total += read; require(total <= 32L * 1024 * 1024) { "照片超过 32 MB，请选择较小的图片" }
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            } }
            validate(temporary)
            check(!file.exists() && temporary.renameTo(file)) { "照片写入失败" }
        } finally { temporary.delete() }
    }

    fun validate(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 80_000_000) { "照片无法读取或尺寸过大" }
        val sample = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (options.outWidth / inSampleSize > 512 || options.outHeight / inSampleSize > 512) inSampleSize *= 2
        }
        requireNotNull(BitmapFactory.decodeFile(file.absolutePath, sample)) { "照片内容无法解码" }.recycle()
    }
}
