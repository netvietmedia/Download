package com.ushareit.core.downloader

import java.io.Serializable

data class VideoQuality(
    val label: String,       // 1080p FHD, 720p HD, 480p SD, MP3 Audio
    val format: String,      // mp4, m3u8, mp3
    val approximateSize: Long, // Bytes
    val downloadUrl: String,
    val isAudioOnly: Boolean = false
) : Serializable

data class VideoInfo(
    val title: String,
    val sourcePageUrl: String,
    val thumbnailUrl: String,
    val durationSeconds: Int = 0,
    val qualities: List<VideoQuality> = emptyList(),
    val extractedHeaders: Map<String, String> = emptyMap(),
    val isStreamM3u8: Boolean = false
) : Serializable

data class DetectedVideoItem(
    val id: String,
    val title: String,
    val url: String,
    val qualityLabel: String,
    val format: String,         // "mp4", "m3u8", "mp3"
    val sizeBytes: Long = 0L,   // > 0L when known or probed via Content-Length / HLS
    val durationSeconds: Int = 0,
    val referer: String? = null,
    val userAgent: String? = null,
    val isAudioOnly: Boolean = false,
    val isLikelyAd: Boolean = false,
    val isSizeProbed: Boolean = false,
    val detectedAt: Long = System.currentTimeMillis()
) : Serializable

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTask(
    val id: String,
    val title: String,
    val downloadUrl: String,
    val targetFilePath: String,
    val qualityLabel: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val referer: String? = null,
    val isMonetized: Boolean = false
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}

data class TabModel(
    val id: String,
    val title: String,
    val currentUrl: String,
    val faviconUrl: String? = null,
    val isIncognito: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val lastAccessed: Long = System.currentTimeMillis()
)
