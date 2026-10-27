package com.floralens.card.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.floralens.domain.card.CardTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject

/** One newly created output, with compensating deletion. Never targets an observation file. */
interface PngDestination {
    fun open(): OutputStream
    fun complete()
    fun abort()
}

object CardPngWriter {
    fun write(bitmap: Bitmap, destination: PngDestination) {
        try {
            require(bitmap.width == CardTemplate.WIDTH && bitmap.height == CardTemplate.HEIGHT)
            destination.open().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG 编码失败" }
                output.flush()
            }
            destination.complete()
        } catch(e: Throwable) {
            runCatching { destination.abort() }
            throw e
        }
    }
}

class CardExporter @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun fileName() = "Wildflower_${System.currentTimeMillis()}_${UUID.randomUUID()}.png"
    fun discardCreatedDocument(uri: Uri) {
        if(uri.scheme == "content" && DocumentsContract.isDocumentUri(context, uri))
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    fun saveGallery(bitmap: Bitmap): Uri {
        check(Build.VERSION.SDK_INT >= 29) { "请通过系统文件选择器保存 PNG" }
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName())
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Wildflower Lens/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })) { "无法创建相册文件" }
        CardPngWriter.write(bitmap, object : PngDestination {
            override fun open() = requireNotNull(resolver.openOutputStream(uri, "w")) { "无法写入 PNG" }
            override fun complete() { check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1) { "PNG 未完成发布" } }
            override fun abort() { resolver.delete(uri, null, null) }
        })
        return uri
    }

    /** URI is the result of ACTION_CREATE_DOCUMENT only, never an imported/original photo URI. */
    fun saveDocument(bitmap: Bitmap, createdDocument: Uri): Uri {
        require(createdDocument.scheme == "content" && DocumentsContract.isDocumentUri(context, createdDocument)) { "请重新选择 PNG 保存位置" }
        CardPngWriter.write(bitmap, object : PngDestination {
            override fun open() = requireNotNull(context.contentResolver.openOutputStream(createdDocument, "w")) { "文件位置无法写入" }
            override fun complete() = Unit
            override fun abort() { DocumentsContract.deleteDocument(context.contentResolver, createdDocument) }
        })
        return createdDocument
    }
}
