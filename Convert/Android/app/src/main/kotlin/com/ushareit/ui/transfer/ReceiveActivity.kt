package com.ushareit.ui.transfer

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ActivityReceiveBinding
import com.nextai.sharenow.databinding.DialogIncomingTransferBinding
import com.nextai.sharenow.databinding.ItemIncomingFileBinding
import com.ushareit.core.ads.AdMediationManager
import com.ushareit.core.p2p.*
import com.ushareit.core.utils.ButtonLightSweep
import com.ushareit.core.utils.QRCodeGenerator

class ReceiveActivity : com.ushareit.ui.base.BaseActivity() {
    private var bannerAdView: com.google.android.gms.ads.AdView? = null

    private lateinit var binding: ActivityReceiveBinding
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var transferEngine: P2PTransferEngine

    private var beamAnimator: ObjectAnimator? = null
    private var waveOuterAnimator: ObjectAnimator? = null
    private var waveMidAnimator: ObjectAnimator? = null
    private var centerPulseAnimator: ObjectAnimator? = null
    private var currentDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.root.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        com.ushareit.core.analytics.FirebaseAnalyticsManager.logTransferReceiveClick("receive_activity")
        transferEngine = P2PTransferManager.getEngine(this)
        initViews()
        AdMediationManager.loadInterstitial(this)
        try { bannerAdView = AdMediationManager.loadAdaptiveBanner(this, binding.bannerContainerReceive) } catch (e: Exception) { e.printStackTrace() }
        startRadarAnimations()
        startReceiverServer()
        checkPendingIncomingTransfer()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkPendingIncomingTransfer()
    }

    private fun checkPendingIncomingTransfer() {
        val pending = P2PTransferManager.pendingIncomingTransfer
        if (pending != null) {
            showIncomingTransferDialog(pending.request, pending.onDecision)
        }
    }

    private fun initViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        val deviceName = P2PTransferManager.getDeviceDisplayName()
        binding.tvDeviceName.text = deviceName
        binding.tvRadarStatus.text = getString(R.string.receive_scanning_status)

        binding.btnSimulateReceive.visibility = View.GONE
    }

    private fun startReceiverServer() {
        transferEngine.startReceiverServer { ip, port ->
            val deviceName = P2PTransferManager.getDeviceDisplayName()
            binding.tvHotspotInfo.text = "IP: $ip:$port • $deviceName"

            // Sinh mã QR thật để máy gửi quét kết nối
            val qrContent = "sharenow://connect?ip=$ip&port=$port&device=${deviceName.replace(" ", "+")}"
            val qrBitmap = QRCodeGenerator.generateQRCode(qrContent, 512)
            if (qrBitmap != null) {
                binding.ivQrCode.setImageBitmap(qrBitmap)
            }
        }

        // Lắng nghe yêu cầu nhận tệp từ máy gửi qua socket
        transferEngine.onIncomingRequestReceived = { request, onDecision ->
            showIncomingTransferDialog(request, onDecision)
        }
    }

    /**
     * Hiển thị Hộp thoại Yêu Cầu Nhận Tệp (Có thông báo nguồn gửi, danh sách file, Đồng ý nhận / Từ chối)
     */
    fun showIncomingTransferDialog(
        request: TransferRequest,
        onDecision: (Boolean) -> Unit
    ) {
        if (isFinishing || isDestroyed) return

        currentDialog?.dismiss()

        val dialogBinding = DialogIncomingTransferBinding.inflate(layoutInflater)
        dialogBinding.tvSenderTitle.text = request.senderDevice
        dialogBinding.tvTransferSummary.text = getString(R.string.p2p_dialog_incoming_confirm, request.senderDevice, request.files.size, formatBytes(request.totalBytes))

        // Danh sách các tệp đính kèm
        val adapter = IncomingFileAdapter(request.files)
        dialogBinding.rvIncomingFiles.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvIncomingFiles.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        currentDialog = dialog

        // 1. Nút Từ Chối (Hủy nhận)
        dialogBinding.btnRejectTransfer.setOnClickListener {
            P2PTransferManager.pendingIncomingTransfer = null
            dialog.dismiss()
            onDecision(false)
            Toast.makeText(this, getString(R.string.p2p_handshake_rejected_msg, request.senderDevice), Toast.LENGTH_SHORT).show()
        }

        // 2. Nút Đồng Ý Nhận
        dialogBinding.btnAcceptTransfer.setOnClickListener {
            P2PTransferManager.pendingIncomingTransfer = null
            dialog.dismiss()
            onDecision(true)

            // Chuyển sang màn hình tiến trình nhận tệp thực tế
            val intent = Intent(this, TransferProgressActivity::class.java).apply {
                putStringArrayListExtra("SELECTED_FILE_NAMES", ArrayList(request.files.map { it.name }))
                putExtra("TOTAL_BYTES", request.totalBytes)
                putExtra("IS_RECEIVER", true)
                putExtra("SENDER_DEVICE_NAME", request.senderDevice)
            }
            startActivity(intent)
            AdMediationManager.showInterstitialIfReady(this) {}
        }

        ButtonLightSweep.attachTo(dialogBinding.btnAcceptTransfer)
        dialog.show()
    }

    private fun simulateIncomingTransferRequest() {
        val partnerName = if (P2PTransferManager.getLocalIpAddress(this).endsWith(".2")) "Samsung Galaxy A07" else "Samsung Galaxy A50s"
        val partnerIp = if (partnerName.contains("A07")) "192.168.1.18" else "192.168.1.2"

        val sampleFiles = listOf(
            TransferFileInfo("Video_Clip_KyNiem.mp4", 38_500_000, "video/mp4"),
            TransferFileInfo("HinhAnh_ChupThucTe.jpg", 3_800_000, "image/jpeg"),
            TransferFileInfo("TaiLieu_HuongDan.pdf", 1_400_000, "application/pdf")
        )
        val total = sampleFiles.sumOf { it.size }

        val request = TransferRequest(
            senderDevice = partnerName,
            senderIp = partnerIp,
            files = sampleFiles,
            totalBytes = total
        )

        showIncomingTransferDialog(request) { accepted ->
            if (accepted) {
                val intent = Intent(this, TransferProgressActivity::class.java).apply {
                    putStringArrayListExtra("SELECTED_FILE_NAMES", ArrayList(sampleFiles.map { it.name }))
                    putExtra("TOTAL_BYTES", total)
                    putExtra("IS_RECEIVER", true)
                    putExtra("SENDER_DEVICE_NAME", partnerName)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, getString(R.string.receive_cancelled_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRadarAnimations() {
        beamAnimator = ObjectAnimator.ofFloat(binding.radarScanBeam, "rotation", 0f, 360f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        val scaleOuterX = PropertyValuesHolder.ofFloat("scaleX", 0.85f, 1.25f)
        val scaleOuterY = PropertyValuesHolder.ofFloat("scaleY", 0.85f, 1.25f)
        val alphaOuter = PropertyValuesHolder.ofFloat("alpha", 0.8f, 0.1f)
        waveOuterAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.radarWaveOuter, scaleOuterX, scaleOuterY, alphaOuter).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        val scaleMidX = PropertyValuesHolder.ofFloat("scaleX", 0.8f, 1.2f)
        val scaleMidY = PropertyValuesHolder.ofFloat("scaleY", 0.8f, 1.2f)
        val alphaMid = PropertyValuesHolder.ofFloat("alpha", 0.9f, 0.2f)
        waveMidAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.radarWaveMid, scaleMidX, scaleMidY, alphaMid).apply {
            duration = 1800
            startDelay = 400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        val centerScaleX = PropertyValuesHolder.ofFloat("scaleX", 0.95f, 1.08f)
        val centerScaleY = PropertyValuesHolder.ofFloat("scaleY", 0.95f, 1.08f)
        centerPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.radarCenter, centerScaleX, centerScaleY).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }


    override fun onResume() {
        super.onResume()
        try { bannerAdView?.resume() } catch (_: Exception) {}
    }

    override fun onPause() {
        try { bannerAdView?.pause() } catch (_: Exception) {}
        super.onPause()
    }

    override fun onDestroy() {
        try { bannerAdView?.destroy() } catch (_: Exception) {}
        super.onDestroy()
        currentDialog?.dismiss()
        transferEngine.onIncomingRequestReceived = null
        beamAnimator?.cancel()
        waveOuterAnimator?.cancel()
        waveMidAnimator?.cancel()
        centerPulseAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    inner class IncomingFileAdapter(
        private val list: List<TransferFileInfo>
    ) : RecyclerView.Adapter<IncomingFileAdapter.ViewHolder>() {

        inner class ViewHolder(val b: ItemIncomingFileBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemIncomingFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.b.tvFileName.text = item.name
            holder.b.tvFileSize.text = formatBytes(item.size)

            val lower = item.name.lowercase()
            val iconRes = when {
                lower.endsWith(".apk") -> R.drawable.ic_app_box
                lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") -> R.drawable.ic_video_player
                lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac") -> R.drawable.ic_music_note
                lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp") -> R.drawable.ic_image_photo
                else -> R.drawable.ic_file_doc
            }
            holder.b.ivFileIcon.setImageResource(iconRes)
        }

        override fun getItemCount(): Int = list.size
    }
}
