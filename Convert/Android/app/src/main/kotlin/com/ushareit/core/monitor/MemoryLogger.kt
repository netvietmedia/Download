package com.ushareit.core.monitor

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.work.*
import com.ushareit.core.analytics.FirebaseAnalyticsManager
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Quản lý tác vụ đo lường, giám sát và ghi log bộ nhớ (RAM, JVM Heap, Native Heap, Storage).
 * Hỗ trợ ghi vào file cục bộ, Logcat, tự động cảnh báo Low Memory và đồng bộ Crashlytics.
 */
object MemoryLogger {

    private const val TAG = "ShareNow_Memory"
    private const val MAX_LOG_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB tối đa cho file log
    private const val LOG_FILE_NAME = "memory_monitor.log"

    private var monitorJob: Job? = null
    private var isInitialized = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * Dữ liệu ảnh chụp bộ nhớ tại 1 thời điểm
     */
    data class MemorySnapshot(
        val timestamp: Long,
        // JVM Heap
        val jvmHeapUsedMb: Double,
        val jvmHeapMaxMb: Double,
        val jvmHeapTotalMb: Double,
        val jvmHeapPercent: Int,
        // Native Heap
        val nativeHeapAllocatedMb: Double,
        val nativeHeapSizeMb: Double,
        // System RAM
        val systemRamAvailMb: Double,
        val systemRamTotalMb: Double,
        val systemRamLowMemory: Boolean,
        val systemRamThresholdMb: Double,
        // Internal Storage
        val storageFreeMb: Double,
        val storageTotalMb: Double
    )

    // Cờ trạng thái điều khiển: Đặt false để tạm thời dừng ghi log và dừng task ngầm (lúc khác chạy tiếp)
    @Volatile
    var isLoggingEnabled: Boolean = false

    /**
     * Khởi tạo MemoryLogger và đăng ký callback theo dõi bộ nhớ hệ thống
     */
    fun init(context: Context) {
        val appContext = context.applicationContext
        if (!isLoggingEnabled) {
            Log.i(TAG, "MemoryLogger is temporarily PAUSED. Skipping periodic tasks and background workers.")
            stopBackgroundWork(appContext)
            return
        }

        if (isInitialized) return
        isInitialized = true

        // Đăng ký lắng nghe cảnh báo tụt RAM từ hệ điều hành Android
        appContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                handleMemoryTrim(appContext, level)
            }

            override fun onConfigurationChanged(newConfig: Configuration) {}
            override fun onLowMemory() {
                handleLowMemory(appContext)
            }
        })

        // Bắt đầu task ghi log định kỳ trong quá trình app đang hoạt động (mỗi 60s)
        startForegroundPeriodicLogging(appContext, intervalMs = 60_000L)

        // Lên lịch tác vụ ngầm định kỳ bằng WorkManager (15 phút / lần theo chuẩn Android)
        scheduleBackgroundWork(appContext)

        // Ghi log khởi động ban đầu
        logCurrentMemory(appContext, "AppLaunch")
        Log.i(TAG, "MemoryLogger & LowMemory listener initialized successfully.")
    }

    /**
     * Tạm dừng ghi log và hủy tác vụ ngầm WorkManager
     */
    fun stopLogging(context: Context) {
        isLoggingEnabled = false
        stopForegroundPeriodicLogging()
        stopBackgroundWork(context)
        Log.i(TAG, "MemoryLogger paused successfully.")
    }

    /**
     * Tiếp tục chạy lại tác vụ ghi log bộ nhớ khi cần
     */
    fun resumeLogging(context: Context) {
        isLoggingEnabled = true
        isInitialized = false
        init(context)
        Log.i(TAG, "MemoryLogger resumed successfully.")
    }

    /**
     * Thu thập thông số bộ nhớ hiện thời
     */
    fun captureSnapshot(context: Context): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val jvmTotal = runtime.totalMemory()
        val jvmFree = runtime.freeMemory()
        val jvmMax = runtime.maxMemory()
        val jvmUsed = jvmTotal - jvmFree

        val jvmUsedMb = jvmUsed / (1024.0 * 1024.0)
        val jvmMaxMb = jvmMax / (1024.0 * 1024.0)
        val jvmTotalMb = jvmTotal / (1024.0 * 1024.0)
        val jvmPercent = if (jvmMax > 0) ((jvmUsed * 100) / jvmMax).toInt() else 0

        // Native Heap (C++ P2P socket engine, image buffers)
        val nativeAllocated = Debug.getNativeHeapAllocatedSize()
        val nativeTotal = Debug.getNativeHeapSize()
        val nativeAllocMb = nativeAllocated / (1024.0 * 1024.0)
        val nativeTotalMb = nativeTotal / (1024.0 * 1024.0)

        // System RAM
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val ramAvailMb = memInfo.availMem / (1024.0 * 1024.0)
        val ramTotalMb = memInfo.totalMem / (1024.0 * 1024.0)
        val ramThresholdMb = memInfo.threshold / (1024.0 * 1024.0)

        // Storage
        var storageFreeMb = 0.0
        var storageTotalMb = 0.0
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            storageFreeMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024.0 * 1024.0)
            storageTotalMb = (stat.blockCountLong * stat.blockSizeLong) / (1024.0 * 1024.0)
        } catch (e: Throwable) {
            Log.w(TAG, "Could not read storage: ${e.message}")
        }

        return MemorySnapshot(
            timestamp = System.currentTimeMillis(),
            jvmHeapUsedMb = jvmUsedMb,
            jvmHeapMaxMb = jvmMaxMb,
            jvmHeapTotalMb = jvmTotalMb,
            jvmHeapPercent = jvmPercent,
            nativeHeapAllocatedMb = nativeAllocMb,
            nativeHeapSizeMb = nativeTotalMb,
            systemRamAvailMb = ramAvailMb,
            systemRamTotalMb = ramTotalMb,
            systemRamLowMemory = memInfo.lowMemory,
            systemRamThresholdMb = ramThresholdMb,
            storageFreeMb = storageFreeMb,
            storageTotalMb = storageTotalMb
        )
    }

    /**
     * Ghi nhật ký bộ nhớ tức thì với nhãn phân loại (VD: BeforeTransfer, AfterClean, OnResume...)
     */
    fun logCurrentMemory(context: Context, tagLabel: String = "Checkpoint"): MemorySnapshot? {
        if (!isLoggingEnabled) return null
        val snapshot = captureSnapshot(context)
        val timeStr = dateFormat.format(Date(snapshot.timestamp))

        val logLine = String.format(
            Locale.US,
            "[%s] [%s] JVM: %.1f/%.1f MB (%d%%) | Native: %.1f/%.1f MB | RAM Avail: %.1f/%.1f MB (Low: %b) | Storage Free: %.1f MB",
            timeStr,
            tagLabel,
            snapshot.jvmHeapUsedMb,
            snapshot.jvmHeapMaxMb,
            snapshot.jvmHeapPercent,
            snapshot.nativeHeapAllocatedMb,
            snapshot.nativeHeapSizeMb,
            snapshot.systemRamAvailMb,
            snapshot.systemRamTotalMb,
            snapshot.systemRamLowMemory,
            snapshot.storageFreeMb
        )

        // 1. Ghi ra Logcat
        Log.i(TAG, logLine)

        // 2. Ghi vào tệp log cục bộ trên thiết bị
        writeToFile(context, logLine)

        // 3. Đồng bộ thông số RAM và JVM lên Firebase Crashlytics để đính kèm khi có sự cố
        try {
            FirebaseAnalyticsManager.setCustomCrashKey("mem_jvm_used_mb", snapshot.jvmHeapUsedMb.toLong())
            FirebaseAnalyticsManager.setCustomCrashKey("mem_ram_avail_mb", snapshot.systemRamAvailMb.toLong())
            FirebaseAnalyticsManager.setCustomCrashKey("mem_is_low", snapshot.systemRamLowMemory)
        } catch (e: Throwable) {}

        return snapshot
    }

    /**
     * Bắt đầu tác vụ Coroutine ghi log định kỳ khi ứng dụng đang hoạt động
     */
    fun startForegroundPeriodicLogging(context: Context, intervalMs: Long = 60_000L) {
        monitorJob?.cancel()
        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    logCurrentMemory(context, "Periodic_Active")
                } catch (e: Throwable) {
                    Log.w(TAG, "Periodic memory log error: ${e.message}")
                }
                delay(intervalMs)
            }
        }
    }

    /**
     * Dừng tác vụ ghi log định kỳ
     */
    fun stopForegroundPeriodicLogging() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Lên lịch tác vụ ngầm chạy định kỳ bằng Android WorkManager
     */
    fun scheduleBackgroundWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val memoryWorkRequest = PeriodicWorkRequestBuilder<MemoryLogWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("ShareNow_MemoryMonitorWorker")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ShareNow_MemoryLogWork",
                ExistingPeriodicWorkPolicy.KEEP,
                memoryWorkRequest
            )
            Log.d(TAG, "WorkManager periodic memory task scheduled.")
        } catch (e: Throwable) {
            Log.w(TAG, "Could not schedule WorkManager task: ${e.message}")
        }
    }

    /**
     * Hủy tác vụ WorkManager chạy ngầm
     */
    fun stopBackgroundWork(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork("ShareNow_MemoryLogWork")
            Log.d(TAG, "WorkManager periodic memory task cancelled.")
        } catch (e: Throwable) {
            Log.w(TAG, "Could not cancel WorkManager task: ${e.message}")
        }
    }

    /**
     * Xử lý khi hệ điều hành gửi tín hiệu thiếu RAM (onTrimMemory)
     */
    private fun handleMemoryTrim(context: Context, level: Int) {
        val levelName = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
            else -> "UNKNOWN_$level"
        }

        val msg = "⚠️ System Memory Trim Alert: level=$levelName ($level)"
        Log.w(TAG, msg)
        writeToFile(context, "[${dateFormat.format(Date())}] [TRIM_ALERT] $msg")
        FirebaseAnalyticsManager.log("onTrimMemory: $levelName")

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // Giải phóng tài nguyên cache tức thời để tránh crash OOM
            try {
                com.bumptech.glide.Glide.get(context).clearMemory()
                System.gc()
                Log.i(TAG, "Emergency cache cleared on low memory level $levelName")
            } catch (e: Throwable) {
                Log.w(TAG, "Cache clear fallback: ${e.message}")
            }
        }
    }

    /**
     * Xử lý khi bộ nhớ máy xuống ngưỡng cạn kiệt (onLowMemory)
     */
    private fun handleLowMemory(context: Context) {
        val msg = "🚨 CRITICAL LOW MEMORY: System is aggressively terminating background processes!"
        Log.e(TAG, msg)
        writeToFile(context, "[${dateFormat.format(Date())}] [LOW_MEMORY_CRITICAL] $msg")
        FirebaseAnalyticsManager.recordException(Exception("CRITICAL_LOW_MEMORY_RECEIVED"))

        try {
            com.bumptech.glide.Glide.get(context).clearMemory()
            System.gc()
        } catch (e: Throwable) {}
    }

    /**
     * Ghi nhật ký an toàn vào file trên bộ nhớ trong của ứng dụng
     */
    @Synchronized
    private fun writeToFile(context: Context, text: String) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val logFile = File(logDir, LOG_FILE_NAME)

            // Kiểm tra kích thước file log, nếu vượt 2MB thì sao lưu xoay vòng
            if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE_BYTES) {
                val backupFile = File(logDir, "${LOG_FILE_NAME}.old")
                if (backupFile.exists()) backupFile.delete()
                logFile.renameTo(backupFile)
            }

            FileWriter(logFile, true).use { writer ->
                writer.appendLine(text)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not write memory log to file: ${e.message}")
        }
    }

    /**
     * Đọc toàn bộ nội dung file log bộ nhớ gần nhất
     */
    fun readLogs(context: Context): String {
        return try {
            val logFile = File(context.filesDir, "logs/$LOG_FILE_NAME")
            if (logFile.exists()) {
                logFile.readText()
            } else {
                "No memory logs available yet."
            }
        } catch (e: Throwable) {
            "Error reading memory logs: ${e.message}"
        }
    }
}
