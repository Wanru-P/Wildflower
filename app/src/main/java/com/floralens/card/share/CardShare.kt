package com.floralens.card.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.floralens.card.export.CardPngWriter
import com.floralens.card.export.PngDestination
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

class CardShare @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val directory get() = File(context.cacheDir, "card-share").apply { check(isDirectory || mkdirs()) }
    fun cleanup(now: Long = System.currentTimeMillis()) {
        // Seven-day grace; a new chooser or receiver never loses its freshly granted file.
        directory.listFiles()?.filter { it.isFile && it.name.startsWith("card-") && it.extension == "png" && now-it.lastModified() > 7L*24*60*60*1000 }
            ?.forEach { it.delete() }
    }
    fun prepare(bitmap: Bitmap): Uri {
        cleanup()
        val file = File(directory, "card-${UUID.randomUUID()}.png")
        CardPngWriter.write(bitmap, object : PngDestination {
            override fun open() = FileOutputStream(file)
            override fun complete() { check(file.length() > 0) }
            override fun abort() { file.delete() }
        })
        return FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
    }
    fun intent(uri: Uri): Intent {
        require(uri.scheme == "content" && uri.authority == "${context.packageName}.photos" && uri.path?.startsWith("/card-share/") == true)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "植物诗笺", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
