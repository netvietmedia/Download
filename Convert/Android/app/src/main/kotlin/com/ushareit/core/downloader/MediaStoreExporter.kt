package com.ushareit.core.downloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object MediaStoreExporter {

    private const val TAG = "MediaStoreExporter"
    private const val ALBUM_NAME = "ShareNow"

    fun exportVideoToGallery(
        context: Context,
        sourceFile: File,
        videoTitle: String? = null
    ): Uri? {
        if (!sourceFile.exists() || sourceFile.length() <= 0) {
            Log.e(TAG, "Source file does not exist or is empty: ${sourceFile.absolutePath}")
            return null
        }

        val cleanTitle = videoTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: sourceFile.nameWithoutExtension.ifEmpty { "Video_${System.currentTimeMillis()}" }

        val ext = if (sourceFile.extension.equals("mp3", ignoreCase = true)) "mp3" else "mp4"
        val safeFileName = cleanTitle.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .removeSuffix(".mp4").removeSuffix(".mp3")
            .take(60) + ".$ext"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val isAudio = (ext == "mp3")
                val contentValues = ContentValues().apply {
                    if (isAudio) {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, safeFileName)
                        put(MediaStore.Audio.Media.TITLE, cleanTitle)
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$ALBUM_NAME")
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    } else {
                        put(MediaStore.Video.Media.DISPLAY_NAME, safeFileName)
                        put(MediaStore.Video.Media.TITLE, cleanTitle)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM_NAME")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val collectionUri = if (isAudio) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val destUri = resolver.insert(collectionUri, contentValues)

                if (destUri != null) {
                    resolver.openOutputStream(destUri)?.use { outStream ->
                        FileInputStream(sourceFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }

                    contentValues.clear()
                    if (isAudio) {
                        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    } else {
                        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    resolver.update(destUri, contentValues, null, null)

                    Log.i(TAG, "Successfully exported media to MediaStore: $destUri")
                    return destUri
                }
            } else {
                val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), ALBUM_NAME)
                if (!moviesDir.exists()) {
                    moviesDir.mkdirs()
                }

                val destFile = File(moviesDir, safeFileName)
                FileInputStream(sourceFile).use { inStream ->
                    FileOutputStream(destFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }

                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(destFile.absolutePath),
                    arrayOf(if (ext == "mp3") "audio/mpeg" else "video/mp4")
                ) { path, uri ->
                    Log.i(TAG, "MediaScanner scanned $path -> $uri")
                }

                return Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting video to MediaStore", e)
        }
        return null
    }
}

