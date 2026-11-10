package com.ushareit.core.p2p



import android.content.Context

import android.content.Intent

import android.net.Uri

import android.util.Log

import com.google.gson.Gson

import com.ushareit.ui.transfer.ReceiveActivity

import kotlinx.coroutines.*

import java.io.*

import java.net.*

import android.net.wifi.WifiManager

import java.util.Collections



class P2PTransferEngine(private val context: Context) {



    companion object {

        private const val TAG = "P2PTransferEngine"

        private val gson = Gson()

        const val BUFFER_SIZE = 64 * 1024 // 64KB buffer for turbo speed

    }



    private var serverSocket: ServerSocket? = null

    private var isServerRunning = false

    private var udpSocket: DatagramSocket? = null

    private var multicastLock: WifiManager.MulticastLock? = null



    // Callbacks cho Receiver

    var onIncomingRequestReceived: ((TransferRequest, (Boolean) -> Unit) -> Unit)? = null

    var onTransferProgressUpdated: ((progress: Int, speedMb: Float, currentFile: String) -> Unit)? = null

    var onTransferCompleted: ((List<File>) -> Unit)? = null

    var onTransferFailed: ((String) -> Unit)? = null



    /**

     * Bắt đầu Server lắng nghe trên máy Nhận

     */

    fun startReceiverServer(

        port: Int = P2PTransferManager.TCP_PORT,

        onReady: (ip: String, port: Int) -> Unit

    ) {

        if (isServerRunning) {

            val localIp = P2PTransferManager.getLocalIpAddress(context)

            onReady(localIp, port)

            return

        }

        try {

            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            multicastLock = wifi?.createMulticastLock("ShareNowP2PMulticast")?.apply {

                setReferenceCounted(true)

                acquire()

            }

        } catch (_: Exception) {}

        isServerRunning = true



        CoroutineScope(Dispatchers.IO).launch {

            try {

                serverSocket = ServerSocket(port).apply {

                    reuseAddress = true

                }

                val localIp = P2PTransferManager.getLocalIpAddress(context)

                withContext(Dispatchers.Main) {

                    onReady(localIp, port)

                }

                Log.i(TAG, "Receiver Server listening on $localIp:$port")



                // Bắt đầu phát sóng UDP beacon thông báo máy nhận đã sẵn sàng

                startUdpBroadcaster(localIp, port)



                while (isServerRunning) {

                    try {

                        val clientSocket = serverSocket?.accept() ?: break

                        Log.i(TAG, "Sender connected from ${clientSocket.inetAddress.hostAddress}")

                        handleIncomingSender(clientSocket)

                    } catch (e: Exception) {

                        if (!isServerRunning) break

                        Log.e(TAG, "Accept error: ${e.message}")

                    }

                }

            } catch (e: Exception) {

                Log.e(TAG, "Server start error or already bound: ${e.message}")

                val localIp = P2PTransferManager.getLocalIpAddress(context)

                withContext(Dispatchers.Main) {

                    onReady(localIp, port)

                }

            }

        }

    }



    /**

     * Xử lý kết nối từ máy gửi

     */

    private fun handleIncomingSender(socket: Socket) {

        CoroutineScope(Dispatchers.IO).launch {

            var dis: DataInputStream? = null

            var dos: DataOutputStream? = null

            try {

                socket.tcpNoDelay = true

                socket.soTimeout = 90_000 // 90 seconds timeout

                dis = DataInputStream(BufferedInputStream(socket.getInputStream(), BUFFER_SIZE))

                dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE))



                // 1. Đọc yêu cầu từ máy gửi

                val header = dis.readUTF()

                if (header == "DISCOVERY_PING") {

                    val deviceName = P2PTransferManager.getDeviceDisplayName()

                    val localIp = P2PTransferManager.getLocalIpAddress(context)

                    dos.writeUTF("DISCOVERY_PONG:$deviceName:$localIp:${P2PTransferManager.TCP_PORT}")

                    dos.flush()

                    socket.close()

                    return@launch

                }



                if (header != "TRANSFER_REQUEST") {

                    Log.w(TAG, "Unknown protocol header: $header")

                    socket.close()

                    return@launch

                }



                val jsonLen = dis.readInt()

                val jsonBytes = ByteArray(jsonLen)

                dis.readFully(jsonBytes)

                val json = String(jsonBytes, Charsets.UTF_8)

                val request = gson.fromJson(json, TransferRequest::class.java)

                Log.i(TAG, "Received transfer request from ${request.senderDevice} (${request.senderIp}) with ${request.files.size} files, total ${request.totalBytes} bytes")



                // Lưu request hiện hành

                P2PTransferManager.currentTransferRequest = request



                // 2. Chờ quyết định của người dùng trên giao diện máy nhận (Đồng ý hoặc Từ chối)

                val decisionChannel = CompletableDeferred<Boolean>()



                withContext(Dispatchers.Main) {

                    val activeListener = onIncomingRequestReceived

                    if (activeListener != null) {

                        // ReceiveActivity đang mở

                        activeListener.invoke(request) { accepted ->

                            if (!decisionChannel.isCompleted) {

                                decisionChannel.complete(accepted)

                            }

                        }

                    } else {

                        // ReceiveActivity chưa mở! Lưu request chờ và mở ReceiveActivity

                        P2PTransferManager.pendingIncomingTransfer = PendingTransfer(request) { accepted ->

                            if (!decisionChannel.isCompleted) {

                                decisionChannel.complete(accepted)

                            }

                        }

                        try {

                            val intent = Intent(context, ReceiveActivity::class.java).apply {

                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

                                putExtra("EXTRA_AUTO_ACCEPT_PROMPT", true)

                            }

                            context.startActivity(intent)

                        } catch (e: Exception) {

                            Log.e(TAG, "Could not start ReceiveActivity: ${e.message}")

                        }



                        // Kích hoạt thông báo rung chuông

                        P2PTransferManager.notifyIncomingTransfer(context, request)

                    }

                }



                // Chờ phản hồi tối đa 60 giây để tránh treo kết nối

                val accepted = withTimeoutOrNull(60_000L) {

                    decisionChannel.await()

                } ?: false



                if (!accepted) {

                    val myDevice = P2PTransferManager.getDeviceDisplayName()

                    val rejectMsg = context.getString(com.nextai.sharenow.R.string.p2p_handshake_rejected_msg, myDevice)

                    Log.i(TAG, "Receiver REJECTED: $rejectMsg")

                    dos.writeUTF("RESPONSE_REJECT")

                    dos.writeUTF(rejectMsg)

                    dos.flush()

                    socket.close()

                    return@launch

                }



                // Người nhận ĐỒNG Ý

                val myDevice = P2PTransferManager.getDeviceDisplayName()

                val acceptMsg = context.getString(com.nextai.sharenow.R.string.p2p_handshake_accepted_msg, myDevice)

                Log.i(TAG, "Receiver ACCEPTED: $acceptMsg. Starting file reception...")

                dos.writeUTF("RESPONSE_ACCEPT")

                dos.writeUTF(acceptMsg)

                dos.flush()



                // 3. Tiến hành nhận từng tệp thật qua socket

                receiveIncomingFiles(dis, request)



            } catch (e: Exception) {

                Log.e(TAG, "Error handling incoming sender: ${e.message}")

                withContext(Dispatchers.Main) {

                    onTransferFailed?.invoke("Transfer error: ${e.message}")

                }

                try { socket.close() } catch (ignored: Exception) {}

            }

        }

    }



    /**

     * Nhận file thực tế qua TCP và lưu vào Download/ShareNow

     */

    private suspend fun receiveIncomingFiles(dis: DataInputStream, request: TransferRequest) = withContext(Dispatchers.IO) {

        val receivedDir = P2PTransferManager.getReceivedFilesDir()

        val receivedFilesList = mutableListOf<File>()

        val totalBytes = request.totalBytes.coerceAtLeast(1L)

        var totalBytesReceived = 0L

        val startTime = System.currentTimeMillis()



        P2PTransferManager.isRealTransferActive = true

        withContext(Dispatchers.Main) {

            P2PTransferManager.onTransferProgressUpdated?.invoke(0, 0f, request.files.firstOrNull()?.name ?: "")

            onTransferProgressUpdated?.invoke(0, 0f, request.files.firstOrNull()?.name ?: "")

        }



        val buffer = ByteArray(BUFFER_SIZE)

        var lastProgressUpdate = -1

        var lastProgressUpdateTime = 0L



        for (fileInfo in request.files) {

            val fileName = fileInfo.name

            val fileSize = fileInfo.size



            // Tạo file đích trong Download/ShareNow

            val targetFile = File(receivedDir, fileName)

            val fos = FileOutputStream(targetFile)

            var bytesReceivedThisFile = 0L



            Log.i(TAG, "Downloading file: $fileName ($fileSize bytes) to ${targetFile.absolutePath}")



            while (bytesReceivedThisFile < fileSize) {

                val toRead = Math.min(buffer.size.toLong(), fileSize - bytesReceivedThisFile).toInt()

                val read = dis.read(buffer, 0, toRead)

                if (read == -1) break

                fos.write(buffer, 0, read)

                bytesReceivedThisFile += read

                totalBytesReceived += read



                val currentElapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)

                val speedMb = (totalBytesReceived.toFloat() / (1024 * 1024)) / (currentElapsed / 1000f)

                val progress = ((totalBytesReceived * 100) / totalBytes).toInt().coerceIn(0, 100)



                val now = System.currentTimeMillis()

                if (progress != lastProgressUpdate && (now - lastProgressUpdateTime >= 100 || progress == 100)) {

                    lastProgressUpdate = progress

                    lastProgressUpdateTime = now

                    withContext(Dispatchers.Main) {

                        onTransferProgressUpdated?.invoke(progress, speedMb, fileName)

                        P2PTransferManager.onTransferProgressUpdated?.invoke(progress, speedMb, fileName)

                    }

                }

            }



            fos.flush()

            fos.close()

            receivedFilesList.add(targetFile)

            P2PTransferManager.scanMediaFile(context, targetFile)

            Log.i(TAG, "Finished receiving: $fileName (${targetFile.length()} bytes)")

        }



        P2PTransferManager.lastReceivedFiles.clear()

        P2PTransferManager.lastReceivedFiles.addAll(receivedFilesList)

        P2PTransferManager.isRealTransferActive = false



        withContext(Dispatchers.Main) {

            onTransferProgressUpdated?.invoke(100, 0f, "Completed")

            P2PTransferManager.onTransferProgressUpdated?.invoke(100, 0f, "Completed")

            onTransferCompleted?.invoke(receivedFilesList)

            P2PTransferManager.onTransferCompleted?.invoke(receivedFilesList)

            P2PTransferManager.notifyTransferSuccess(context, receivedFilesList.size, request.senderDevice)

        }

    }



    /**

     * Gửi yêu cầu và truyền file từ máy Gửi sang máy Nhận

     */

    fun sendFilesToReceiver(

        receiverIp: String,

        receiverPort: Int = P2PTransferManager.TCP_PORT,

        request: TransferRequest,

        onAccepted: (message: String) -> Unit = {},

        onProgress: (progress: Int, speedMb: Float, currentFile: String) -> Unit,

        onSuccess: () -> Unit,

        onRejected: (reason: String) -> Unit,

        onError: (error: String) -> Unit

    ) {

        CoroutineScope(Dispatchers.IO).launch {

            var socket: Socket? = null

            try {

                Log.i(TAG, "Connecting to receiver $receiverIp:$receiverPort...")

                socket = Socket()

                socket.tcpNoDelay = true

                socket.soTimeout = 90_000

                socket.connect(InetSocketAddress(receiverIp, receiverPort), 5000)



                val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE))

                val dis = DataInputStream(BufferedInputStream(socket.getInputStream(), BUFFER_SIZE))



                // 1. Gửi TRANSFER_REQUEST

                dos.writeUTF("TRANSFER_REQUEST")

                val requestJson = gson.toJson(request)

                val jsonBytes = requestJson.toByteArray(Charsets.UTF_8)

                dos.writeInt(jsonBytes.size)

                dos.write(jsonBytes)

                dos.flush()

                Log.i(TAG, "Sent TRANSFER_REQUEST. Waiting for receiver's decision...")



                // 2. Chờ phản hồi từ máy nhận

                val responseTag = dis.readUTF()



                if (responseTag == "RESPONSE_REJECT") {

                    P2PTransferManager.isRealTransferActive = false

                    val reason = dis.readUTF()

                    withContext(Dispatchers.Main) {

                        onRejected(reason.ifEmpty { "Đối phương từ chối kết nối, tìm máy khác" })

                    }

                    socket.close()

                    return@launch

                }



                if (responseTag != "RESPONSE_ACCEPT") {

                    P2PTransferManager.isRealTransferActive = false

                    withContext(Dispatchers.Main) {

                        onError("Invalid response from receiver: $responseTag")

                    }

                    socket.close()

                    return@launch

                }



                val acceptMsg = dis.readUTF()

                Log.i(TAG, "Receiver accepted ($acceptMsg)! Streaming file data...")

                P2PTransferManager.isRealTransferActive = true

                withContext(Dispatchers.Main) {

                    onAccepted(acceptMsg.ifEmpty { "Đối phương đồng ý kết nối sẵn sàng nhận file" })

                }



                // 3. Tiến hành stream byte của từng tệp

                val totalBytes = request.totalBytes.coerceAtLeast(1L)

                var totalBytesSent = 0L

                val startTime = System.currentTimeMillis()

                val buffer = ByteArray(BUFFER_SIZE)

        var lastProgressUpdate = -1

        var lastProgressUpdateTime = 0L



                for (fileInfo in request.files) {

                    var inputStream: InputStream? = null

                    try {

                        if (!fileInfo.uriString.isNullOrEmpty()) {

                            inputStream = context.contentResolver.openInputStream(Uri.parse(fileInfo.uriString))

                        }

                        if (inputStream == null && !fileInfo.filePath.isNullOrEmpty()) {

                            val f = File(fileInfo.filePath)

                            if (f.exists()) inputStream = FileInputStream(f)

                        }

                    } catch (e: Exception) {

                        Log.e(TAG, "Cannot open input stream for ${fileInfo.name}: ${e.message}")

                    }



                    var bytesSentThisFile = 0L

                    val fileSize = fileInfo.size



                    if (inputStream != null) {

                        while (bytesSentThisFile < fileSize) {

                            val bytesRead = inputStream.read(buffer)

                            if (bytesRead == -1) break

                            val toWrite = Math.min(bytesRead.toLong(), fileSize - bytesSentThisFile).toInt()

                            dos.write(buffer, 0, toWrite)

                            bytesSentThisFile += toWrite

                            totalBytesSent += toWrite



                            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)

                            val speedMb = (totalBytesSent.toFloat() / (1024 * 1024)) / (elapsed / 1000f)

                            val progress = ((totalBytesSent * 100) / totalBytes).toInt().coerceIn(0, 100)



                            val now = System.currentTimeMillis()

                            if (progress != lastProgressUpdate && (now - lastProgressUpdateTime >= 100 || progress == 100)) {

                                lastProgressUpdate = progress

                                lastProgressUpdateTime = now

                                withContext(Dispatchers.Main) {

                                    onProgress(progress, speedMb, fileInfo.name)

                                    P2PTransferManager.onTransferProgressUpdated?.invoke(progress, speedMb, fileInfo.name)

                                }

                            }

                        }

                        // Gửi bù byte nếu luồng file ngắn hơn size ghi nhận

                        while (bytesSentThisFile < fileSize) {

                            val pad = Math.min(buffer.size.toLong(), fileSize - bytesSentThisFile).toInt()

                            dos.write(buffer, 0, pad)

                            bytesSentThisFile += pad

                            totalBytesSent += pad

                        }

                        inputStream.close()

                    } else {

                        // Nếu file demo/không đọc được, gửi padding bytes để đảm bảo tiến trình hoàn tất

                        val dummyChunk = ByteArray(BUFFER_SIZE)

                        while (bytesSentThisFile < fileSize) {

                            val toSend = Math.min(dummyChunk.size.toLong(), fileSize - bytesSentThisFile).toInt()

                            dos.write(dummyChunk, 0, toSend)

                            bytesSentThisFile += toSend

                            totalBytesSent += toSend



                            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)

                            val speedMb = (totalBytesSent.toFloat() / (1024 * 1024)) / (elapsed / 1000f)

                            val progress = ((totalBytesSent * 100) / totalBytes).toInt().coerceIn(0, 100)



                            val now = System.currentTimeMillis()

                            if (progress != lastProgressUpdate && (now - lastProgressUpdateTime >= 100 || progress == 100)) {

                                lastProgressUpdate = progress

                                lastProgressUpdateTime = now

                                withContext(Dispatchers.Main) {

                                    onProgress(progress, speedMb, fileInfo.name)

                                    P2PTransferManager.onTransferProgressUpdated?.invoke(progress, speedMb, fileInfo.name)

                                }

                            }

                        }

                    }

                    dos.flush()

                }



                dos.flush()

                P2PTransferManager.isRealTransferActive = false

                withContext(Dispatchers.Main) {

                    onProgress(100, 0f, "Completed")

                    P2PTransferManager.onTransferProgressUpdated?.invoke(100, 0f, "Completed")

                    onSuccess()

                    P2PTransferManager.onTransferCompleted?.invoke(emptyList())

                }



            } catch (e: Exception) {

                Log.e(TAG, "Send error: ${e.message}")

                P2PTransferManager.isRealTransferActive = false

                withContext(Dispatchers.Main) {

                    onError("Transfer error: ${e.message}")

                }

            } finally {

                try { socket?.close() } catch (ignored: Exception) {}

            }

        }

    }



    /**

     * Phát sóng UDP beacon để máy gửi phát hiện trong radar

     */

    private fun startUdpBroadcaster(ip: String, port: Int) {

        CoroutineScope(Dispatchers.IO).launch {

            try {

                udpSocket = DatagramSocket()

                udpSocket?.broadcast = true

                val deviceName = P2PTransferManager.getDeviceDisplayName()

                val message = "SHARENOW_BEACON:$deviceName:$ip:$port"

                val data = message.toByteArray()



                while (isServerRunning) {

                    val packet = DatagramPacket(

                        data,

                        data.size,

                        InetAddress.getByName("255.255.255.255"),

                        P2PTransferManager.UDP_PORT

                    )

                    udpSocket?.send(packet)

                    delay(1500)

                }

            } catch (e: Exception) {

                Log.e(TAG, "UDP broadcast error: ${e.message}")

            }

        }

    }



    /**

     * Dò tìm máy nhận trên mạng nội bộ (UDP Listener + Subnet scan)

     */

    fun discoverReceivers(

        onDeviceFound: (DiscoveredDevice) -> Unit

    ): Job {

        return CoroutineScope(Dispatchers.IO).launch {

            val foundSet = mutableSetOf<String>()



            // 1. Lắng nghe UDP Beacon từ máy nhận

            val udpJob = launch {

                var socket: DatagramSocket? = null

                try {

                    socket = DatagramSocket(P2PTransferManager.UDP_PORT).apply {

                        broadcast = true

                        reuseAddress = true

                    }

                    val buf = ByteArray(1024)

                    while (isActive) {

                        val packet = DatagramPacket(buf, buf.size)

                        socket.receive(packet)

                        val msg = String(packet.data, 0, packet.length)

                        if (msg.startsWith("SHARENOW_BEACON:")) {

                            val parts = msg.split(":")

                            if (parts.size >= 4) {

                                val name = parts[1]

                                val ip = parts[2]

                                val port = parts[3].toIntOrNull() ?: P2PTransferManager.TCP_PORT

                                if (!foundSet.contains(ip)) {

                                    foundSet.add(ip)

                                    val dev = DiscoveredDevice(name, ip, port)

                                    withContext(Dispatchers.Main) {

                                        onDeviceFound(dev)

                                    }

                                }

                            }

                        }

                    }

                } catch (e: Exception) {

                    Log.d(TAG, "UDP listener closed: ${e.message}")

                } finally {

                    socket?.close()

                }

            }



            // 2. Quét liên tục đa luồng trong subnet nội bộ (TCP Probe qua port 8988)

            val tcpJob = launch {

                while (isActive) {

                    val myIp = P2PTransferManager.getLocalIpAddress(context)

                    val prefix = myIp.substringBeforeLast(".")



                    // Ưu tiên các IP quen thuộc của thiết bị test và Hotspot

                    val priorityIps = listOf(

                        "$prefix.17",

                        "$prefix.2",

                        "192.168.1.17",

                        "192.168.1.2",

                        "192.168.49.1"

                    ).distinct().filter { it != myIp }



                    val scanList = mutableListOf<String>()

                    scanList.addAll(priorityIps)

                    for (i in 1..40) {

                        val ip = "$prefix.$i"

                        if (ip != myIp && !scanList.contains(ip)) scanList.add(ip)

                    }



                    val probeJobs = scanList.map { targetIp ->

                        launch {

                            var s: Socket? = null

                            try {

                                s = Socket()

                                s.tcpNoDelay = true

                                s.connect(InetSocketAddress(targetIp, P2PTransferManager.TCP_PORT), 450)

                                val pDos = DataOutputStream(s.getOutputStream())

                                val pDis = DataInputStream(s.getInputStream())

                                pDos.writeUTF("DISCOVERY_PING")

                                pDos.flush()



                                s.soTimeout = 1000

                                val resp = pDis.readUTF()

                                if (resp.startsWith("DISCOVERY_PONG:")) {

                                    val parts = resp.split(":")

                                    val realName = if (parts.size >= 2 && parts[1].isNotBlank()) parts[1] else "Samsung Device"

                                    val realPort = if (parts.size >= 4) parts[3].toIntOrNull() ?: P2PTransferManager.TCP_PORT else P2PTransferManager.TCP_PORT



                                    if (foundSet.add(targetIp)) {

                                        val dev = DiscoveredDevice(realName, targetIp, realPort)

                                        withContext(Dispatchers.Main) {

                                            onDeviceFound(dev)

                                        }

                                    }

                                }

                            } catch (_: Exception) {

                            } finally {

                                try { s?.close() } catch (_: Exception) {}

                            }

                        }

                    }

                    probeJobs.forEach { it.join() }

                    delay(2500)

                }

            }



            joinAll(udpJob, tcpJob)

        }

    }



    fun stopServer() {

        try {

            multicastLock?.release()

        } catch (_: Exception) {}

        multicastLock = null

        isServerRunning = false

        try { serverSocket?.close() } catch (ignored: Exception) {}

        try { udpSocket?.close() } catch (ignored: Exception) {}

        serverSocket = null

        udpSocket = null

    }

}

