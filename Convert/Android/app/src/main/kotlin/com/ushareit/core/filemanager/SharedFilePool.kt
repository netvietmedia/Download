package com.ushareit.core.filemanager

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * SharedFilePool - Bộ nhớ lưu trữ và đồng bộ các tệp Ảnh, Video, Nhạc, Tài liệu và Thư mục (SAF)
 * do người dùng chọn qua System Photo Picker / File Picker hoặc cấp quyền thư mục.
 * Đảm bảo tuân thủ 100% chính sách Google Play trên Android 13/14/15/16 (API 33-36)
 * đồng thời giúp WebShare PC và SendActivity truy cập đầy đủ mọi loại tệp.
 */
object SharedFilePool {

    private const val TAG = "SharedFilePool"
    private const val KEY_TREE_URIS = "shared_pool_tree_uris_v1"

    fun getPoolDir(context: Context, category: FileCategory): File {
        val baseDir = context.applicationContext.getExternalFilesDir("SharedPool")
            ?: File(context.applicationContext.filesDir, "SharedPool")
        val subDir = File(baseDir, category.name)
        if (!subDir.exists()) {
            subDir.mkdirs()
        }
        return subDir
    }

    /**
     * Đăng ký danh sách URI được chọn từ System Photo Picker hoặc Document Picker.
     * Sao lưu bản sao vào thư mục SharedPool của ứng dụng để WebShare PC có thể stream/download/zip
     * với tốc độ cao và không bị mất quyền truy cập sau khi khởi động lại máy.
     */
    fun addPickedUris(context: Context, uris: List<Uri>, onComplete: ((Int) -> Unit)? = null) {
        if (uris.isEmpty()) {
            onComplete?.invoke(0)
            return
        }
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            var addedCount = 0
            val cr = appContext.contentResolver
            for (uri in uris) {
                try {
                    try {
                        cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Throwable) {}

                    var displayName = ""
                    var size = 0L
                    val mimeType = cr.getType(uri) ?: ""

                    cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: ""
                            if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                        }
                    }

                    if (displayName.isBlank()) {
                        val ext = when {
                            mimeType.startsWith("image/") -> "." + mimeType.substringAfter("image/").substringBefore(";").ifBlank { "jpg" }
                            mimeType.startsWith("video/") -> "." + mimeType.substringAfter("video/").substringBefore(";").ifBlank { "mp4" }
                            mimeType.startsWith("audio/") -> "." + mimeType.substringAfter("audio/").substringBefore(";").ifBlank { "mp3" }
                            mimeType.contains("pdf") -> ".pdf"
                            else -> ""
                        }
                        displayName = "Shared_${System.currentTimeMillis()}$ext"
                    }

                    val sanitizedName = displayName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                    val category = classifyFile(sanitizedName, mimeType)
                    val destDir = getPoolDir(appContext, category)
                    val destFile = File(destDir, sanitizedName)

                    if (!destFile.exists() || (size > 0L && destFile.length() != size)) {
                        cr.openInputStream(uri)?.use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output, 64 * 1024)
                            }
                        }
                    }

                    if (destFile.exists() && destFile.length() > 0L) {
                        addedCount++
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to import URI $uri: ${e.message}")
                }
            }

            try {
                com.ushareit.core.webshare.WebShareManager.refreshFileCache()
            } catch (_: Throwable) {}

            if (onComplete != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(addedCount)
                }
            }
        }
    }

    /**
     * Lưu URI thư mục được cấp quyền qua ACTION_OPEN_DOCUMENT_TREE
     */
    fun addGrantedTreeUri(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Throwable) {
            Log.w(TAG, "takePersistableUriPermission warning: ${e.message}")
        }

        try {
            val kv = MMKV.defaultMMKV()
            val existing = kv?.decodeStringSet(KEY_TREE_URIS)?.toMutableSet() ?: mutableSetOf()
            existing.add(treeUri.toString())
            kv?.encode(KEY_TREE_URIS, existing)
            com.ushareit.core.webshare.WebShareManager.refreshFileCache()
        } catch (e: Throwable) {
            Log.w(TAG, "Save treeUri warning: ${e.message}")
        }
    }

    /**
     * Quét toàn bộ tệp đã được lưu trong SharedPool và các thư mục SAF đã cấp quyền theo danh mục
     */
    fun scanPooledFiles(context: Context, targetCategory: FileCategory): List<FileItem> {
        val result = mutableListOf<FileItem>()

        // 1. Quét các tệp vật lý trong thư mục SharedPool
        try {
            val categoriesToCheck = if (targetCategory == FileCategory.DOCUMENT) {
                listOf(FileCategory.DOCUMENT, FileCategory.ZIP)
            } else {
                listOf(targetCategory)
            }
            for (cat in categoriesToCheck) {
                val dir = getPoolDir(context, cat)
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    if (f.isFile && f.length() > 0L && !f.name.startsWith(".")) {
                        result.add(
                            FileItem(
                                file = f,
                                name = f.name,
                                size = f.length(),
                                category = targetCategory,
                                lastModified = f.lastModified(),
                                uri = Uri.fromFile(f)
                            )
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "scanPooledFiles dir error: ${e.message}")
        }

        // 2. Quét các thư mục SAF đã được người dùng cấp quyền (persistedUriPermissions)
        try {
            val treeStrings = mutableSetOf<String>()
            try {
                context.contentResolver.persistedUriPermissions
                    .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
                    .forEach { treeStrings.add(it.uri.toString()) }
            } catch (_: Throwable) {}

            try {
                MMKV.defaultMMKV()?.decodeStringSet(KEY_TREE_URIS)?.let { treeStrings.addAll(it) }
            } catch (_: Throwable) {}

            for (treeStr in treeStrings) {
                val treeUri = Uri.parse(treeStr)
                result.addAll(scanDocumentTree(context, treeUri, targetCategory))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "scanPooledFiles SAF error: ${e.message}")
        }

        return result
    }

    private fun scanDocumentTree(context: Context, treeUri: Uri, targetCategory: FileCategory): List<FileItem> {
        val items = mutableListOf<FileItem>()
        val cr = context.contentResolver

        fun walkDocument(docId: String, depth: Int) {
            if (depth > 3 || items.size >= 250) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            var cursor: Cursor? = null
            try {
                cursor = cr.query(childrenUri, projection, null, null, null)
                if (cursor != null) {
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val dateIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext() && items.size < 250) {
                        val childId = if (idIdx >= 0) cursor.getString(idIdx) ?: continue else continue
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                        if (name.isBlank() || name.startsWith(".")) continue
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        val lastMod = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            walkDocument(childId, depth + 1)
                        } else if (size > 0L) {
                            val fileCat = classifyFile(name, mime)
                            val matches = if (targetCategory == FileCategory.DOCUMENT) {
                                fileCat == FileCategory.DOCUMENT || fileCat == FileCategory.ZIP
                            } else {
                                fileCat == targetCategory
                            }
                            if (matches) {
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                val fallbackFile = File(context.cacheDir, name)
                                items.add(
                                    FileItem(
                                        file = fallbackFile,
                                        name = name,
                                        size = size,
                                        category = targetCategory,
                                        lastModified = if (lastMod > 0L) lastMod else System.currentTimeMillis(),
                                        uri = docUri
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { cursor?.close() } catch (_: Throwable) {}
            }
        }

        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            walkDocument(rootDocId, 0)
        } catch (_: Throwable) {}

        return items
    }

    fun classifyFile(name: String, mimeType: String = ""): FileCategory {
        val lower = name.lowercase()
        val mime = mimeType.lowercase()
        return when {
            lower.endsWith(".apk") || lower.endsWith(".xapk") || lower.endsWith(".apks") || mime == "application/vnd.android.package-archive" -> FileCategory.APP
            lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".mov") ||
                lower.endsWith(".3gp") || lower.endsWith(".webm") || lower.endsWith(".flv") || lower.endsWith(".ts") ||
                lower.endsWith(".m4v") || mime.startsWith("video/") -> FileCategory.VIDEO
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".heic") || lower.endsWith(".heif") ||
                lower.endsWith(".avif") || mime.startsWith("image/") -> FileCategory.PHOTO
            lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".flac") || lower.endsWith(".wav") ||
                lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".opus") || lower.endsWith(".wma") ||
                lower.endsWith(".amr") || mime.startsWith("audio/") -> FileCategory.MUSIC
            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar") ||
                lower.endsWith(".gz") || mime.contains("zip") || mime.contains("rar") || mime.contains("7z") -> FileCategory.ZIP
            else -> FileCategory.DOCUMENT
        }
    }
}

