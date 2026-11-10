package com.ushareit.ui.webshare

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.method.ScrollingMovementMethod
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ActivityWebShareBinding
import com.ushareit.core.filemanager.SharedFilePool
import com.ushareit.core.p2p.P2PTransferManager
import com.ushareit.core.utils.QRCodeGenerator
import com.ushareit.core.webshare.WebShareManager
import com.ushareit.core.webshare.WebShareServer
import com.ushareit.ui.transfer.ReceivedFilesActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class WebShareActivity : com.ushareit.ui.base.BaseActivity(), WebShareServer.WebShareListener {

    private lateinit var binding: ActivityWebShareBinding
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var receivedFileCount = 0
    private var downloadedFileCount = 0
    private var currentUrl = ""

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            WebShareManager.refreshFileCache()
            appendLog("🔓 Đã cấp quyền truy cập phương tiện, làm mới danh sách tệp PC.")
        }
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            handlePickedUris(uris)
        }
    }

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = mutableListOf<Uri>()
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    uris.add(data.clipData!!.getItemAt(i).uri)
                }
            } else if (data?.data != null) {
                data.data?.let { uris.add(it) }
            }
            if (uris.isNotEmpty()) {
                handlePickedUris(uris)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.root.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                notifyBackgroundAndFinish()
            }
        })

        initViews()
        checkAndRequestMediaPermissions()
        setupServerState()
        handlePickerIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePickerIntent(intent)
    }

    private fun checkAndRequestMediaPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (needed.isNotEmpty()) {
            try {
                permissionLauncher.launch(needed.toTypedArray())
            } catch (_: Throwable) {}
        }
    }

    private fun handlePickerIntent(intent: Intent?) {
        val triggerCategory = intent?.getStringExtra("EXTRA_TRIGGER_PICKER_CATEGORY")
        if (!triggerCategory.isNullOrBlank()) {
            intent.removeExtra("EXTRA_TRIGGER_PICKER_CATEGORY")
            launchPickerForCategory(triggerCategory)
        }
    }

    private fun launchPickerForCategory(category: String) {
        when (category.uppercase()) {
            "PHOTO" -> launchPhotoVideoPicker(ActivityResultContracts.PickVisualMedia.ImageOnly)
            "VIDEO" -> launchPhotoVideoPicker(ActivityResultContracts.PickVisualMedia.VideoOnly)
            "DOCUMENT", "MUSIC" -> launchDocumentPicker(category.uppercase())
            else -> launchPhotoVideoPicker(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        }
    }

    private fun launchPhotoVideoPicker(mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType) {
        try {
            photoPickerLauncher.launch(PickVisualMediaRequest(mediaType))
        } catch (_: Throwable) {
            launchDocumentPicker("PHOTO")
        }
    }

    private fun launchDocumentPicker(category: String = "DOCUMENT") {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = when (category) {
                    "MUSIC" -> "audio/*"
                    else -> "*/*"
                }
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            documentPickerLauncher.launch(intent)
        } catch (_: Throwable) {
            try {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                documentPickerLauncher.launch(fallback)
            } catch (_: Throwable) {}
        }
    }

    private fun handlePickedUris(uris: List<Uri>) {
        appendLog("⏳ Đang đồng bộ ${uris.size} tệp đã chọn lên WebShare PC...")
        SharedFilePool.addPickedUris(this, uris) { addedCount ->
            appendLog("✅ Đã thêm $addedCount tệp vào danh sách chia sẻ PC!")
            Toast.makeText(this, "Đã thêm $addedCount tệp chia sẻ lên PC", Toast.LENGTH_SHORT).show()
            vibrateFeedback()
        }
    }

    private fun initViews() {
        // Toolbar Navigation
        binding.toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.text_white))
        binding.toolbar.setNavigationOnClickListener {
            notifyBackgroundAndFinish()
        }

        // Enable scrolling on live log text view
        binding.tvLiveLog.movementMethod = ScrollingMovementMethod()

        // Copy URL button & IP Box click
        val copyUrlAction = {
            if (currentUrl.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Share Now WebShare URL", currentUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, getString(R.string.toast_copied_url, currentUrl), Toast.LENGTH_SHORT).show()

                binding.btnCopyUrl.setIconResource(R.drawable.ic_check_circle)
                binding.btnCopyUrl.postDelayed({
                    binding.btnCopyUrl.setIconResource(R.drawable.ic_copy)
                }, 1500)
            }
        }
        binding.btnCopyUrl.setOnClickListener { copyUrlAction() }
        binding.layoutIpBox.setOnClickListener { copyUrlAction() }

        // Quick Add Media & Docs buttons
        binding.btnAddMediaWebshare.setOnClickListener {
            launchPhotoVideoPicker(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        }
        binding.btnAddDocsWebshare.setOnClickListener {
            launchDocumentPicker("DOCUMENT")
        }

        // Open received files folder
        binding.btnOpenReceivedFolder.setOnClickListener {
            startActivity(Intent(this, ReceivedFilesActivity::class.java))
        }

        // Toggle Server Button
        binding.btnToggleServer.setOnClickListener {
            if (WebShareManager.isRunning) {
                stopWebServer()
            } else {
                startWebServer()
            }
        }

        // Hiển thị tên mạng Wi-Fi
        updateWifiInfo()
    }

    private fun notifyBackgroundAndFinish() {
        if (WebShareManager.isRunning) {
            Toast.makeText(this, getString(R.string.webshare_background_running_hint), Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun updateWifiInfo() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: ""
            val wifiName = if (ssid.isNotBlank() && ssid != "<unknown ssid>") {
                ssid
            } else {
                getString(R.string.webshare_hotspot_lan)
            }
            binding.tvWifiName.text = getString(R.string.webshare_wifi_status, wifiName)
        } catch (e: Exception) {
            binding.tvWifiName.text = getString(R.string.webshare_wifi_status, getString(R.string.webshare_lan))
        }
    }

    private fun setupServerState() {
        WebShareManager.registerListener(this)
        if (WebShareManager.isRunning) {
            WebShareManager.refreshFileCache()
            val ip = P2PTransferManager.getLocalIpAddress(this)
            updateUiServerRunning("http://$ip:${WebShareManager.PORT}")
            appendLog("🟢 " + getString(R.string.webshare_log_ready, WebShareManager.PORT))
        } else {
            startWebServer()
        }
    }

    private fun startWebServer() {
        currentUrl = WebShareManager.startServer(this)
        updateUiServerRunning(currentUrl)
        appendLog("🚀 " + getString(R.string.webshare_log_started, currentUrl))
    }

    private fun updateUiServerRunning(url: String) {
        currentUrl = url
        binding.tvWebUrl.text = currentUrl
        binding.tvServerStatusBadge.text = getString(R.string.webshare_status_online)
        binding.tvServerStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.green_success))
        binding.btnToggleServer.text = getString(R.string.webshare_btn_stop)
        binding.btnToggleServer.setTextColor(ContextCompat.getColor(this, R.color.red_danger))
        binding.btnToggleServer.strokeColor = ContextCompat.getColorStateList(this, R.color.red_danger)

        val qrBitmap = QRCodeGenerator.generateQRCode(currentUrl, 512)
        if (qrBitmap != null) {
            binding.ivQrCode.setImageBitmap(qrBitmap)
        }
    }

    private fun stopWebServer() {
        WebShareManager.stopServer(this)

        binding.tvServerStatusBadge.text = getString(R.string.webshare_status_offline)
        binding.tvServerStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.red_danger))
        binding.btnToggleServer.text = getString(R.string.webshare_btn_start)
        binding.btnToggleServer.setTextColor(ContextCompat.getColor(this, R.color.primary))
        binding.btnToggleServer.strokeColor = ContextCompat.getColorStateList(this, R.color.primary)

        appendLog("🛑 " + getString(R.string.webshare_log_stopped))
    }

    private fun appendLog(message: String) {
        val time = timeFormat.format(Date())
        val logEntry = "[$time] $message\n"
        binding.tvLiveLog.append(logEntry)
    }

    // --- WebShareListener Callbacks ---

    override fun onServerStarted(ip: String, port: Int) {
        runOnUiThread {
            updateUiServerRunning("http://$ip:$port")
            appendLog("🟢 " + getString(R.string.webshare_log_ready, port))
        }
    }

    override fun onServerStopped() {
        runOnUiThread {
            appendLog("⚪ " + getString(R.string.webshare_log_closed))
        }
    }

    override fun onClientConnected(clientIp: String) {
        runOnUiThread {
            appendLog("💻 " + getString(R.string.webshare_log_connected, clientIp))
        }
    }

    override fun onFileDownloaded(fileName: String, bytes: Long) {
        runOnUiThread {
            downloadedFileCount++
            binding.tvStatDownloadCount.text = getString(R.string.webshare_stat_files, downloadedFileCount)
            val mb = bytes / (1024.0 * 1024.0)
            val sizeStr = if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb) else "${bytes / 1024} KB"
            appendLog("📤 " + getString(R.string.webshare_log_pc_downloaded, fileName, sizeStr))
        }
    }

    override fun onFileUploaded(file: File) {
        runOnUiThread {
            receivedFileCount++
            binding.tvStatReceivedCount.text = getString(R.string.webshare_stat_files, receivedFileCount)
            val mb = file.length() / (1024.0 * 1024.0)
            val sizeStr = if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb) else "${file.length() / 1024} KB"
            appendLog("📥 " + getString(R.string.webshare_log_pc_received, file.name, sizeStr))
            Toast.makeText(this, getString(R.string.toast_webshare_received_file, file.name), Toast.LENGTH_SHORT).show()
            vibrateFeedback()
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            appendLog("⚠️ " + getString(R.string.webshare_log_error, error))
        }
    }

    override fun onPcRequestFilePicker(category: String) {
        runOnUiThread {
            vibrateFeedback()
            appendLog("📲 PC yêu cầu chọn tệp danh mục $category trên điện thoại...")
            launchPickerForCategory(category)
        }
    }

    private fun vibrateFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        WebShareManager.unregisterListener(this)
    }
}
