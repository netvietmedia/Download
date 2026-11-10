package com.ushareit.core.downloader

import android.content.Context
import android.os.PowerManager
import android.util.Log
import android.webkit.CookieManager
import com.ushareit.core.analytics.FirebaseAnalyticsManager
import com.ushareit.core.p2p.P2PTransferManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadEngine(
    val downloadDir: File,
    val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    companion object {
        private val instances = java.util.concurrent.CopyOnWriteArrayList<DownloadEngine>()

        fun notifyExternalFileDeleted(fileName: String, filePath: String) {
            for (engine in instances) {
                engine.removeTaskMatchingFile(fileName, filePath)
            }
        }
    }

    private val tasksMap = ConcurrentHashMap<String, DownloadTask>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private var wakeLock: PowerManager.WakeLock? = null

    private val okHttpClient = OkHttpClient.Builder()
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                return okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { addr ->
                    if (addr is java.net.Inet4Address) 0 else 1
                }
            }
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _tasksFlow = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasksFlow: StateFlow<List<DownloadTask>> = _tasksFlow.asStateFlow()

    init {
        instances.add(this)
        try {
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
        } catch (_: Exception) {}
        scanExistingDownloads()
    }

    fun removeTaskMatchingFile(fileName: String, filePath: String) {
        var changed = false
        val toRemove = tasksMap.values.filter { task ->
            val taskFile = File(task.targetFilePath)
            task.targetFilePath.equals(filePath, ignoreCase = true) ||
                taskFile.name.equals(fileName, ignoreCase = true) ||
                task.title.equals(fileName.substringBeforeLast('.'), ignoreCase = true)
        }
        for (task in toRemove) {
            activeJobs[task.id]?.cancel()
            activeJobs.remove(task.id)
            tasksMap.remove(task.id)
            try {
                val f = File(task.targetFilePath)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
            changed = true
        }
        if (changed) {
            emitUpdate()
        }
    }

    fun syncExistingDownloads() {
        var removedAny = false
        val recvDir = try { P2PTransferManager.getReceivedFilesDir() } catch (_: Exception) { null }
        val completedTasks = tasksMap.values.filter { it.status == DownloadStatus.COMPLETED }
        for (task in completedTasks) {
            val targetFile = File(task.targetFilePath)
            val recvCopy = if (recvDir != null) File(recvDir, targetFile.name) else null
            val targetExists = targetFile.exists() && targetFile.length() > 0L
            val recvExists = recvCopy != null && recvCopy.exists() && recvCopy.length() > 0L
            if (!targetExists && !recvExists) {
                tasksMap.remove(task.id)
                removedAny = true
            }
        }
        if (removedAny) {
            emitUpdate()
        }
        scanExistingDownloads()
    }

    private fun resolveWritableDir(): File {
        try {
            if (!downloadDir.exists()) downloadDir.mkdirs()
            if (downloadDir.exists() && downloadDir.canWrite()) {
                return downloadDir
            }
        } catch (_: Exception) {}

        val appExtDir = context?.getExternalFilesDir(null)?.let { File(it, "Videos") }
        if (appExtDir != null) {
            try {
                if (!appExtDir.exists()) appExtDir.mkdirs()
                if (appExtDir.exists() && appExtDir.canWrite()) return appExtDir
            } catch (_: Exception) {}
        }

        val internalDir = File(context?.filesDir ?: downloadDir, "Videos")
        if (!internalDir.exists()) internalDir.mkdirs()
        return internalDir
    }

    private fun getCookieHeader(targetUrl: String, refererUrl: String?): String? {
        return try {
            val cm = CookieManager.getInstance()
            val cookies = LinkedHashSet<String>()
            cm.getCookie(targetUrl)?.trim()?.takeIf { it.isNotEmpty() }?.let { cookies.add(it) }
            if (!refererUrl.isNullOrBlank()) {
                cm.getCookie(refererUrl)?.trim()?.takeIf { it.isNotEmpty() }?.let { cookies.add(it) }
            }
            val lower = (targetUrl + " " + (refererUrl ?: "")).lowercase()
            if (lower.contains("tiktok") || lower.contains("byteoversea") || lower.contains("ibytedtos")) {
                cm.getCookie("https://www.tiktok.com")?.trim()?.takeIf { it.isNotEmpty() }?.let { cookies.add(it) }
                cm.getCookie("https://m.tiktok.com")?.trim()?.takeIf { it.isNotEmpty() }?.let { cookies.add(it) }
            }
            if (cookies.isEmpty()) null else cookies.joinToString("; ")
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveRelativeUrl(baseUrl: String, relativeOrFull: String): String {
        val trimmed = relativeOrFull.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("//")) {
            val scheme = if (baseUrl.startsWith("http://")) "http:" else "https:"
            return "$scheme$trimmed"
        }
        return try {
            URI(baseUrl).resolve(trimmed).toString()
        } catch (_: Exception) {
            val base = baseUrl.substringBefore('?').substringBeforeLast('/')
            if (trimmed.startsWith("/")) {
                val u = android.net.Uri.parse(baseUrl)
                "${u.scheme}://${u.host}$trimmed"
            } else {
                "$base/$trimmed"
            }
        }
    }

    @Synchronized
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null && context != null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShareNow:DownloadWakeLock")?.apply {
                    setReferenceCounted(false)
                    acquire(2 * 60 * 60 * 1000L)
                }
            }
        } catch (e: Exception) {
            Log.w("DownloadEngine", "Could not acquire WakeLock: ${e.message}")
        }
    }

    @Synchronized
    private fun releaseWakeLockIfIdle() {
        try {
            val hasActive = tasksMap.values.any { it.status == DownloadStatus.DOWNLOADING }
            if (!hasActive && wakeLock?.isHeld == true) {
                wakeLock?.release()
                wakeLock = null
            }
        } catch (e: Exception) {
            Log.w("DownloadEngine", "Error releasing WakeLock: ${e.message}")
        }
    }

    private fun emitUpdate() {
        _tasksFlow.value = tasksMap.values.toList().sortedByDescending { it.createdAt }
    }

    fun scanExistingDownloads() {
        val dirs = listOfNotNull(
            downloadDir,
            context?.getExternalFilesDir(null)?.let { File(it, "Videos") }
        ).distinctBy { it.absolutePath }

        val seenFileNames = HashSet<String>()
        for (existingTask in tasksMap.values) {
            seenFileNames.add(File(existingTask.targetFilePath).name.lowercase())
        }

        for (dir in dirs) {
            if (!dir.exists()) continue
            val files = dir.listFiles { f ->
                f.isFile &&
                    !f.name.startsWith(".") &&
                    f.length() > 10 * 1024L &&
                    (f.name.endsWith(".mp4", ignoreCase = true) ||
                        f.name.endsWith(".mkv", ignoreCase = true) ||
                        f.name.endsWith(".webm", ignoreCase = true) ||
                        f.name.endsWith(".mp3", ignoreCase = true))
            } ?: continue

            for (file in files) {
                val lowerName = file.name.lowercase()
                if (!seenFileNames.add(lowerName)) continue
                val taskId = "SAVED_${file.name.hashCode()}"
                val alreadyTracked = tasksMap.containsKey(taskId) || tasksMap.values.any {
                    it.targetFilePath.equals(file.absolutePath, ignoreCase = true) ||
                        File(it.targetFilePath).name.equals(file.name, ignoreCase = true)
                }
                if (!alreadyTracked) {
                    tasksMap[taskId] = DownloadTask(
                        id = taskId,
                        title = file.nameWithoutExtension,
                        downloadUrl = file.absolutePath,
                        targetFilePath = file.absolutePath,
                        qualityLabel = if (file.name.endsWith(".mp3", true)) "Audio MP3" else "HD Video",
                        totalBytes = file.length(),
                        downloadedBytes = file.length(),
                        status = DownloadStatus.COMPLETED,
                        createdAt = file.lastModified(),
                        completedAt = file.lastModified(),
                        isMonetized = MonetizedDomainRegistry.isMonetizedVideoFile(file.name)
                    )
                }
            }
        }
        emitUpdate()
    }

    fun enqueueDownload(
        title: String,
        url: String,
        qualityLabel: String,
        format: String = "mp4",
        referer: String? = null,
        activePageUrl: String? = null
    ): DownloadTask {
        val ext = if (format.equals("mp3", ignoreCase = true)) "mp3" else "mp4"

        // Clean byte-range params from Facebook/Instagram CDN links
        var cleanUrl = SnifferEngine.sanitizeMediaUrl(url)
        if (cleanUrl.contains("fbcdn.net", ignoreCase = true) || cleanUrl.contains("cdninstagram.com", ignoreCase = true)) {
            cleanUrl = SnifferEngine.stripByteRangeParams(cleanUrl)
            if (ext == "mp3" && !SnifferEngine.parseMetaEfgInfo(cleanUrl).isAudioOnly) {
                SnifferEngine.findCompanionAudioUrl(cleanUrl)?.let { audioUrl ->
                    cleanUrl = audioUrl
                }
            }
        }

        val now = System.currentTimeMillis()
        val incomingMetaVid = SnifferEngine.parseMetaEfgInfo(cleanUrl).videoId
        val duplicateTask = tasksMap.values.firstOrNull { existing ->
            !existing.id.startsWith("SAVED_") &&
                File(existing.targetFilePath).extension.equals(ext, ignoreCase = true) &&
                (existing.downloadUrl == cleanUrl ||
                    (incomingMetaVid.isNotEmpty() && SnifferEngine.parseMetaEfgInfo(existing.downloadUrl).videoId == incomingMetaVid)) &&
                (existing.status == DownloadStatus.DOWNLOADING ||
                    existing.status == DownloadStatus.PENDING ||
                    (existing.status == DownloadStatus.COMPLETED && (now - (existing.completedAt ?: existing.createdAt)) < 45_000L))
        }
        if (duplicateTask != null) {
            Log.i("DownloadEngine", "Skipping duplicate enqueue for already active/recent task: ${duplicateTask.id}")
            return duplicateTask
        }

        val taskId = "TASK_${now}_${(1000..9999).random()}"
        val sanitizedTitle = title
            .removeSuffix(".mp4")
            .removeSuffix(".mp3")
            .replace(Regex("[^\\p{L}\\p{N}_. -]"), "_")
            .take(60)
            .ifBlank { "Video_$now" }
        val fileName = "${sanitizedTitle}_${(100..999).random()}.$ext"

        val activeDir = resolveWritableDir()
        val targetFile = File(activeDir, fileName)

        val resolvedReferer = SnifferEngine.getRefererForUrl(cleanUrl, referer)
        val isMonetized = MonetizedDomainRegistry.registerMonetizedDownload(
            fileName = fileName,
            refererUrl = resolvedReferer,
            downloadUrl = cleanUrl,
            activePageUrl = activePageUrl
        )

        Log.i("DownloadEngine", "Enqueuing download: title=$title, format=$ext, isMonetized=$isMonetized, url=$cleanUrl, target=${targetFile.absolutePath}")

        val task = DownloadTask(
            id = taskId,
            title = title.ifBlank { sanitizedTitle },
            downloadUrl = cleanUrl,
            targetFilePath = targetFile.absolutePath,
            qualityLabel = qualityLabel,
            status = DownloadStatus.DOWNLOADING,
            referer = resolvedReferer,
            isMonetized = isMonetized
        )

        val host = MonetizedDomainRegistry.extractHost(resolvedReferer ?: cleanUrl).ifEmpty { "direct_url" }
        FirebaseAnalyticsManager.logBrowserDownloadStart(
            taskId = taskId,
            title = task.title,
            host = host,
            qualityLabel = qualityLabel,
            format = ext,
            isMonetized = isMonetized
        )

        tasksMap[taskId] = task
        emitUpdate()

        val job = scope.launch {
            runDownload(task, targetFile, null)
        }
        activeJobs[taskId] = job

        return task
    }

    private fun onDownloadSuccessSyncMedia(file: File, title: String, isMonetized: Boolean = false) {
        try {
            val ctx = context ?: return
            if (!isMonetized || com.ushareit.core.subscription.BillingManager.isVipUser()) {
                MediaStoreExporter.exportVideoToGallery(ctx, file, title)
            }
            val recvDir = P2PTransferManager.getReceivedFilesDir()
            if (file.parentFile?.absolutePath != recvDir.absolutePath) {
                try {
                    if (!recvDir.exists()) recvDir.mkdirs()
                    val dest = File(recvDir, file.name)
                    if (!dest.exists() && recvDir.canWrite()) {
                        file.copyTo(dest, overwrite = true)
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w("DownloadEngine", "Media sync warning: ${e.message}")
        }
    }

    private suspend fun runDownload(
        initialTask: DownloadTask,
        targetFile: File,
        resumeFrom: Long? = null
    ) = withContext(Dispatchers.IO) {
        acquireWakeLock()
        var currentDownloaded = if (resumeFrom != null && resumeFrom > 0 && targetFile.exists()) resumeFrom else 0L
        var totalBytes = initialTask.totalBytes
        val maxRetries = 5
        var attempt = 0
        var isSuccess = false

        try {
            targetFile.parentFile?.let {
                if (!it.exists()) it.mkdirs()
            }

            var activeDownloadUrl = initialTask.downloadUrl
            while (attempt < maxRetries && !isSuccess && coroutineContext.isActive) {
                attempt++
                val isResume = (currentDownloaded > 0 && targetFile.exists())
                val isYouTubeCdn = activeDownloadUrl.contains("googlevideo.com")
                val userAgent = when {
                    isYouTubeCdn && activeDownloadUrl.contains("c=ANDROID_VR") ->
                        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                    isYouTubeCdn && activeDownloadUrl.contains("c=ANDROID_TESTSUITE") ->
                        "com.google.android.youtube/1.9 (Linux; U; Android 12; US) gzip"
                    isYouTubeCdn && activeDownloadUrl.contains("c=ANDROID") ->
                        "com.google.android.youtube/20.03.35 (Linux; U; Android 14) gzip"
                    isYouTubeCdn && activeDownloadUrl.contains("c=IOS") ->
                        "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
                    isYouTubeCdn ->
                        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                    attempt % 2 == 1 -> SnifferEngine.MOBILE_USER_AGENT
                    else -> SnifferEngine.DESKTOP_USER_AGENT
                }

                val requestBuilder = Request.Builder()
                    .url(activeDownloadUrl)
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9,vi;q=0.8")

                if (!isYouTubeCdn) {
                    if (!initialTask.referer.isNullOrEmpty()) {
                        requestBuilder.header("Referer", initialTask.referer)
                        try {
                            val refUri = android.net.Uri.parse(initialTask.referer)
                            if (refUri.scheme != null && refUri.host != null) {
                                requestBuilder.header("Origin", "${refUri.scheme}://${refUri.host}")
                            }
                        } catch (_: Exception) {}
                    }

                    val cookieHeader = getCookieHeader(initialTask.downloadUrl, initialTask.referer)
                    if (!cookieHeader.isNullOrEmpty()) {
                        requestBuilder.header("Cookie", cookieHeader)
                    }
                }

                if (isResume) {
                    requestBuilder.header("Range", "bytes=$currentDownloaded-")
                }

                try {
                    val response = okHttpClient.newCall(requestBuilder.build()).execute()
                    val responseCode = response.code

                    if (responseCode == 416) {
                        response.close()
                        if (targetFile.exists() && targetFile.length() > 10 * 1024L) {
                            isSuccess = true
                            break
                        } else {
                            currentDownloaded = 0L
                            if (targetFile.exists()) targetFile.delete()
                            continue
                        }
                    }

                    if (responseCode !in 200..299) {
                        response.close()
                        Log.w("DownloadEngine", "HTTP $responseCode for $activeDownloadUrl (attempt $attempt/$maxRetries)")
                        if (isYouTubeCdn && responseCode == 403 && !activeDownloadUrl.contains("&range=")) {
                            val clenMatch = Regex("""[?&]clen=(\d+)""").find(activeDownloadUrl)
                            val clen = clenMatch?.groupValues?.get(1)?.toLongOrNull() ?: 25_000_000L
                            activeDownloadUrl = "$activeDownloadUrl&range=0-${clen - 1}"
                            continue
                        }
                        if (attempt < maxRetries) {
                            delay(attempt * 1500L)
                            continue
                        } else {
                            failTask(initialTask.id, "HTTP Error $responseCode")
                            return@withContext
                        }
                    }

                    val body = response.body
                    if (body == null) {
                        response.close()
                        if (attempt < maxRetries) {
                            delay(1500L)
                            continue
                        } else {
                            failTask(initialTask.id, "Empty response body")
                            return@withContext
                        }
                    }

                    val contentType = response.header("Content-Type") ?: ""
                    val isHls = initialTask.downloadUrl.contains(".m3u8", ignoreCase = true) ||
                        contentType.contains("mpegurl", ignoreCase = true)

                    if (isHls) {
                        val playlistContent = body.string()
                        response.close()
                        downloadHlsStream(initialTask, targetFile, playlistContent)
                        return@withContext
                    }

                    val streamLength = body.contentLength()
                    val isAppendMode = (responseCode == 206)
                    if (!isAppendMode && isResume) {
                        currentDownloaded = 0L
                    }

                    if (streamLength > 0) {
                        totalBytes = currentDownloaded + streamLength
                    } else if (totalBytes <= 0) {
                        totalBytes = 15 * 1024 * 1024L
                    }

                    var currentTask = tasksMap[initialTask.id] ?: initialTask
                    currentTask = currentTask.copy(
                        totalBytes = totalBytes,
                        downloadedBytes = currentDownloaded,
                        status = DownloadStatus.DOWNLOADING,
                        errorMessage = null
                    )
                    tasksMap[initialTask.id] = currentTask
                    emitUpdate()

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile, isAppendMode).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var lastUpdateTime = System.currentTimeMillis()
                            var bytesSinceLastUpdate = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!coroutineContext.isActive) {
                                    return@withContext
                                }

                                output.write(buffer, 0, bytesRead)
                                currentDownloaded += bytesRead
                                bytesSinceLastUpdate += bytesRead

                                val now = System.currentTimeMillis()
                                val timeDiff = now - lastUpdateTime
                                if (timeDiff >= 250) {
                                    val speed = if (timeDiff > 0) (bytesSinceLastUpdate * 1000) / timeDiff else 0L
                                    val adjustedTotal = if (currentDownloaded > totalBytes) currentDownloaded + (2 * 1024 * 1024L) else totalBytes
                                    currentTask = currentTask.copy(
                                        totalBytes = adjustedTotal,
                                        downloadedBytes = currentDownloaded,
                                        speedBytesPerSec = speed,
                                        status = DownloadStatus.DOWNLOADING
                                    )
                                    tasksMap[initialTask.id] = currentTask
                                    emitUpdate()

                                    lastUpdateTime = now
                                    bytesSinceLastUpdate = 0L
                                }
                            }
                            output.flush()
                        }
                    }

                    // Check if the downloaded payload was actually an M3U8 playlist or HTML error page
                    if (currentDownloaded < 256 * 1024L && targetFile.exists()) {
                        val sample = try { targetFile.readText().take(200).trim() } catch (_: Exception) { "" }
                        if (sample.startsWith("#EXTM3U")) {
                            val playlistContent = try { targetFile.readText() } catch (_: Exception) { "" }
                            targetFile.delete()
                            if (playlistContent.isNotEmpty()) {
                                downloadHlsStream(initialTask, targetFile, playlistContent)
                                return@withContext
                            }
                        }
                        if (sample.startsWith("<html", ignoreCase = true) ||
                            sample.startsWith("<!doctype", ignoreCase = true) ||
                            (currentDownloaded < 2048L && sample.contains("sabr", ignoreCase = true))
                        ) {
                            Log.e("DownloadEngine", "Invalid non-video payload downloaded (${currentDownloaded}B): ${sample.take(80)}")
                            targetFile.delete()
                            failTask(initialTask.id, "Protected or expired stream link")
                            return@withContext
                        }
                    }

                    // Nếu file MP4 tải về là luồng DASH video-only (không có track âm thanh, ví dụ Facebook Reels/Watch),
                    // tự động tìm luồng audio đi kèm (companion audio stream) và ghép tiếng (mux) bằng MediaMuxer
                    if (targetFile.exists() &&
                        currentDownloaded > 10 * 1024L &&
                        targetFile.extension.equals("mp4", ignoreCase = true) &&
                        !hasAudioTrack(targetFile)
                    ) {
                        var companionAudioUrl = SnifferEngine.findCompanionAudioUrl(activeDownloadUrl)
                            ?: SnifferEngine.findCompanionAudioUrl(initialTask.downloadUrl)
                        if (companionAudioUrl == null && !initialTask.referer.isNullOrEmpty() &&
                            (initialTask.referer.contains("facebook.com") || initialTask.referer.contains("fb.watch"))
                        ) {
                            try {
                                SnifferEngine().extractFacebookVideo(initialTask.referer)
                                companionAudioUrl = SnifferEngine.findCompanionAudioUrl(activeDownloadUrl)
                                    ?: SnifferEngine.findCompanionAudioUrl(initialTask.downloadUrl)
                            } catch (_: Exception) {}
                        }
                        if (!companionAudioUrl.isNullOrEmpty()) {
                            val workDir = targetFile.parentFile ?: downloadDir
                            val tempAudioFile = File(workDir, ".tmp_audio_${initialTask.id}.m4a")
                            val tempMuxedFile = File(workDir, ".tmp_muxed_${initialTask.id}.mp4")
                            try {
                                Log.i("DownloadEngine", "Muxing companion audio for silent DASH video: ${targetFile.name}")
                                val audioDownloaded = downloadCompanionAudioFile(
                                    audioUrl = companionAudioUrl,
                                    referer = initialTask.referer,
                                    cookieHeader = getCookieHeader(companionAudioUrl, initialTask.referer),
                                    targetAudioFile = tempAudioFile
                                )
                                if (audioDownloaded && tempAudioFile.exists() && tempAudioFile.length() > 1024L) {
                                    val muxedOk = muxVideoAndAudio(targetFile, tempAudioFile, tempMuxedFile)
                                    if (muxedOk && tempMuxedFile.exists() && tempMuxedFile.length() > targetFile.length()) {
                                        if (targetFile.delete()) {
                                            tempMuxedFile.renameTo(targetFile)
                                        } else {
                                            tempMuxedFile.copyTo(targetFile, overwrite = true)
                                            tempMuxedFile.delete()
                                        }
                                        currentDownloaded = targetFile.length()
                                        Log.i("DownloadEngine", "Successfully muxed video + audio: ${targetFile.name} ($currentDownloaded bytes)")
                                    }
                                }
                            } catch (muxEx: Exception) {
                                Log.w("DownloadEngine", "Audio muxing fallback (keeping video): ${muxEx.message}")
                            } finally {
                                try { if (tempAudioFile.exists()) tempAudioFile.delete() } catch (_: Exception) {}
                                try { if (tempMuxedFile.exists()) tempMuxedFile.delete() } catch (_: Exception) {}
                            }
                        } else {
                            Log.w("DownloadEngine", "No companion audio URL found for silent DASH video: ${targetFile.name}")
                        }
                    }

                    currentTask = currentTask.copy(
                        totalBytes = currentDownloaded.coerceAtLeast(1L),
                        downloadedBytes = currentDownloaded.coerceAtLeast(1L),
                        status = DownloadStatus.COMPLETED,
                        completedAt = System.currentTimeMillis(),
                        speedBytesPerSec = 0L,
                        errorMessage = null
                    )
                    tasksMap[initialTask.id] = currentTask
                    emitUpdate()
                    Log.i("DownloadEngine", "Download 100% completed: ${targetFile.absolutePath} ($currentDownloaded bytes)")
                    val durMs = (System.currentTimeMillis() - initialTask.createdAt).coerceAtLeast(1L)
                    FirebaseAnalyticsManager.logBrowserDownloadCompleted(
                        taskId = initialTask.id,
                        title = initialTask.title,
                        format = targetFile.extension.ifEmpty { "mp4" },
                        qualityLabel = initialTask.qualityLabel,
                        sizeBytes = currentDownloaded.coerceAtLeast(1L),
                        durationMs = durMs,
                        isMonetized = initialTask.isMonetized
                    )
                    onDownloadSuccessSyncMedia(targetFile, initialTask.title, initialTask.isMonetized)
                    isSuccess = true

                } catch (e: Exception) {
                    if (!coroutineContext.isActive) return@withContext
                    Log.e("DownloadEngine", "Download exception (attempt $attempt/$maxRetries): ${e.message}", e)
                    if (attempt < maxRetries) {
                        if (targetFile.exists()) {
                            currentDownloaded = targetFile.length()
                        }
                        delay(attempt * 1500L)
                    } else {
                        failTask(initialTask.id, e.message ?: "Download exception")
                    }
                }
            }
        } finally {
            activeJobs.remove(initialTask.id)
            releaseWakeLockIfIdle()
        }
    }

    private suspend fun downloadHlsStream(
        initialTask: DownloadTask,
        targetFile: File,
        initialPlaylist: String
    ) = withContext(Dispatchers.IO) {
        acquireWakeLock()
        try {
            var mediaPlaylist = initialPlaylist
            var playlistUrl = initialTask.downloadUrl
            val cookieHeader = getCookieHeader(initialTask.downloadUrl, initialTask.referer)

            var masterDepth = 0
            while (mediaPlaylist.contains("#EXT-X-STREAM-INF") && masterDepth < 3) {
                masterDepth++
                data class VariantInfo(
                    val url: String,
                    val bandwidth: Long,
                    val width: Int,
                    val height: Int
                )

                val variants = mutableListOf<VariantInfo>()
                val lines = mediaPlaylist.lines()
                var currentBandwidth = 0L
                var currentWidth = 0
                var currentHeight = 0

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                        val bwMatch = Regex("""BANDWIDTH=(\d+)""").find(trimmed)
                        currentBandwidth = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L

                        val resMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(trimmed)
                        if (resMatch != null) {
                            currentWidth = resMatch.groupValues[1].toIntOrNull() ?: 0
                            currentHeight = resMatch.groupValues[2].toIntOrNull() ?: 0
                        } else {
                            currentWidth = 0
                            currentHeight = 0
                        }
                    } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        variants.add(VariantInfo(trimmed, currentBandwidth, currentWidth, currentHeight))
                        currentBandwidth = 0L
                        currentWidth = 0
                        currentHeight = 0
                    }
                }

                val targetQuality = initialTask.qualityLabel.lowercase()
                val targetHeight = when {
                    targetQuality.contains("4k") || targetQuality.contains("2160") -> 2160
                    targetQuality.contains("1440") || targetQuality.contains("2k") -> 1440
                    targetQuality.contains("1080") -> 1080
                    targetQuality.contains("720") -> 720
                    targetQuality.contains("480") -> 480
                    targetQuality.contains("360") -> 360
                    else -> 1080
                }

                val selectedVariant = if (variants.isNotEmpty()) {
                    val withRes = variants.filter { it.height > 0 }
                    if (withRes.isNotEmpty()) {
                        val candidates = withRes.filter { it.height <= targetHeight }
                        if (candidates.isNotEmpty()) {
                            candidates.maxByOrNull { it.height * 10_000_000L + it.bandwidth }
                        } else {
                            withRes.minByOrNull { it.height }
                        }
                    } else {
                        val safeBw = variants.filter { it.bandwidth in 1..6_000_000 }
                        safeBw.maxByOrNull { it.bandwidth } ?: variants.firstOrNull()
                    }
                } else null

                val bestVariantUrl = selectedVariant?.url ?: ""
                if (bestVariantUrl.isNotEmpty()) {
                    playlistUrl = resolveRelativeUrl(playlistUrl, bestVariantUrl)

                    val reqBuilder = Request.Builder()
                        .url(playlistUrl)
                        .header("User-Agent", SnifferEngine.MOBILE_USER_AGENT)
                    if (!initialTask.referer.isNullOrEmpty()) reqBuilder.header("Referer", initialTask.referer)
                    if (!cookieHeader.isNullOrEmpty()) reqBuilder.header("Cookie", cookieHeader)
                    val resp = okHttpClient.newCall(reqBuilder.build()).execute()
                    mediaPlaylist = resp.body?.string() ?: ""
                    resp.close()
                } else {
                    break
                }
            }

            if (mediaPlaylist.contains("#EXT-X-KEY") && !mediaPlaylist.contains("METHOD=NONE")) {
                failTask(initialTask.id, "Stream is DRM-protected. Please view online.")
                return@withContext
            }

            var initSegmentUrl: String? = null
            val segments = mutableListOf<String>()

            for (line in mediaPlaylist.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXT-X-MAP:")) {
                    val mapMatch = Regex("""URI=["']?([^"',]+)["']?""").find(trimmed)
                    if (mapMatch != null) {
                        val uri = mapMatch.groupValues[1]
                        initSegmentUrl = resolveRelativeUrl(playlistUrl, uri)
                    }
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    segments.add(resolveRelativeUrl(playlistUrl, trimmed))
                }
            }

            if (segments.isEmpty()) {
                failTask(initialTask.id, "No playable video segments found in HLS stream.")
                return@withContext
            }

            val totalSegments = segments.size
            var totalBytesDownloaded = 0L
            var currentTask = initialTask.copy(
                status = DownloadStatus.DOWNLOADING,
                totalBytes = totalSegments * 500_000L
            )
            tasksMap[initialTask.id] = currentTask
            emitUpdate()

            FileOutputStream(targetFile, false).use { output ->
                if (!initSegmentUrl.isNullOrEmpty()) {
                    try {
                        val initReq = Request.Builder()
                            .url(initSegmentUrl)
                            .header("User-Agent", SnifferEngine.MOBILE_USER_AGENT)
                        if (!initialTask.referer.isNullOrEmpty()) initReq.header("Referer", initialTask.referer)
                        if (!cookieHeader.isNullOrEmpty()) initReq.header("Cookie", cookieHeader)
                        val initResp = okHttpClient.newCall(initReq.build()).execute()
                        if (initResp.isSuccessful && initResp.body != null) {
                            val initBytes = initResp.body!!.bytes()
                            output.write(initBytes)
                            totalBytesDownloaded += initBytes.size
                        }
                        initResp.close()
                    } catch (e: Exception) {
                        Log.w("DownloadEngine", "Could not download fMP4 Init Segment: ${e.message}")
                    }
                }

                for ((index, segUrl) in segments.withIndex()) {
                    if (!coroutineContext.isActive) {
                        return@withContext
                    }

                    var segAttempt = 0
                    var segSuccess = false

                    while (segAttempt < 4 && !segSuccess && coroutineContext.isActive) {
                        segAttempt++
                        try {
                            val segReq = Request.Builder()
                                .url(segUrl)
                                .header("User-Agent", if (segAttempt % 2 == 1) SnifferEngine.MOBILE_USER_AGENT else SnifferEngine.DESKTOP_USER_AGENT)
                            if (!initialTask.referer.isNullOrEmpty()) segReq.header("Referer", initialTask.referer)
                            if (!cookieHeader.isNullOrEmpty()) segReq.header("Cookie", cookieHeader)

                            val segResp = okHttpClient.newCall(segReq.build()).execute()
                            if (segResp.isSuccessful && segResp.body != null) {
                                val bytes = segResp.body!!.bytes()
                                segResp.close()
                                output.write(bytes)
                                totalBytesDownloaded += bytes.size
                                segSuccess = true

                                currentTask = currentTask.copy(
                                    downloadedBytes = totalBytesDownloaded,
                                    totalBytes = if (index + 1 == totalSegments) totalBytesDownloaded else (totalBytesDownloaded * totalSegments) / (index + 1),
                                    status = DownloadStatus.DOWNLOADING
                                )
                                tasksMap[initialTask.id] = currentTask
                                emitUpdate()
                            } else {
                                segResp.close()
                                if (segAttempt < 4) delay(800L * segAttempt)
                            }
                        } catch (e: Exception) {
                            if (!coroutineContext.isActive) return@withContext
                            if (segAttempt < 4) delay(800L * segAttempt)
                        }
                    }
                }
                output.flush()
            }

            if (targetFile.exists() && targetFile.length() > 25 * 1024L) {
                currentTask = currentTask.copy(
                    downloadedBytes = targetFile.length(),
                    totalBytes = targetFile.length(),
                    status = DownloadStatus.COMPLETED,
                    completedAt = System.currentTimeMillis(),
                    speedBytesPerSec = 0L,
                    errorMessage = null
                )
                tasksMap[initialTask.id] = currentTask
                emitUpdate()
                val durMs = (System.currentTimeMillis() - initialTask.createdAt).coerceAtLeast(1L)
                FirebaseAnalyticsManager.logBrowserDownloadCompleted(
                    taskId = initialTask.id,
                    title = initialTask.title,
                    format = "m3u8_mp4",
                    qualityLabel = initialTask.qualityLabel,
                    sizeBytes = targetFile.length(),
                    durationMs = durMs,
                    isMonetized = initialTask.isMonetized
                )
                onDownloadSuccessSyncMedia(targetFile, initialTask.title, initialTask.isMonetized)
            } else {
                targetFile.delete()
                failTask(initialTask.id, "Downloaded file is corrupted or too small")
            }

        } catch (e: Exception) {
            if (targetFile.exists() && targetFile.length() > 300 * 1024L) {
                val partialTask = tasksMap[initialTask.id]?.copy(
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = targetFile.length(),
                    totalBytes = targetFile.length(),
                    completedAt = System.currentTimeMillis()
                )
                if (partialTask != null) {
                    tasksMap[initialTask.id] = partialTask
                    emitUpdate()
                    val durMs = (System.currentTimeMillis() - initialTask.createdAt).coerceAtLeast(1L)
                    FirebaseAnalyticsManager.logBrowserDownloadCompleted(
                        taskId = initialTask.id,
                        title = initialTask.title,
                        format = "m3u8_mp4",
                        qualityLabel = initialTask.qualityLabel,
                        sizeBytes = targetFile.length(),
                        durationMs = durMs,
                        isMonetized = initialTask.isMonetized
                    )
                    onDownloadSuccessSyncMedia(targetFile, initialTask.title, initialTask.isMonetized)
                }
            } else {
                targetFile.delete()
                failTask(initialTask.id, e.message ?: "HLS download error")
            }
        } finally {
            releaseWakeLockIfIdle()
        }
    }

    private fun failTask(taskId: String, error: String) {
        val existing = tasksMap[taskId] ?: return
        tasksMap[taskId] = existing.copy(
            status = DownloadStatus.FAILED,
            errorMessage = error,
            speedBytesPerSec = 0L
        )
        emitUpdate()
        val ext = File(existing.targetFilePath).extension.ifEmpty { "mp4" }
        FirebaseAnalyticsManager.logBrowserDownloadFailed(
            taskId = existing.id,
            title = existing.title,
            format = ext,
            errorReason = error,
            isMonetized = existing.isMonetized
        )
        releaseWakeLockIfIdle()
    }

    fun pauseTask(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        val existing = tasksMap[taskId] ?: return
        tasksMap[taskId] = existing.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L)
        emitUpdate()
        releaseWakeLockIfIdle()
    }

    fun resumeTask(taskId: String) {
        val task = tasksMap[taskId] ?: return
        if (task.status == DownloadStatus.DOWNLOADING) return

        val targetFile = File(task.targetFilePath)
        val resumeFrom = if (task.status == DownloadStatus.FAILED) {
            try {
                if (targetFile.exists()) targetFile.delete()
            } catch (_: Exception) {}
            0L
        } else {
            if (targetFile.exists()) targetFile.length() else 0L
        }

        val updatedTask = task.copy(
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = resumeFrom,
            errorMessage = null
        )
        tasksMap[taskId] = updatedTask
        emitUpdate()

        acquireWakeLock()
        val job = scope.launch {
            runDownload(updatedTask, targetFile, resumeFrom)
        }
        activeJobs[taskId] = job
    }

    fun deleteTask(taskId: String, deletePhysicalFile: Boolean = true) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        val existing = tasksMap.remove(taskId)
        if (deletePhysicalFile && existing != null) {
            try {
                val file = File(existing.targetFilePath)
                MonetizedDomainRegistry.unregisterMonetizedDownload(file.name)
                if (file.exists()) {
                    file.delete()
                }
                val recvCopy = File(P2PTransferManager.getReceivedFilesDir(), file.name)
                if (recvCopy.exists()) {
                    recvCopy.delete()
                }
            } catch (e: Exception) {
                Log.e("DownloadEngine", "Could not delete file: ${existing.targetFilePath}", e)
            }
        }
        emitUpdate()
        releaseWakeLockIfIdle()
    }

    private fun hasAudioTrack(file: File): Boolean {
        if (!file.exists() || file.length() < 1024L) return false
        val extractor = android.media.MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.w("DownloadEngine", "hasAudioTrack check failed for ${file.name}: ${e.message}")
            false
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    private fun downloadCompanionAudioFile(
        audioUrl: String,
        referer: String?,
        cookieHeader: String?,
        targetAudioFile: File
    ): Boolean {
        val cleanAudioUrl = SnifferEngine.stripByteRangeParams(audioUrl)
        return try {
            val reqBuilder = Request.Builder()
                .url(cleanAudioUrl)
                .header("User-Agent", SnifferEngine.MOBILE_USER_AGENT)
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
            if (!referer.isNullOrEmpty()) {
                reqBuilder.header("Referer", referer)
            }
            if (!cookieHeader.isNullOrEmpty()) {
                reqBuilder.header("Cookie", cookieHeader)
            }
            val resp = okHttpClient.newCall(reqBuilder.build()).execute()
            if (!resp.isSuccessful || resp.body == null) {
                resp.close()
                return false
            }
            resp.body!!.byteStream().use { input ->
                FileOutputStream(targetAudioFile, false).use { output ->
                    val buf = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                    }
                    output.flush()
                }
            }
            resp.close()
            targetAudioFile.exists() && targetAudioFile.length() > 1024L
        } catch (e: Exception) {
            Log.w("DownloadEngine", "Failed to download companion audio: ${e.message}")
            false
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File): Boolean {
        val videoExtractor = android.media.MediaExtractor()
        val audioExtractor = android.media.MediaExtractor()
        var muxer: android.media.MediaMuxer? = null
        var muxerStarted = false
        return try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            var videoTrackIndex = -1
            var videoFormat: android.media.MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }
            if (videoTrackIndex < 0 || videoFormat == null) return false

            audioExtractor.setDataSource(audioFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: android.media.MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return false

            videoExtractor.selectTrack(videoTrackIndex)
            audioExtractor.selectTrack(audioTrackIndex)

            if (outputFile.exists()) outputFile.delete()
            muxer = android.media.MediaMuxer(
                outputFile.absolutePath,
                android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            if (videoFormat.containsKey(android.media.MediaFormat.KEY_ROTATION)) {
                try {
                    muxer.setOrientationHint(videoFormat.getInteger(android.media.MediaFormat.KEY_ROTATION))
                } catch (_: Exception) {}
            }

            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val bufferSize = 1024 * 1024
            val buffer = java.nio.ByteBuffer.allocate(bufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            var firstVideoPts = -1L
            var maxVideoPts = 0L
            while (true) {
                buffer.clear()
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val rawTime = videoExtractor.sampleTime
                if (rawTime < 0) {
                    videoExtractor.advance()
                    continue
                }
                if (firstVideoPts < 0L) firstVideoPts = rawTime
                val pts = (rawTime - firstVideoPts).coerceAtLeast(0L)
                if (pts > maxVideoPts) maxVideoPts = pts
                val sampleFlags = videoExtractor.sampleFlags
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = pts
                bufferInfo.flags = if ((sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            var firstAudioPts = -1L
            var lastAudioPts = -1L
            val audioLimitUs = if (maxVideoPts > 0L) maxVideoPts + 1_500_000L else Long.MAX_VALUE
            while (true) {
                buffer.clear()
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val rawTime = audioExtractor.sampleTime
                if (rawTime < 0) {
                    audioExtractor.advance()
                    continue
                }
                if (firstAudioPts < 0L) firstAudioPts = rawTime
                var pts = (rawTime - firstAudioPts).coerceAtLeast(0L)
                if (pts > audioLimitUs) break
                if (pts <= lastAudioPts) {
                    pts = lastAudioPts + 1L
                }
                lastAudioPts = pts
                val sampleFlags = audioExtractor.sampleFlags
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = pts
                bufferInfo.flags = if ((sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                audioExtractor.advance()
            }

            muxer.stop()
            muxerStarted = false
            outputFile.exists() && outputFile.length() > videoFile.length()
        } catch (e: Exception) {
            Log.e("DownloadEngine", "MediaMuxer error: ${e.message}", e)
            false
        } finally {
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (_: Exception) {}
            try {
                videoExtractor.release()
            } catch (_: Exception) {}
            try {
                audioExtractor.release()
            } catch (_: Exception) {}
        }
    }
}
