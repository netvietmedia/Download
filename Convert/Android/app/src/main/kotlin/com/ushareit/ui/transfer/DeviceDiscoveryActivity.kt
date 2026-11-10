package com.ushareit.ui.transfer

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ActivityDeviceDiscoveryBinding
import com.nextai.sharenow.databinding.ItemDiscoveredDeviceBinding
import com.ushareit.core.ads.AdMediationManager
import com.ushareit.core.p2p.*
import com.ushareit.core.utils.ButtonLightSweep
import kotlinx.coroutines.Job

class DeviceDiscoveryActivity : com.ushareit.ui.base.BaseActivity() {
    private var bannerAdView: com.google.android.gms.ads.AdView? = null

    private lateinit var binding: ActivityDeviceDiscoveryBinding
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var transferEngine: P2PTransferEngine
    private var discoveryJob: Job? = null

    private val devicesList = mutableListOf<DiscoveredDevice>()
    private lateinit var adapter: DeviceAdapter

    private var beamAnimator: ObjectAnimator? = null
    private var waveOuterAnimator: ObjectAnimator? = null
    private var waveMidAnimator: ObjectAnimator? = null

    private var fileNames = arrayListOf<String>()
    private var totalBytes = 0L
    private var fileInfos = arrayListOf<TransferFileInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDiscoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.root.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        transferEngine = P2PTransferManager.getEngine(this)
        getIntentData()
        initViews()
        try { bannerAdView = AdMediationManager.loadAdaptiveBanner(this, binding.bannerContainerDiscovery) } catch (e: Exception) { e.printStackTrace() }
        startRadarAnimations()
        startDiscovery()
    }

    private fun getIntentData() {
        fileNames = intent.getStringArrayListExtra("SELECTED_FILE_NAMES") ?: arrayListOf()
        totalBytes = intent.getLongExtra("TOTAL_BYTES", 0L)
        val serializableInfos = intent.getSerializableExtra("SELECTED_FILE_INFOS") as? ArrayList<TransferFileInfo>
        if (serializableInfos != null) {
            fileInfos.addAll(serializableInfos)
        } else {
            val perFileSize = totalBytes / fileNames.size.coerceAtLeast(1)
            fileNames.forEach { name ->
                fileInfos.add(TransferFileInfo(name = name, size = perFileSize))
            }
        }

        binding.tvSelectedSummaryBadge.text = getString(R.string.selected_summary_format, fileNames.size, formatBytes(totalBytes))
    }

    private fun initViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        adapter = DeviceAdapter(devicesList) { selectedDevice ->
            initiateTransferToDevice(selectedDevice)
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = adapter

        binding.btnManualConnect.setOnClickListener {
            devicesList.clear()
            adapter.notifyDataSetChanged()
            startDiscovery()
            Toast.makeText(this, getString(R.string.discovery_rescanning_toast), Toast.LENGTH_SHORT).show()
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
    }

    private fun startDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = transferEngine.discoverReceivers { device ->
            if (devicesList.none { it.ip == device.ip }) {
                devicesList.add(device)
                adapter.notifyItemInserted(devicesList.size - 1)
                binding.tvStatusHeadline.text = "✓ " + getString(R.string.status_found_receivers, devicesList.size)
            }
        }

// Quét và hiển thị thiết bị phát hiện thực tế trên mạng P2P
    }

    private fun initiateTransferToDevice(device: DiscoveredDevice) {
        binding.layoutRequestStatus.visibility = View.VISIBLE
        binding.tvRequestStatusMsg.text = getString(R.string.discovery_waiting_acceptance, device.name)

        val request = TransferRequest(
            senderDevice = P2PTransferManager.getDeviceDisplayName(),
            senderIp = P2PTransferManager.getLocalIpAddress(this),
            files = fileInfos,
            totalBytes = totalBytes
        )

        // Lưu trữ request toàn cục
        P2PTransferManager.currentTransferRequest = request

        transferEngine.sendFilesToReceiver(
            receiverIp = device.ip,
            receiverPort = device.port,
            request = request,
            onAccepted = { acceptMsg ->
                val displayMsg = if (acceptMsg.isNotBlank()) acceptMsg else getString(R.string.p2p_handshake_accepted_msg, device.name)
                Toast.makeText(this, displayMsg, Toast.LENGTH_LONG).show()
                val intent = Intent(this, TransferProgressActivity::class.java).apply {
                    putStringArrayListExtra("SELECTED_FILE_NAMES", fileNames)
                    putExtra("TOTAL_BYTES", totalBytes)
                    putExtra("IS_RECEIVER", false)
                    putExtra("TARGET_DEVICE_NAME", device.name)
                }
                startActivity(intent)
                finish()
            },
            onProgress = { progress, speedMb, currentFile ->
                binding.tvRequestStatusMsg.text = getString(R.string.status_sending_progress, currentFile, progress)
            },
            onSuccess = {
                if (!isFinishing && !isDestroyed) {
                    val intent = Intent(this, TransferProgressActivity::class.java).apply {
                        putStringArrayListExtra("SELECTED_FILE_NAMES", fileNames)
                        putExtra("TOTAL_BYTES", totalBytes)
                        putExtra("IS_RECEIVER", false)
                        putExtra("TARGET_DEVICE_NAME", device.name)
                    }
                    startActivity(intent)
                    finish()
                }
            },
            onRejected = { reason ->
                binding.layoutRequestStatus.visibility = View.GONE
                val rejectMsg = if (reason.isNotBlank()) reason else getString(R.string.p2p_handshake_rejected_msg, device.name)
                Toast.makeText(this, rejectMsg, Toast.LENGTH_LONG).show()
                showRejectDialog(device, rejectMsg)
            },
            onError = { error ->
                binding.layoutRequestStatus.visibility = View.GONE
                showRetryConnectionDialog(device, error)
            }
        )
    }

    private fun showRetryConnectionDialog(device: DiscoveredDevice, error: String) {
        if (isFinishing || isDestroyed) return

        val dialogBinding = com.nextai.sharenow.databinding.DialogConnectionRetryBinding.inflate(layoutInflater)
        dialogBinding.tvRetryDeviceName.text = device.name
        dialogBinding.tvRetryDeviceIp.text = "${device.ip}:${device.port}"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        ButtonLightSweep.attachTo(dialogBinding.btnRetryConnect)

        // 1. Thử lại kết nối
        dialogBinding.btnRetryConnect.setOnClickListener {
            dialog.dismiss()
            initiateTransferToDevice(device)
        }

        // Bỏ mô phỏng, chỉ cho phép thử lại kết nối thật
        dialogBinding.btnRetrySimulate.visibility = View.GONE

        // 3. Hủy bỏ
        dialogBinding.btnRetryCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showRejectDialog(device: DiscoveredDevice, reason: String) {
        if (isFinishing || isDestroyed) return

        val dialog = AlertDialog.Builder(this, R.style.Theme_ShareNow_Dialog)
            .setTitle(R.string.receive_rejected_toast)
            .setMessage("${device.name}\n$reason")
            .setPositiveButton(R.string.btn_understand, null)
            .create()
        dialog.show()
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
        discoveryJob?.cancel()
        beamAnimator?.cancel()
        waveOuterAnimator?.cancel()
        waveMidAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    inner class DeviceAdapter(
        private val list: List<DiscoveredDevice>,
        private val onItemClick: (DiscoveredDevice) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        inner class ViewHolder(val b: ItemDiscoveredDeviceBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDiscoveredDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.b.tvDeviceName.text = item.name
            holder.b.tvDeviceIp.text = holder.itemView.context.getString(R.string.device_ready_receive_format, item.ip)

            holder.b.cardDevice.setOnClickListener {
                onItemClick(item)
            }

            holder.b.btnSendToDevice.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
