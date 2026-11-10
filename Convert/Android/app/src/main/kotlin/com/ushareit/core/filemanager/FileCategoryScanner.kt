package com.ushareit.core.filemanager

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ushareit.core.p2p.P2PTransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class FileCategory {
    APP, VIDEO, PHOTO, MUSIC, DOCUMENT, ZIP
}

data class FileItem(
    val file: File,
    val name: String,
    val size: Long,
    val category: FileCategory,
    val lastModified: Long,
    val uri: Uri? = null,
    val durationText: String = "",
    val packageName: String = ""
)

class FileCategoryScanner(private val context: Context) {

    suspend fun scanCategory(category: FileCategory): List<FileItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FileItem>()

        // 1. Quét Ứng dụng (APP)
        if (category == FileCategory.APP) {
            try {
                val pm = context.packageManager
                val seenPackages = mutableSetOf<String>()

                val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveList = pm.queryIntentActivities(launcherIntent, 0)

                for (info in resolveList) {
                    val pkg = info.activityInfo.packageName
                    if (seenPackages.contains(pkg)) continue
                    seenPackages.add(pkg)

                    val appInfo = info.activityInfo.applicationInfo
                    val srcFile = File(appInfo.sourceDir)
                    val appLabel = info.loadLabel(pm).toString().trim()
                    val fileName = if (appLabel.isNotBlank()) "$appLabel.apk" else "$pkg.apk"
                    val size = if (srcFile.exists() && srcFile.length() > 0) srcFile.length() else 35_000_000L
                    val date = if (srcFile.exists()) srcFile.lastModified() else System.currentTimeMillis()

                    result.add(FileItem(srcFile, fileName, size, FileCategory.APP, date, Uri.fromFile(srcFile), packageName = pkg))
                }

                try {
                    val installedApps = pm.getInstalledApplications(0)
                    for (app in installedApps) {
                        if (!seenPackages.contains(app.packageName)) {
                            val isSystem = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                            if (!isSystem || launchIntent != null) {
                                seenPackages.add(app.packageName)
                                val srcFile = File(app.sourceDir)
                                val appLabel = pm.getApplicationLabel(app).toString().trim()
                                val fileName = if (appLabel.isNotBlank()) "$appLabel.apk" else "${app.packageName}.apk"
                                val size = if (srcFile.exists() && srcFile.length() > 0) srcFile.length() else 25_000_000L
                                result.add(FileItem(srcFile, fileName, size, FileCategory.APP, srcFile.lastModified(), Uri.fromFile(srcFile), packageName = app.packageName))
                            }
                        }
                    }
                } catch (_: Throwable) {}

                // Quét thêm các tệp APK trong thư mục nhận file / SharedPool
                try {
                    val extraApkDirs = listOfNotNull(
                        P2PTransferManager.getReceivedFilesDir(),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        SharedFilePool.getPoolDir(context, FileCategory.APP)
                    )
                    for (dir in extraApkDirs) {
                        val apkFiles = scanDirectory(dir, FileCategory.APP, maxDepth = 2)
                        for (apkItem in apkFiles) {
                            var pkg = ""
                            try {
                                val pi = pm.getPackageArchiveInfo(apkItem.file.absolutePath, 0)
                                if (pi != null && !pi.packageName.isNullOrBlank()) {
                                    pkg = pi.packageName
                                }
                            } catch (_: Throwable) {}
                            result.add(apkItem.copy(packageName = pkg))
                        }
                    }
                } catch (_: Throwable) {}

                return@withContext deduplicateAndSort(result).sortedBy { it.name.lowercase() }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return@withContext result
        }

        // 2. Quét từ SharedFilePool (tệp đã chọn qua System Picker & thư mục SAF đã cấp quyền)
        try {
            result.addAll(SharedFilePool.scanPooledFiles(context, category))
        } catch (_: Throwable) {}

        // 3. Quét MediaStore chính theo từng danh mục
        val primaryMediaUri = when (category) {
            FileCategory.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            FileCategory.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            FileCategory.MUSIC -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            FileCategory.DOCUMENT, FileCategory.ZIP -> MediaStore.Files.getContentUri("external")
            else -> null
        }

        if (primaryMediaUri != null) {
            result.addAll(scanMediaStore(primaryMediaUri, category))
        }

        // 4. Quét bổ sung MediaStore.Downloads (API 29+) & MediaStore.Files cho mọi danh mục
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                result.addAll(scanMediaStore(MediaStore.Downloads.EXTERNAL_CONTENT_URI, category))
            } catch (_: Throwable) {}
        }
        if (category != FileCategory.DOCUMENT && category != FileCategory.ZIP) {
            try {
                result.addAll(scanMediaStore(MediaStore.Files.getContentUri("external"), category))
            } catch (_: Throwable) {}
        }

        // 5. LUÔN LUÔN quét các thư mục của ứng dụng và các thư mục công cộng trên máy
        val searchDirs = buildSearchDirectories(category)
        for (dir in searchDirs) {
            result.addAll(scanDirectory(dir, category, maxDepth = 3))
        }

        // 6. Quét thêm danh sách tệp trong P2PTransferManager.getAllReceivedFiles() phân loại theo category
        try {
            for (recvFile in P2PTransferManager.getAllReceivedFiles()) {
                if (recvFile.exists() && recvFile.isFile && recvFile.length() > 0L) {
                    val fileCat = categorizeFile(recvFile.name)
                    val isMatch = if (category == FileCategory.DOCUMENT) {
                        fileCat == FileCategory.DOCUMENT || fileCat == FileCategory.ZIP
                    } else {
                        fileCat == category
                    }
                    if (isMatch) {
                        result.add(
                            FileItem(
                                file = recvFile,
                                name = recvFile.name,
                                size = recvFile.length(),
                                category = category,
                                lastModified = recvFile.lastModified(),
                                uri = Uri.fromFile(recvFile)
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {}

        deduplicateAndSort(result)
    }

    private fun buildSearchDirectories(category: FileCategory): List<File> {
        val dirs = mutableListOf<File>()
        try {
            val appExtRoot = context.getExternalFilesDir(null)
            val filesRoot = context.filesDir
            val sdRoot = Environment.getExternalStorageDirectory()

            // Thư mục nội bộ & riêng của ứng dụng Share Now (luôn đọc được 100% trên Android 13-16)
            dirs.add(P2PTransferManager.getReceivedFilesDir())
            if (appExtRoot != null) {
                dirs.add(File(appExtRoot, "Videos"))
                dirs.add(File(appExtRoot, "Music"))
                dirs.add(File(appExtRoot, "Pictures"))
                dirs.add(File(appExtRoot, "Documents"))
                dirs.add(File(appExtRoot, "Download"))
                dirs.add(appExtRoot)
            }
            if (filesRoot != null) {
                dirs.add(File(filesRoot, "Videos"))
                dirs.add(File(filesRoot, "safebox_vault"))
            }
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { dirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { dirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { dirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { dirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dirs.add(it) }

            // Thư mục album ShareNow trong bộ nhớ chung
            dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ShareNow"))
            dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "ShareNow"))
            dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ShareNow"))
            dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ShareNow"))

            // Thư mục công cộng theo danh mục
            when (category) {
                FileCategory.PHOTO -> {
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
                    dirs.add(File(sdRoot, "DCIM/Camera"))
                    dirs.add(File(sdRoot, "DCIM/Screenshots"))
                    dirs.add(File(sdRoot, "Pictures/Screenshots"))
                    dirs.add(File(sdRoot, "Pictures/Telegram"))
                    dirs.add(File(sdRoot, "Pictures/Facebook"))
                    dirs.add(File(sdRoot, "Pictures/Messenger"))
                    dirs.add(File(sdRoot, "Pictures/Zalo"))
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                }
                FileCategory.VIDEO -> {
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
                    dirs.add(File(sdRoot, "DCIM/Camera"))
                    dirs.add(File(sdRoot, "Movies/Telegram"))
                    dirs.add(File(sdRoot, "Movies/Facebook"))
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                }
                FileCategory.MUSIC -> {
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))
                    dirs.add(File(sdRoot, "Music/Samsung"))
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS))
                    } else {
                        dirs.add(File(sdRoot, "Recordings"))
                        dirs.add(File(sdRoot, "Sounds"))
                    }
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS))
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES))
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS))
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))
                }
                FileCategory.DOCUMENT, FileCategory.ZIP -> {
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
                    dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                    dirs.add(File(sdRoot, "Download/OfficeDocument"))
                    dirs.add(File(sdRoot, "Download/Telegram"))
                    dirs.add(File(sdRoot, "Documents"))
                }
                else -> {}
            }
        } catch (_: Throwable) {}

        return dirs.distinctBy { it.absolutePath }
    }

    private fun scanMediaStore(uri: Uri, targetCategory: FileCategory): List<FileItem> {
        val list = mutableListOf<FileItem>()
        val projectionList = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE
        )

        val isDedicatedVideoUri = (uri == MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val isDedicatedAudioUri = (uri == MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)

        val durationColName = when {
            isDedicatedVideoUri -> MediaStore.Video.VideoColumns.DURATION
            isDedicatedAudioUri -> MediaStore.Audio.AudioColumns.DURATION
            else -> null
        }

        if (durationColName != null) {
            projectionList.add(durationColName)
        }

        val projection = projectionList.toTypedArray()

        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val durCol = if (durationColName != null) cursor.getColumnIndex(durationColName) else -1

                while (cursor.moveToNext() && list.size < 300) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else -1L
                    val rawName = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val dateSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val realPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    val durMs = if (durCol >= 0) cursor.getLong(durCol) else 0L

                    val derivedName = if (!rawName.isNullOrBlank()) {
                        rawName
                    } else if (!realPath.isNullOrBlank()) {
                        File(realPath).name
                    } else {
                        null
                    }

                    if (derivedName.isNullOrBlank() || derivedName.startsWith(".")) continue

                    // Xác định chính xác category thực tế của tệp (ngăn chặn file .mp4 lọt vào tab MUSIC)
                    val detectedCat = categorizeFile(derivedName, mimeType)
                    val matchesCategory = when (targetCategory) {
                        FileCategory.DOCUMENT -> detectedCat == FileCategory.DOCUMENT || detectedCat == FileCategory.ZIP
                        FileCategory.ZIP -> detectedCat == FileCategory.ZIP
                        FileCategory.MUSIC -> detectedCat == FileCategory.MUSIC && !isVideoFileName(derivedName)
                        FileCategory.VIDEO -> detectedCat == FileCategory.VIDEO || isVideoFileName(derivedName)
                        FileCategory.PHOTO -> detectedCat == FileCategory.PHOTO
                        else -> detectedCat == targetCategory
                    }
                    if (!matchesCategory) continue

                    val file = if (!realPath.isNullOrBlank()) File(realPath) else File(context.cacheDir, derivedName)
                    val actualSize = if (size > 0L) size else if (file.exists()) file.length() else 0L
                    if (actualSize <= 0L) continue

                    val contentUri = if (id >= 0) ContentUris.withAppendedId(uri, id) else null
                    val durationText = formatDuration(durMs)
                    val finalTimestamp = if (dateSec > 0) dateSec * 1000L else if (file.exists()) file.lastModified() else System.currentTimeMillis()

                    list.add(FileItem(file, derivedName, actualSize, targetCategory, finalTimestamp, contentUri, durationText))
                }
            }
        } catch (_: Throwable) {}
        return list
    }

    private fun scanDirectory(dir: File?, targetCategory: FileCategory, maxDepth: Int = 3): List<FileItem> {
        val list = mutableListOf<FileItem>()
        if (dir == null || !dir.exists() || !dir.canRead()) return list

        fun walk(current: File, depth: Int) {
            if (depth > maxDepth || list.size >= 200) return
            val files = current.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".") || f.name.endsWith(".part", ignoreCase = true)) continue
                if (f.isDirectory) {
                    walk(f, depth + 1)
                } else if (f.isFile && f.length() > 0L) {
                    val cat = categorizeFile(f.name)
                    val isMatch = when (targetCategory) {
                        FileCategory.DOCUMENT -> cat == FileCategory.DOCUMENT || cat == FileCategory.ZIP
                        else -> cat == targetCategory
                    }
                    if (isMatch) {
                        list.add(FileItem(f, f.name, f.length(), targetCategory, f.lastModified(), Uri.fromFile(f)))
                    }
                }
            }
        }

        try {
            walk(dir, 0)
        } catch (_: Throwable) {}
        return list
    }

    private fun deduplicateAndSort(items: List<FileItem>): List<FileItem> {
        if (items.isEmpty()) return emptyList()
        val grouped = items.groupBy { it.name.trim().lowercase() }
        val deduplicated = grouped.map { (_, group) ->
            // Ưu tiên phần tử có file vật lý tồn tại và đọc được trực tiếp trên đĩa
            group.firstOrNull { it.file.exists() && it.file.canRead() && it.file.length() > 0L }
                ?: group.firstOrNull { it.uri != null }
                ?: group.first()
        }
        return deduplicated.sortedByDescending { it.lastModified }
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return ""
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun isVideoFileName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") ||
            lower.endsWith(".mov") || lower.endsWith(".3gp") || lower.endsWith(".webm") ||
            lower.endsWith(".flv") || lower.endsWith(".ts") || lower.endsWith(".m4v")
    }

    private fun categorizeFile(name: String, mimeType: String = ""): FileCategory {
        val lower = name.lowercase()
        val mime = mimeType.lowercase()
        return when {
            lower.endsWith(".apk") || lower.endsWith(".xapk") || lower.endsWith(".apks") ||
                mime == "application/vnd.android.package-archive" -> FileCategory.APP

            isVideoFileName(lower) || (mime.startsWith("video/") && !lower.endsWith(".mp3") && !lower.endsWith(".m4a")) -> FileCategory.VIDEO

            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp") ||
                lower.endsWith(".heic") || lower.endsWith(".heif") || lower.endsWith(".avif") ||
                mime.startsWith("image/") -> FileCategory.PHOTO

            lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".flac") ||
                lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".ogg") ||
                lower.endsWith(".opus") || lower.endsWith(".wma") || lower.endsWith(".amr") ||
                (mime.startsWith("audio/") && !isVideoFileName(lower)) -> FileCategory.MUSIC

            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") ||
                lower.endsWith(".tar") || lower.endsWith(".gz") ||
                mime.contains("zip") || mime.contains("rar") || mime.contains("7z") || mime.contains("compressed") -> FileCategory.ZIP

            lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") ||
                lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".ppt") ||
                lower.endsWith(".pptx") || lower.endsWith(".txt") || lower.endsWith(".csv") ||
                lower.endsWith(".rtf") || lower.endsWith(".json") || lower.endsWith(".xml") ||
                lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".md") ||
                lower.endsWith(".wps") || lower.endsWith(".epub") || lower.endsWith(".mobi") ||
                mime.contains("pdf") || mime.contains("document") || mime.contains("msword") ||
                mime.contains("sheet") || mime.contains("excel") || mime.contains("presentation") ||
                mime.contains("powerpoint") || mime.startsWith("text/") -> FileCategory.DOCUMENT

            else -> FileCategory.DOCUMENT
        }
    }
}
