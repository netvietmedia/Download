package com.ushareit.core.p2p

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ServerSocket
import java.net.Socket

class HotspotServerEngine(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    suspend fun startServer(port: Int = 8988, onFileReceived: (File) -> Unit) = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            Log.i("HotspotServer", "P2P Socket Server listening on port $port")

            while (isRunning) {
                val clientSocket = serverSocket?.accept() ?: break
                handleIncomingClient(clientSocket, onFileReceived)
            }
        } catch (e: Exception) {
            Log.e("HotspotServer", "Server error: ${e.message}")
        }
    }

    private fun handleIncomingClient(socket: Socket, onFileReceived: (File) -> Unit) {
        Thread {
            try {
                val dis = socket.getInputStream()
                // Xử lý stream truyền file
                socket.close()
            } catch (e: Exception) {
                Log.e("HotspotServer", "Client handler error: ${e.message}")
            }
        }.start()
    }

    fun stopServer() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
    }
}

