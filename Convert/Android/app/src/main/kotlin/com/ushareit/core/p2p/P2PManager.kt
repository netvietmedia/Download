package com.ushareit.core.p2p

import android.content.Context
import java.io.File

object P2PManager {

    private var wifiEngine: WifiDirectTransferEngine? = null
    private var hotspotEngine: HotspotServerEngine? = null

    fun init(context: Context) {
        wifiEngine = WifiDirectTransferEngine(context)
        hotspotEngine = HotspotServerEngine(context)
    }

    fun startReceiveMode(context: Context, onFileReady: (File) -> Unit) {
        // Khởi động trạm nhận file
    }

    fun startSendFiles(targetIp: String, files: List<File>, onProgress: (Int, Long) -> Unit) {
        // Gửi danh sách tệp
    }
}

