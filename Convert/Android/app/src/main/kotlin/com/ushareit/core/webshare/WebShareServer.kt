package com.ushareit.core.webshare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.webkit.MimeTypeMap
import com.google.gson.Gson
import com.ushareit.core.filemanager.FileCategory
import com.ushareit.core.filemanager.FileCategoryScanner
import com.ushareit.core.filemanager.FileItem
import com.ushareit.core.p2p.P2PTransferManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * WebShareServer - Máy chủ Web HTTP phục vụ truyền nhận tệp giữa Điện thoại và Máy tính (PC/Mac/iOS)
 * Tương thích cơ chế AirDroid / SHAREit WebShare qua Wi-Fi cục bộ hoặc Hotspot.
 */
class WebShareServer(
    private val context: Context,
    val port: Int = 8888,
    var listener: WebShareListener? = null
) {

    interface WebShareListener {
        fun onServerStarted(ip: String, port: Int)
        fun onServerStopped()
        fun onClientConnected(clientIp: String)
        fun onFileDownloaded(fileName: String, bytes: Long)
        fun onFileUploaded(file: File)
        fun onError(error: String)
        fun onPcRequestFilePicker(category: String) {}
    }

    private var serverSocket: ServerSocket? = null
    @Volatile
    var isRunning = false
        private set

    private val threadPool = Executors.newCachedThreadPool()
    private val gson = Gson()
    private val scanner = FileCategoryScanner(context)

    // Bộ nhớ đệm danh sách tệp theo danh mục để phản hồi tức thì
    private val fileCache = ConcurrentHashMap<String, List<FileItem>>()
    // Bộ nhớ đệm icon app đã nén PNG theo packageName để phản hồi siêu tốc (<1ms)
    private val appIconCache = ConcurrentHashMap<String, ByteArray>()
    // Bộ nhớ đệm thumbnail Ảnh & Video đã nén JPEG để tải lưới ảnh mượt mà
    private val mediaThumbCache = ConcurrentHashMap<String, ByteArray>()

    fun start() {
        if (isRunning) return
        threadPool.execute {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                val ip = P2PTransferManager.getLocalIpAddress(context)
                Log.i(TAG, "WebShare HTTP Server started at http://$ip:$port")
                listener?.onServerStarted(ip, port)

                // Tiền tải trước danh sách tệp trong nền
                preloadFiles()

                while (isRunning) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        val clientIp = socket.inetAddress.hostAddress ?: "Unknown"
                        listener?.onClientConnected(clientIp)
                        threadPool.execute {
                            handleClientSocket(socket)
                        }
                    } catch (e: SocketException) {
                        if (!isRunning) break
                        Log.e(TAG, "SocketException: ${e.message}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server start error: ${e.message}")
                listener?.onError(e.message ?: "Failed to start server")
            } finally {
                isRunning = false
                listener?.onServerStopped()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        fileCache.clear()
        appIconCache.clear()
        mediaThumbCache.clear()
        Log.i(TAG, "WebShare HTTP Server stopped")
    }

    fun clearCache(category: String? = null) {
        if (category.isNullOrBlank()) {
            fileCache.clear()
            preloadFiles()
        } else {
            fileCache.remove(category.uppercase())
        }
    }

    private fun preloadFiles() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fileCache["PHOTO"] = scanner.scanCategory(FileCategory.PHOTO)
                fileCache["VIDEO"] = scanner.scanCategory(FileCategory.VIDEO)
                fileCache["MUSIC"] = scanner.scanCategory(FileCategory.MUSIC)
                fileCache["DOCUMENT"] = scanner.scanCategory(FileCategory.DOCUMENT)
                fileCache["APP"] = scanner.scanCategory(FileCategory.APP)
            } catch (e: Exception) {
                Log.w(TAG, "Preload files warning: ${e.message}")
            }
        }
    }

    private fun handleClientSocket(socket: Socket) {
        var inputStream: BufferedInputStream? = null
        var outputStream: BufferedOutputStream? = null
        try {
            socket.soTimeout = 60000 // 60s timeout for large file transfers
            try {
                socket.setSoLinger(true, 5)
            } catch (e: Exception) {}

            inputStream = BufferedInputStream(socket.getInputStream())
            outputStream = BufferedOutputStream(socket.getOutputStream())

            val headers = mutableMapOf<String, String>()
            val requestLine = readLine(inputStream) ?: return
            val requestParts = requestLine.split(" ")
            if (requestParts.size < 2) return

            val method = requestParts[0].uppercase()
            val rawPath = requestParts[1]

            // Đọc Headers
            while (true) {
                val line = readLine(inputStream) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            // Xử lý CORS Preflight
            if (method == "OPTIONS") {
                sendCorsOk(outputStream)
                return
            }

            // Tách Path và Query
            val urlParts = rawPath.split("?", limit = 2)
            val path = urlParts[0]
            val queryParams = if (urlParts.size > 1) parseQueryParams(urlParts[1]) else emptyMap()

            // Routing
            when {
                path == "/" || path == "/index.html" -> {
                    serveWebDashboard(outputStream)
                }
                path == "/api/status" -> {
                    val refresh = queryParams["refresh"] == "1"
                    if (refresh) fileCache.clear()
                    serveApiStatus(outputStream)
                }
                path == "/api/categories" -> {
                    serveApiCategories(outputStream)
                }
                path == "/api/files" -> {
                    val cat = queryParams["category"]?.uppercase() ?: "PHOTO"
                    val refresh = queryParams["refresh"] == "1"
                    if (refresh) fileCache.remove(cat)
                    serveApiFiles(cat, outputStream)
                }
                path == "/api/trigger-picker" -> {
                    val cat = queryParams["category"]?.uppercase() ?: "PHOTO"
                    listener?.onPcRequestFilePicker(cat)
                    sendJsonResponse(outputStream, mapOf("success" to true, "category" to cat))
                }
                path == "/api/download" -> {
                    val cat = queryParams["category"]?.uppercase() ?: "PHOTO"
                    val id = queryParams["id"]?.toIntOrNull() ?: 0
                    val inline = queryParams["inline"] == "1"
                    serveFileDownload(cat, id, inline, method, headers, outputStream)
                }
                path == "/api/thumbnail" -> {
                    val cat = queryParams["category"]?.uppercase() ?: "PHOTO"
                    val id = queryParams["id"]?.toIntOrNull() ?: 0
                    val pkg = queryParams["package"] ?: ""
                    val name = queryParams["name"] ?: ""
                    serveThumbnail(cat, id, pkg, name, outputStream)
                }
                path == "/api/download-all" -> {
                    val cat = queryParams["category"]?.uppercase() ?: "PHOTO"
                    serveDownloadZip(cat, method, outputStream)
                }
                path == "/api/upload" && method == "POST" -> {
                    handleFileUpload(queryParams, headers, inputStream, outputStream)
                }
                else -> {
                    sendNotFound(outputStream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request handle error: ${e.message}")
        } finally {
            try {
                outputStream?.flush()
                socket.shutdownOutput()
            } catch (e: Exception) {}
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }

    // --- API HANDLERS ---

    private fun serveApiStatus(out: OutputStream) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85

        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong

        val freeGb = String.format(Locale.US, "%.1f GB", freeBytes / (1024.0 * 1024.0 * 1024.0))
        val totalGb = String.format(Locale.US, "%.1f GB", totalBytes / (1024.0 * 1024.0 * 1024.0))

        val receivedList = getFilesForCategory("RECEIVED")
        val countsMap = mapOf(
            "PHOTO" to getFilesForCategory("PHOTO").size,
            "VIDEO" to getFilesForCategory("VIDEO").size,
            "MUSIC" to getFilesForCategory("MUSIC").size,
            "APP" to getFilesForCategory("APP").size,
            "DOCUMENT" to getFilesForCategory("DOCUMENT").size,
            "RECEIVED" to receivedList.size
        )

        val statusMap = mapOf(
            "deviceName" to P2PTransferManager.getDeviceDisplayName(),
            "ip" to P2PTransferManager.getLocalIpAddress(context),
            "port" to port,
            "battery" to battery,
            "storageFree" to freeGb,
            "storageTotal" to totalGb,
            "receivedCount" to receivedList.size,
            "counts" to countsMap
        )
        sendJsonResponse(out, statusMap)
    }

    private fun serveApiCategories(out: OutputStream) {
        val categories = listOf(
            mapOf("id" to "PHOTO", "name" to "Photos", "icon" to "📷", "count" to getFilesForCategory("PHOTO").size),
            mapOf("id" to "VIDEO", "name" to "Videos", "icon" to "🎬", "count" to getFilesForCategory("VIDEO").size),
            mapOf("id" to "MUSIC", "name" to "Music", "icon" to "🎵", "count" to getFilesForCategory("MUSIC").size),
            mapOf("id" to "APP", "name" to "Apps (APK)", "icon" to "📱", "count" to getFilesForCategory("APP").size),
            mapOf("id" to "DOCUMENT", "name" to "Documents", "icon" to "📄", "count" to getFilesForCategory("DOCUMENT").size),
            mapOf("id" to "RECEIVED", "name" to "Received Files", "icon" to "📥", "count" to getFilesForCategory("RECEIVED").size)
        )
        sendJsonResponse(out, categories)
    }

    private fun getFilesForCategory(category: String): List<FileItem> {
        if (category == "RECEIVED") {
            val files = P2PTransferManager.getAllReceivedFiles()
            return files.map { file ->
                var pkgName = ""
                val isApk = file.name.endsWith(".apk", true)
                if (isApk && file.exists()) {
                    try {
                        val pi = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                        if (pi != null && !pi.packageName.isNullOrBlank()) {
                            pkgName = pi.packageName
                        }
                    } catch (e: Throwable) {}
                }
                val fileCat = when {
                    isApk -> FileCategory.APP
                    isPhotoFileName(file.name) -> FileCategory.PHOTO
                    isVideoFileName(file.name) -> FileCategory.VIDEO
                    isAudioFileName(file.name) -> FileCategory.MUSIC
                    else -> FileCategory.DOCUMENT
                }
                FileItem(
                    file = file,
                    name = file.name,
                    size = file.length(),
                    category = fileCat,
                    lastModified = file.lastModified(),
                    uri = Uri.fromFile(file),
                    packageName = pkgName
                )
            }
        }

        val cached = fileCache[category]
        if (cached != null && cached.isNotEmpty()) {
            return cached
        }

        // Quét trực tiếp nếu chưa có trong cache
        val catEnum = when (category) {
            "APP" -> FileCategory.APP
            "VIDEO" -> FileCategory.VIDEO
            "MUSIC" -> FileCategory.MUSIC
            "DOCUMENT" -> FileCategory.DOCUMENT
            else -> FileCategory.PHOTO
        }
        val items = runBlocking(Dispatchers.IO) {
            scanner.scanCategory(catEnum)
        }
        fileCache[category] = items
        return items
    }

    private fun serveApiFiles(category: String, out: OutputStream) {
        val items = getFilesForCategory(category)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val dtoList = items.mapIndexed { index, item ->
            var pkgName = item.packageName
            val isApk = category == "APP" || item.name.endsWith(".apk", true)
            if (pkgName.isBlank() && isApk && item.file.exists()) {
                try {
                    val pi = context.packageManager.getPackageArchiveInfo(item.file.absolutePath, 0)
                    if (pi != null && !pi.packageName.isNullOrBlank()) {
                        pkgName = pi.packageName
                    }
                } catch (e: Throwable) {}
            }

            val isPhoto = category == "PHOTO" || isPhotoFileName(item.name)
            val isVideo = category == "VIDEO" || isVideoFileName(item.name)
            val hasThumb = isPhoto || isVideo || isApk

            mapOf(
                "id" to index,
                "name" to item.name,
                "packageName" to pkgName,
                "size" to item.size,
                "sizeStr" to formatSize(item.size),
                "date" to dateFormat.format(Date(item.lastModified)),
                "dateModified" to item.lastModified,
                "duration" to item.durationText,
                "category" to category,
                "downloadUrl" to "/api/download?category=$category&id=$index",
                "previewUrl" to "/api/download?category=$category&id=$index&inline=1",
                "isImage" to hasThumb,
                "isPhoto" to isPhoto,
                "isVideo" to isVideo
            )
        }
        sendJsonResponse(out, dtoList)
    }

    private fun buildContentDisposition(fileName: String, inline: Boolean = false): String {
        val dispType = if (inline) "inline" else "attachment"
        val cleanAscii = fileName.replace("\"", "").filter { it.code in 32..126 }
        val safeAscii = if (cleanAscii.isNotBlank()) cleanAscii else "download"
        val utf8Encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        return "$dispType; filename=\"$safeAscii\"; filename*=UTF-8''$utf8Encoded"
    }

    private fun isDirectFileReadable(file: File?): Boolean {
        if (file == null) return false
        return try {
            file.exists() && file.canRead() && file.length() > 0L
        } catch (_: Throwable) {
            false
        }
    }

    private fun openItemInputStream(item: FileItem): InputStream? {
        if (isDirectFileReadable(item.file)) {
            try {
                return FileInputStream(item.file)
            } catch (_: Throwable) {}
        }
        val uri = item.uri
        if (uri != null) {
            try {
                return context.contentResolver.openInputStream(uri)
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun resolveItemLength(item: FileItem): Long {
        if (isDirectFileReadable(item.file)) {
            return item.file.length()
        }
        val uri = item.uri
        if (uri != null) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0L) return afd.length
                }
            } catch (_: Throwable) {}
        }
        return item.size
    }

    private fun serveFileDownload(
        category: String,
        id: Int,
        inline: Boolean,
        method: String,
        headers: Map<String, String>,
        out: OutputStream
    ) {
        val items = getFilesForCategory(category)
        if (id < 0 || id >= items.size) {
            sendNotFound(out)
            return
        }

        val item = items[id]
        val fileLength = resolveItemLength(item)
        if (fileLength <= 0L) {
            sendNotFound(out)
            return
        }

        // Dùng application/octet-stream cho các file APK khi tải xuống để Chrome không chặn vì MIME package-archive
        val mimeType = if (!inline && item.name.endsWith(".apk", true)) {
            "application/octet-stream"
        } else {
            getMimeType(item.name)
        }
        val contentDisposition = buildContentDisposition(item.name, inline)

        // Kiểm tra Range Header (Streaming Video / Resume Download)
        val rangeHeader = headers["range"]
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            handleRangeDownload(item, fileLength, mimeType, contentDisposition, rangeHeader, method, out)
            return
        }

        val inputStream = if (method == "HEAD") null else openItemInputStream(item)
        if (method != "HEAD" && inputStream == null) {
            sendNotFound(out)
            return
        }

        val headerBuilder = StringBuilder()
        headerBuilder.append("HTTP/1.1 200 OK\r\n")
        headerBuilder.append("Content-Type: $mimeType\r\n")
        headerBuilder.append("Content-Length: $fileLength\r\n")
        headerBuilder.append("Content-Disposition: $contentDisposition\r\n")
        headerBuilder.append("X-Content-Type-Options: nosniff\r\n")
        headerBuilder.append("Cache-Control: public, max-age=3600\r\n")
        headerBuilder.append("Accept-Ranges: bytes\r\n")
        headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
        headerBuilder.append("Connection: close\r\n\r\n")

        out.write(headerBuilder.toString().toByteArray())
        out.flush()

        if (method == "HEAD") {
            return
        }

        try {
            inputStream?.use { fis ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
            }
            out.flush()
            if (!inline) {
                listener?.onFileDownloaded(item.name, fileLength)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download stream interrupted or cancelled: ${e.message}")
        }
    }

    private fun handleRangeDownload(
        item: FileItem,
        fileLength: Long,
        mimeType: String,
        contentDisposition: String,
        rangeHeader: String,
        method: String,
        out: OutputStream
    ) {
        val rangeSpec = rangeHeader.removePrefix("bytes=").trim()
        val parts = rangeSpec.split("-")
        val start = (parts[0].toLongOrNull() ?: 0L).coerceIn(0L, (fileLength - 1).coerceAtLeast(0L))
        val end = if (parts.size > 1 && parts[1].isNotBlank()) {
            parts[1].toLong().coerceIn(start, fileLength - 1)
        } else {
            fileLength - 1
        }
        val contentLength = (end - start + 1).coerceAtLeast(0L)

        val headerBuilder = StringBuilder()
        headerBuilder.append("HTTP/1.1 206 Partial Content\r\n")
        headerBuilder.append("Content-Type: $mimeType\r\n")
        headerBuilder.append("Content-Length: $contentLength\r\n")
        headerBuilder.append("Content-Range: bytes $start-$end/$fileLength\r\n")
        headerBuilder.append("Content-Disposition: $contentDisposition\r\n")
        headerBuilder.append("X-Content-Type-Options: nosniff\r\n")
        headerBuilder.append("Accept-Ranges: bytes\r\n")
        headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
        headerBuilder.append("Connection: close\r\n\r\n")

        out.write(headerBuilder.toString().toByteArray())
        out.flush()

        if (method == "HEAD") return

        try {
            if (isDirectFileReadable(item.file)) {
                RandomAccessFile(item.file, "r").use { raf ->
                    raf.seek(start)
                    val buffer = ByteArray(64 * 1024)
                    var remaining = contentLength
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = raf.read(buffer, 0, toRead)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            } else {
                openItemInputStream(item)?.use { input ->
                    var toSkip = start
                    while (toSkip > 0) {
                        val skipped = input.skip(toSkip)
                        if (skipped <= 0) break
                        toSkip -= skipped
                    }
                    val buffer = ByteArray(64 * 1024)
                    var remaining = contentLength
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
            out.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Range stream interrupted or cancelled: ${e.message}")
        }
    }

    private fun serveThumbnail(category: String, id: Int, packageName: String, fileName: String, out: OutputStream) {
        val isApk = category == "APP" || fileName.endsWith(".apk", true) || packageName.isNotBlank()

        if (isApk) {
            val pkg = packageName.trim()
            if (pkg.isNotBlank()) {
                val cached = appIconCache[pkg]
                if (cached != null && cached.isNotEmpty()) {
                    writeImageBytes(out, "image/png", cached)
                    return
                }
            }

            var drawable: Drawable? = null
            val pm = context.packageManager

            if (pkg.isNotBlank()) {
                try {
                    val launchIntent = pm.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        val resolveInfo = pm.resolveActivity(launchIntent, 0)
                        if (resolveInfo != null) {
                            drawable = resolveInfo.loadIcon(pm)
                        }
                    }
                } catch (e: Throwable) {}

                if (drawable == null) {
                    try {
                        drawable = pm.getApplicationIcon(pkg)
                    } catch (e: Throwable) {}
                }
            }

            if (drawable == null) {
                val appItems = getFilesForCategory("APP")
                val item = if (pkg.isNotBlank()) {
                    appItems.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }
                } else if (fileName.isNotBlank()) {
                    appItems.firstOrNull { it.name.equals(fileName, ignoreCase = true) }
                } else {
                    appItems.getOrNull(id)
                }

                val apkFile = item?.file
                if (apkFile != null && apkFile.exists()) {
                    try {
                        val pi = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
                        val appInfo = pi?.applicationInfo
                        if (appInfo != null) {
                            appInfo.sourceDir = apkFile.absolutePath
                            appInfo.publicSourceDir = apkFile.absolutePath
                            drawable = appInfo.loadIcon(pm)
                        }
                    } catch (e: Throwable) {}
                }
            }

            if (drawable == null && fileName.isNotBlank()) {
                val receivedItems = getFilesForCategory("RECEIVED")
                val item = receivedItems.firstOrNull { it.name.equals(fileName, ignoreCase = true) }
                val apkFile = item?.file
                if (apkFile != null && apkFile.exists()) {
                    try {
                        val pi = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
                        val appInfo = pi?.applicationInfo
                        if (appInfo != null) {
                            appInfo.sourceDir = apkFile.absolutePath
                            appInfo.publicSourceDir = apkFile.absolutePath
                            drawable = appInfo.loadIcon(pm)
                        }
                    } catch (e: Throwable) {}
                }
            }

            if (drawable != null) {
                try {
                    val bitmap = drawableToBitmap(drawable)
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    val bytes = baos.toByteArray()
                    if (pkg.isNotBlank()) {
                        appIconCache[pkg] = bytes
                    }
                    writeImageBytes(out, "image/png", bytes)
                    return
                } catch (e: Throwable) {
                    Log.w(TAG, "Error compressing icon for $pkg: ${e.message}")
                }
            }
        }

        // Tìm item trong danh mục
        val items = getFilesForCategory(category)
        val item = if (id in items.indices) {
            items[id]
        } else if (fileName.isNotBlank()) {
            items.firstOrNull { it.name.equals(fileName, ignoreCase = true) }
        } else {
            null
        }

        if (item == null) {
            sendNotFound(out)
            return
        }

        val cacheKey = "${category}_${item.name}_${item.size}"
        val cachedThumb = mediaThumbCache[cacheKey]
        if (cachedThumb != null && cachedThumb.isNotEmpty()) {
            writeImageBytes(out, "image/jpeg", cachedThumb)
            return
        }

        // Thử trích xuất thumbnail gọn nhẹ (cho cả PHOTO và VIDEO từ Uri hoặc File)
        val thumbBitmap = extractMediaThumbnailBitmap(item, category)
        if (thumbBitmap != null) {
            try {
                val baos = ByteArrayOutputStream()
                thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                val bytes = baos.toByteArray()
                if (bytes.isNotEmpty()) {
                    if (mediaThumbCache.size > 300) mediaThumbCache.clear()
                    mediaThumbCache[cacheKey] = bytes
                    writeImageBytes(out, "image/jpeg", bytes)
                    return
                }
            } catch (_: Throwable) {}
        }

        // Fallback: nếu là file ảnh và có thể đọc stream trực tiếp
        if (category == "PHOTO" || isPhotoFileName(item.name)) {
            val stream = openItemInputStream(item)
            if (stream != null) {
                try {
                    val bytes = stream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        writeImageBytes(out, getMimeType(item.name), bytes)
                        return
                    }
                } catch (_: Throwable) {}
            }
        }

        sendNotFound(out)
    }

    private fun extractMediaThumbnailBitmap(item: FileItem, category: String): Bitmap? {
        val isVideo = category == "VIDEO" || isVideoFileName(item.name)

        // 1. Ưu tiên ContentResolver.loadThumbnail trên Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.uri != null && item.uri.scheme == "content") {
            try {
                return context.contentResolver.loadThumbnail(item.uri, Size(240, 240), null)
            } catch (_: Throwable) {}
        }

        // 2. Nếu là Video: dùng ThumbnailUtils hoặc MediaMetadataRetriever
        if (isVideo) {
            if (isDirectFileReadable(item.file)) {
                try {
                    @Suppress("DEPRECATION")
                    val bmp = ThumbnailUtils.createVideoThumbnail(item.file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
                    if (bmp != null) return bmp
                } catch (_: Throwable) {}
            }
            val retriever = MediaMetadataRetriever()
            try {
                if (isDirectFileReadable(item.file)) {
                    retriever.setDataSource(item.file.absolutePath)
                } else if (item.uri != null) {
                    retriever.setDataSource(context, item.uri)
                }
                return retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
            } catch (_: Throwable) {
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }
            return null
        }

        // 3. Nếu là Ảnh (PHOTO): giải mã giảm chiều (inSampleSize) xuống ~256px để phản hồi cực nhanh
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (isDirectFileReadable(item.file)) {
                BitmapFactory.decodeFile(item.file.absolutePath, opts)
            } else {
                openItemInputStream(item)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }

            if (opts.outWidth > 0 && opts.outHeight > 0) {
                var sampleSize = 1
                val maxDim = maxOf(opts.outWidth, opts.outHeight)
                while (maxDim / sampleSize > 320) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                return if (isDirectFileReadable(item.file)) {
                    BitmapFactory.decodeFile(item.file.absolutePath, decodeOpts)
                } else {
                    openItemInputStream(item)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    private fun writeImageBytes(out: OutputStream, mimeType: String, bytes: ByteArray) {
        val header = "HTTP/1.1 200 OK\r\nContent-Type: $mimeType\r\nContent-Length: ${bytes.size}\r\nAccess-Control-Allow-Origin: *\r\nCache-Control: public, max-age=86400\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) minOf(drawable.intrinsicWidth, 256) else 96
        val height = if (drawable.intrinsicHeight > 0) minOf(drawable.intrinsicHeight, 256) else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun serveDownloadZip(category: String, method: String, out: OutputStream) {
        val items = getFilesForCategory(category)
        val zipName = "ShareNow_${category}_${System.currentTimeMillis()}.zip"
        val contentDisposition = buildContentDisposition(zipName)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Disposition: $contentDisposition\r\nX-Content-Type-Options: nosniff\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.flush()

        if (method == "HEAD") return

        try {
            val zos = ZipOutputStream(out)
            val usedNames = mutableSetOf<String>()
            for (item in items) {
                val inputStream = openItemInputStream(item) ?: continue
                try {
                    var entryName = item.name
                    var counter = 1
                    while (usedNames.contains(entryName.lowercase())) {
                        val base = item.name.substringBeforeLast(".")
                        val ext = item.name.substringAfterLast(".", "")
                        entryName = if (ext.isNotEmpty()) "$base ($counter).$ext" else "$base ($counter)"
                        counter++
                    }
                    usedNames.add(entryName.lowercase())

                    val entry = ZipEntry(entryName)
                    zos.putNextEntry(entry)
                    inputStream.use { fis ->
                        fis.copyTo(zos, 32 * 1024)
                    }
                    zos.closeEntry()
                } catch (e: Exception) {
                    Log.w(TAG, "Zip entry error: ${e.message}")
                }
            }
            zos.finish()
            zos.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Zip stream interrupted: ${e.message}")
        }
    }

    /**
     * Xử lý PC gửi (Upload) tệp lên điện thoại
     */
    private fun handleFileUpload(
        queryParams: Map<String, String>,
        headers: Map<String, String>,
        input: InputStream,
        out: OutputStream
    ) {
        try {
            val rawName = queryParams["filename"] ?: headers["x-filename"] ?: "Upload_${System.currentTimeMillis()}.bin"
            val decodedName = URLDecoder.decode(rawName, "UTF-8")
            val sanitizedName = decodedName.replace(Regex("[/\\\\:*?\"<>|]"), "_")

            val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
            val targetDir = P2PTransferManager.getReceivedFilesDir()
            var targetFile = File(targetDir, sanitizedName)

            var counter = 1
            val baseName = sanitizedName.substringBeforeLast(".")
            val ext = sanitizedName.substringAfterLast(".", "")
            while (targetFile.exists()) {
                val newName = if (ext.isNotEmpty()) "$baseName ($counter).$ext" else "$baseName ($counter)"
                targetFile = File(targetDir, newName)
                counter++
            }

            Log.i(TAG, "Receiving upload file from PC: ${targetFile.name} (length: $contentLength)")

            FileOutputStream(targetFile).use { fos ->
                val buffer = ByteArray(64 * 1024)
                if (contentLength > 0) {
                    var remaining = contentLength
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        remaining -= read
                    }
                } else {
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                    }
                }
                fos.flush()
            }

            // Quét vào thư viện hệ thống
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)

            // Xóa toàn bộ cache để tệp vừa nhận hiển thị ngay ở cả RECEIVED lẫn PHOTO/VIDEO/MUSIC/DOCUMENT
            fileCache.clear()

            // Báo cho UI Android
            listener?.onFileUploaded(targetFile)

            val responseData = mapOf(
                "success" to true,
                "message" to "Upload successful",
                "filename" to targetFile.name,
                "size" to targetFile.length()
            )
            sendJsonResponse(out, responseData)

        } catch (e: Exception) {
            Log.e(TAG, "Upload handler error: ${e.message}")
            sendJsonResponse(out, mapOf("success" to false, "error" to (e.message ?: "Upload failed")), 500)
        }
    }

    // --- HTML / DASHBOARD SERVING ---

    private fun serveWebDashboard(out: OutputStream) {
        val htmlContent = WebShareDashboardHtml.getHtml(context)
        val bytes = htmlContent.toByteArray(Charsets.UTF_8)
        val response = StringBuilder()
            .append("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: text/html; charset=UTF-8\r\n")
            .append("Content-Length: ${bytes.size}\r\n")
            .append("Access-Control-Allow-Origin: *\r\n")
            .append("Connection: close\r\n\r\n")
            .toString()

        out.write(response.toByteArray())
        out.write(bytes)
        out.flush()
    }

    // --- TIỆN ÍCH HTTP ---

    private fun sendJsonResponse(out: OutputStream, data: Any, statusCode: Int = 200) {
        val json = gson.toJson(data)
        val bytes = json.toByteArray(Charsets.UTF_8)
        val statusText = if (statusCode == 200) "OK" else "Internal Server Error"
        val response = "HTTP/1.1 $statusCode $statusText\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: ${bytes.size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        out.write(response.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun sendCorsOk(out: OutputStream) {
        val response = "HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: *\r\nConnection: close\r\n\r\n"
        out.write(response.toByteArray())
        out.flush()
    }

    private fun sendNotFound(out: OutputStream) {
        val body = "404 Not Found"
        val response = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: ${body.length}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n$body"
        out.write(response.toByteArray())
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        while (input.read().also { c = it } != -1) {
            if (c == '\r'.code) {
                val next = input.read()
                if (next == '\n'.code) break
                sb.append(c.toChar())
                if (next != -1) sb.append(next.toChar())
            } else if (c == '\n'.code) {
                break
            } else {
                sb.append(c.toChar())
            }
        }
        return if (sb.isEmpty() && c == -1) null else sb.toString()
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val kv = pair.split("=", limit = 2)
            if (kv.isNotEmpty()) {
                val key = URLDecoder.decode(kv[0], "UTF-8")
                val value = if (kv.size > 1) URLDecoder.decode(kv[1], "UTF-8") else ""
                map[key] = value
            }
        }
        return map
    }

    private fun isPhotoFileName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp") ||
            lower.endsWith(".heic") || lower.endsWith(".heif") || lower.endsWith(".avif")
    }

    private fun isVideoFileName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") ||
            lower.endsWith(".mov") || lower.endsWith(".3gp") || lower.endsWith(".webm") ||
            lower.endsWith(".flv") || lower.endsWith(".ts") || lower.endsWith(".m4v")
    }

    private fun isAudioFileName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".flac") ||
            lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".ogg") ||
            lower.endsWith(".opus") || lower.endsWith(".wma") || lower.endsWith(".amr")
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "txt", "csv", "md", "log" -> "text/plain"
            "json" -> "application/json"
            "html", "htm" -> "text/html"
            else -> "application/octet-stream"
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            mb >= 1024.0 -> String.format(Locale.US, "%.2f GB", mb / 1024.0)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", (bytes / 1024.0).coerceAtLeast(1.0))
        }
    }

    companion object {
        private const val TAG = "WebShareServer"
    }
}
