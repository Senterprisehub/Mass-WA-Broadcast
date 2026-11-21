package com.example.util

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object MediaAttachmentHelper {

    private const val MEDIA_DIR_NAME = "campaign_media"

    fun getMediaDirectory(context: Context): File {
        return File(context.filesDir, MEDIA_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Copies a zero-permission Photo Picker (PickVisualMedia) URI into persistent internal
     * app storage (`filesDir/campaign_media/`) so scheduled broadcasts days later still have read access.
     */
    fun stageMediaToInternalStorage(context: Context, sourceUri: Uri): String? {
        return runCatching {
            val mimeType = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            val destFile = File(getMediaDirectory(context), "media_${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            destFile.absolutePath
        }.getOrNull()
    }

    fun getShareableUri(context: Context, storedPathOrUri: String): Uri? {
        if (storedPathOrUri.isBlank()) return null
        val file = File(storedPathOrUri)
        if (file.exists()) {
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
        return runCatching { Uri.parse(storedPathOrUri) }.getOrNull()
    }

    fun resolveMimeType(context: Context, storedPathOrUri: String): String {
        val ext = storedPathOrUri.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        val uri = runCatching { Uri.parse(storedPathOrUri) }.getOrNull()
        return uri?.let { context.contentResolver.getType(it) } ?: "image/*"
    }

    /**
     * Deletes staged files in `filesDir/campaign_media/` that are no longer referenced by any campaign.
     */
    fun collectOrphanedMedia(context: Context, referencedPaths: Set<String>): Int {
        val dir = getMediaDirectory(context)
        val files = dir.listFiles() ?: return 0
        var removed = 0
        for (file in files) {
            if (file.isFile && file.absolutePath !in referencedPaths) {
                if (file.delete()) removed++
            }
        }
        return removed
    }
}
