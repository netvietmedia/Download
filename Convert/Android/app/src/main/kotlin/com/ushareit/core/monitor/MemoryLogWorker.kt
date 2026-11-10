package com.ushareit.core.monitor

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Task chạy ngầm định kỳ bằng Android WorkManager để ghi log bộ nhớ hệ thống
 */
class MemoryLogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!MemoryLogger.isLoggingEnabled) {
                return@withContext Result.success()
            }
            val snapshot = MemoryLogger.logCurrentMemory(applicationContext, "WorkManager_Background")
            if (snapshot != null) {
                Log.d("MemoryLogWorker", "Background memory logged: RAM Free=${snapshot.systemRamAvailMb}MB, JVM Used=${snapshot.jvmHeapUsedMb}MB")
            }
            Result.success()
        } catch (e: Throwable) {
            Log.e("MemoryLogWorker", "Failed to log memory in worker: ${e.message}", e)
            Result.retry()
        }
    }
}
