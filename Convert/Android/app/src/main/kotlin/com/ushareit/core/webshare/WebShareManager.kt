package com.ushareit.core.webshare

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ushareit.app.ShareItApplication
import com.ushareit.core.p2p.P2PTransferManager
import com.ushareit.ui.webshare.WebShareActivity
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * WebShareManager - Quản lý máy chủ WebShare vòng đời toàn cục (Singleton).
 * Cho phép WebShare tiếp tục chạy ngầm khi người dùng chuyển sang mục khác trong app
 * hoặc chuyển sang sử dụng ứng dụng khác, màn hình tắt.
 */
object WebShareManager {

    private const val TAG = "WebShareManager"
    const val PORT = 8888

    private var server: WebShareServer? = null
    private val listeners = CopyOnWriteArrayList<WebShareServer.WebShareListener>()

    var currentUrl: String = ""
        private set

    val isRunning: Boolean
        get() = server?.isRunning == true

    private val internalListener = object : WebShareServer.WebShareListener {
        override fun onServerStarted(ip: String, port: Int) {
            currentUrl = "http://$ip:$port"
            for (listener in listeners) {
                try {
                    listener.onServerStarted(ip, port)
                } catch (e: Exception) {
                    Log.e(TAG, "Listener onServerStarted error: ${e.message}")
                }
            }
        }

        override fun onServerStopped() {
            for (listener in listeners) {
                try {
                    listener.onServerStopped()
                } catch (e: Exception) {
                    Log.e(TAG, "Listener onServerStopped error: ${e.message}")
                }
            }
        }

        override fun onClientConnected(clientIp: String) {
            for (listener in listeners) {
                try {
                    listener.onClientConnected(clientIp)
                } catch (e: Exception) {
                    Log.e(TAG, "Listener onClientConnected error: ${e.message}")
                }
            }
        }

        override fun onFileDownloaded(fileName: String, bytes: Long) {
            for (listener in listeners) {
                try {
                    listener.onFileDownloaded(fileName, bytes)
                } catch (e: Exception) {
                    Log.e(TAG, "Listener onFileDownloaded error: ${e.message}")
                }
            }
        }

        override fun onFileUploaded(file: File) {
            for (listener in listeners) {
                try {
                    listener.onFileUploaded(file)
                } catch (e: Exception) {
                    Log.e(TAG, "Listener onFileUploaded error: ${e.message}")
                }
            }
        }

        override fun onError(error: String) {
            for (listener in listeners) {
                try {
                    listener.onError(error)
                } catch (e: Exception) {
                    Log.e(TAG, "Listener onError error: ${e.message}")
                }
            }
        }

        override fun onPcRequestFilePicker(category: String) {
            if (listeners.isNotEmpty()) {
                for (listener in listeners) {
                    try {
                        listener.onPcRequestFilePicker(category)
                    } catch (e: Exception) {
                        Log.e(TAG, "Listener onPcRequestFilePicker error: ${e.message}")
                    }
                }
            } else {
                try {
                    val ctx = ShareItApplication.instance
                    val intent = Intent(ctx, WebShareActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("EXTRA_TRIGGER_PICKER_CATEGORY", category)
                    }
                    ctx.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch WebShareActivity for picker: ${e.message}")
                }
            }
        }
    }

    @Synchronized
    fun startServer(context: Context): String {
        val appContext = context.applicationContext
        val ip = P2PTransferManager.getLocalIpAddress(appContext)
        currentUrl = "http://$ip:$PORT"

        if (server == null || !server!!.isRunning) {
            server = WebShareServer(appContext, PORT, internalListener).apply {
                start()
            }
            Log.i(TAG, "WebShare server started at $currentUrl")
        } else {
            server?.clearCache()
        }

        // Khởi động Foreground Service để giữ tiến trình và kết nối mạng ngầm
        try {
            WebShareService.start(appContext, currentUrl)
        } catch (e: Exception) {
            Log.w(TAG, "Start Foreground Service warning: ${e.message}")
        }

        return currentUrl
    }

    fun refreshFileCache(category: String? = null) {
        try {
            server?.clearCache(category)
        } catch (e: Exception) {
            Log.w(TAG, "refreshFileCache warning: ${e.message}")
        }
    }

    @Synchronized
    fun stopServer(context: Context) {
        val appContext = context.applicationContext
        try {
            server?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server: ${e.message}")
        }
        server = null
        currentUrl = ""

        try {
            WebShareService.stop(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "Stop Foreground Service warning: ${e.message}")
        }

        Log.i(TAG, "WebShare server stopped completely.")
    }

    fun registerListener(listener: WebShareServer.WebShareListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            // Nếu server đã sẵn sàng chạy thì báo ngay cho listener mới
            if (isRunning && currentUrl.isNotEmpty()) {
                val ip = P2PTransferManager.getLocalIpAddress(ShareItApplication.instance)
                listener.onServerStarted(ip, PORT)
            }
        }
    }

    fun unregisterListener(listener: WebShareServer.WebShareListener) {
        listeners.remove(listener)
    }
}
