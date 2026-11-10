package com.ushareit.core.p2p

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.nextai.sharenow.R
import com.ushareit.ui.transfer.ReceiveActivity
import kotlinx.coroutines.*
import java.io.*
import java.net.*

data class TransferFileInfo(
    val name: String,
    val size: Long,
    val mimeType: String = "*/*",
    val uriString: String? = null,
    val filePath: String? = null
) : Serializable

data class TransferRequest(
    val senderDevice: String,
    val senderIp: String,
    val files: List<TransferFileInfo>,
    val totalBytes: Long
) : Serializable

data class TransferResponse(
    val accepted: Boolean,
    val message: String = ""
) : Serializable

data class DiscoveredDevice(
    val name: String,
    val ip: String,
    val port: Int = 8988,
    val lastSeen: Long = System.currentTimeMillis()
)

data class PendingTransfer(
    val request: TransferRequest,
    val onDecision: (Boolean) -> Unit
)

object P2PTransferManager {

    private const val TAG = "P2PTransfer"
    const val TCP_PORT = 8988
    const val UDP_PORT = 8989
    private val gson = Gson()

    // Danh sách các tệp vừa nhận thành công trong phiên hiện tại
    val lastReceivedFiles = mutableListOf<File>()

    // Dữ liệu truyền nhận hiện hành để truyền qua Activity
    var currentTransferRequest: TransferRequest? = null
    var activeSenderSocket: Socket? = null
    var activeReceiverSocket: Socket? = null

    // Yêu cầu nhận tệp đang chờ người dùng quyết định (nếu ReceiveActivity chưa mở)
    var pendingIncomingTransfer: PendingTransfer? = null

    // Cờ trạng thái truyền thật qua TCP socket
    var isRealTransferActive: Boolean = false

    // Callbacks tiến trình truyền dữ liệu thời gian thực
    var onTransferProgressUpdated: ((progress: Int, speedMb: Float, currentFile: String) -> Unit)? = null
    var onTransferCompleted: ((List<File>) -> Unit)? = null

    @Volatile
    private var engineInstance: P2PTransferEngine? = null

    @Synchronized
    fun getEngine(context: Context): P2PTransferEngine {
        if (engineInstance == null) {
            engineInstance = P2PTransferEngine(context.applicationContext)
        }
        return engineInstance!!
    }

    /**
     * Khởi động server lắng nghe socket P2P chạy ngầm cho máy nhận
     */
    fun startServerIfNeeded(context: Context) {
        val engine = getEngine(context)
        engine.startReceiverServer { ip, port ->
            Log.i(TAG, "Global P2P Receiver Server listening at $ip:$port")
        }
    }

    /**
     * Bắn thông báo Heads-Up và rung chuông khi có thiết bị khác gửi tệp tới
     */
    fun notifyIncomingTransfer(context: Context, request: TransferRequest) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val channelId = "sharenow_incoming_transfers"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.p2p_notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.p2p_notif_channel_desc)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, ReceiveActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("EXTRA_AUTO_ACCEPT_PROMPT", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                8988,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val mb = request.totalBytes / (1024.0 * 1024.0)
            val sizeStr = if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_app_box)
                .setContentTitle("📥 " + context.getString(R.string.p2p_notif_incoming_title, request.senderDevice))
                .setContentText(context.getString(R.string.p2p_notif_incoming_content, request.files.size, sizeStr))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .build()

            notificationManager.notify(8988, notification)

            // Rung phản hồi
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notification trigger error: ${e.message}")
        }
    }

    /**
     * Lấy địa chỉ IP mạng nội bộ của thiết bị hiện tại (Wi-Fi hoặc Hotspot)
     */
    fun notifyTransferSuccess(context: Context, fileCount: Int, senderName: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "sharenow_p2p_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Share Now P2P",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo nhận file hoàn tất"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
            val intent = Intent(context, com.ushareit.ui.transfer.ReceivedFilesActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                8989,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_share_now_logo)
                .setContentTitle(context.getString(R.string.p2p_notif_transfer_success_title))
                .setContentText(context.getString(R.string.p2p_notif_transfer_success_content, fileCount, senderName))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            notificationManager.notify(8989, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Notification success error: ${e.message}")
        }
    }

    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (e: Exception) {}

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP: ${e.message}")
        }
        return "192.168.1.2"
    }

    /**
     * Tên thiết bị hiển thị thân thiện (ví dụ: Samsung Galaxy A50s)
     */
    fun getDeviceDisplayName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    /**
     * Thư mục lưu tệp nhận an toàn trên thiết bị (/Download/ShareNow)
     */
    fun getReceivedFilesDir(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val shareNowDir = File(downloadDir, "ShareNow")
        if (!shareNowDir.exists()) {
            shareNowDir.mkdirs()
        }
        return shareNowDir
    }

    /**
     * Lấy toàn bộ danh sách tệp đã nhận từ trước đến nay trong thư mục Download/ShareNow
     */
    fun getAllReceivedFiles(): List<File> {
        val dir = getReceivedFilesDir()
        // Dọn dẹp các tệp 0-byte hoặc tệp rác demo cũ nếu có
        dir.listFiles()?.filter { it.isFile && (it.length() == 0L || it.name.startsWith(".")) }?.forEach {
            try { it.delete() } catch (_: Exception) {}
        }
        val extraDirs = mutableListOf(dir)
        try {
            extraDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ShareNow"))
            extraDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "ShareNow"))
            extraDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ShareNow"))
            extraDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ShareNow"))
            val appExtDir = com.ushareit.app.ShareItApplication.instance.getExternalFilesDir(null)
            if (appExtDir != null) {
                extraDirs.add(File(appExtDir, "Videos"))
                extraDirs.add(File(appExtDir, "Music"))
                extraDirs.add(File(appExtDir, "Pictures"))
                extraDirs.add(File(appExtDir, "Documents"))
            }
            val sharedPoolRoot = com.ushareit.app.ShareItApplication.instance.getExternalFilesDir("SharedPool")
            if (sharedPoolRoot != null && sharedPoolRoot.exists()) {
                sharedPoolRoot.listFiles()?.filter { it.isDirectory }?.forEach { extraDirs.add(it) }
            }
            extraDirs.add(File(com.ushareit.app.ShareItApplication.instance.filesDir, "Videos"))
        } catch (_: Exception) {}
        val files = extraDirs.distinctBy { it.absolutePath }.flatMap { d ->
            d.listFiles()?.filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".part") && it.length() > 0 } ?: emptyList()
        }.distinctBy { it.name to it.length() }
        return files.sortedByDescending { it.lastModified() }
    }

    /**
     * Xóa tệp đồng bộ trên cả Received Files và thư mục Video Downloads
     */
    fun deleteReceivedFileAndSync(context: Context, file: File): Boolean {
        var deletedAny = false
        val fileName = file.name
        val filePath = file.absolutePath
        try {
            if (file.exists() && file.delete()) {
                deletedAny = true
            }
        } catch (_: Exception) {}

        val dirsToCheck = mutableListOf(getReceivedFilesDir())
        try {
            dirsToCheck.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ShareNow"))
            dirsToCheck.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "ShareNow"))
            dirsToCheck.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ShareNow"))
            dirsToCheck.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ShareNow"))
            val appExtDir = context.applicationContext.getExternalFilesDir(null)
            if (appExtDir != null) {
                dirsToCheck.add(File(appExtDir, "Videos"))
                dirsToCheck.add(File(appExtDir, "Music"))
                dirsToCheck.add(File(appExtDir, "Pictures"))
                dirsToCheck.add(File(appExtDir, "Documents"))
            }
            val sharedPoolRoot = context.applicationContext.getExternalFilesDir("SharedPool")
            if (sharedPoolRoot != null && sharedPoolRoot.exists()) {
                sharedPoolRoot.listFiles()?.filter { it.isDirectory }?.forEach { dirsToCheck.add(it) }
            }
            dirsToCheck.add(File(context.applicationContext.filesDir, "Videos"))
        } catch (_: Exception) {}

        for (dir in dirsToCheck.distinctBy { it.absolutePath }) {
            try {
                val mirrorFile = File(dir, fileName)
                if (mirrorFile.exists() && mirrorFile.delete()) {
                    deletedAny = true
                }
            } catch (_: Exception) {}
        }

        try {
            com.ushareit.core.downloader.DownloadEngine.notifyExternalFileDeleted(fileName, filePath)
        } catch (_: Exception) {}

        try {
            scanMediaFile(context, file)
        } catch (_: Exception) {}

        return deletedAny
    }

    /**
     * Mở tệp trực tiếp (ảnh xem ngay, video phát luôn, apk cài đặt,...)
     */
    fun openFile(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: when {
                extension in listOf("jpg", "jpeg", "png", "webp", "gif") -> "image/*"
                extension in listOf("mp4", "mkv", "avi", "mov", "3gp") -> "video/*"
                extension in listOf("mp3", "wav", "aac", "flac") -> "audio/*"
                extension == "apk" -> "application/vnd.android.package-archive"
                extension == "pdf" -> "application/pdf"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open file: ${file.name}", e)
            try {
                // Fallback với intent chia sẻ
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.p2p_open_file_with)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * Đăng ký tệp vào MediaStore để thư viện Ảnh/Video hiển thị ngay
     */
    fun scanMediaFile(context: Context, file: File) {
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                null
            ) { path, uri ->
                Log.d(TAG, "Scanned $path to MediaStore: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Media scan error: ${e.message}")
        }
    }
}
