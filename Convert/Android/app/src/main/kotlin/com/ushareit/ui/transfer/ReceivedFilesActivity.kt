package com.ushareit.ui.transfer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ActivityReceivedFilesBinding
import com.nextai.sharenow.databinding.ItemReceivedFileBinding
import com.ushareit.core.ads.AdMediationManager
import com.ushareit.core.downloader.MonetizedDomainRegistry
import com.ushareit.core.p2p.P2PTransferManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ReceivedFilesActivity : com.ushareit.ui.base.BaseActivity() {

    private lateinit var binding: ActivityReceivedFilesBinding
    private val filesList = mutableListOf<File>()
    private val selectedFiles = mutableSetOf<File>()
    private var nativeAd: NativeAd? = null
    private lateinit var adapter: ReceivedFileAdapter

    companion object {
        private const val VIEW_TYPE_FILE = 0
        private const val VIEW_TYPE_NATIVE_AD = 1
        private const val NATIVE_AD_AFTER_COUNT = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceivedFilesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.root.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        initViews()
        initAds()
        loadReceivedFiles()
    }

    override fun onResume() {
        super.onResume()
        loadReceivedFiles()
    }

    override fun onDestroy() {
        super.onDestroy()
        nativeAd?.destroy()
    }

    private fun initAds() {
        try {
            AdMediationManager.loadAdaptiveBanner(this, binding.bannerContainerReceived)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        AdMediationManager.loadNativeAd(this, onLoaded = { ad ->
            if (!isFinishing && !isDestroyed) {
                nativeAd = ad
                adapter.notifyDataSetChanged()
            }
        })
    }

    private fun initViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        adapter = ReceivedFileAdapter()
        binding.rvReceivedFiles.layoutManager = LinearLayoutManager(this)
        binding.rvReceivedFiles.adapter = adapter

        binding.btnToggleSelectAll.setOnClickListener {
            com.ushareit.core.analytics.FirebaseAnalyticsManager.logButtonClick("btn_toggle_select_all", "ReceivedFilesActivity")
            if (selectedFiles.size == filesList.size) {
                selectedFiles.clear()
            } else {
                selectedFiles.clear()
                selectedFiles.addAll(filesList)
            }
            updateSelectionUI()
            adapter.notifyDataSetChanged()
        }

        binding.btnDeleteSelected.setOnClickListener {
            com.ushareit.core.analytics.FirebaseAnalyticsManager.logButtonClick("btn_delete_received_files", "ReceivedFilesActivity")
            if (selectedFiles.isNotEmpty()) {
                confirmDeleteSelectedFiles()
            }
        }
    }

    private fun updateSelectionUI() {
        if (selectedFiles.isEmpty()) {
            binding.layoutSelectionBar.visibility = View.GONE
        } else {
            binding.layoutSelectionBar.visibility = View.VISIBLE
            binding.btnDeleteSelected.text = getString(R.string.delete_selected_files, selectedFiles.size)
            binding.btnToggleSelectAll.text = if (selectedFiles.size == filesList.size) {
                getString(R.string.deselect_all)
            } else {
                getString(R.string.select_all)
            }
        }
    }

    private fun confirmDeleteSelectedFiles() {
        val count = selectedFiles.size
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_confirm_title))
            .setMessage(getString(R.string.delete_confirm_msg, count))
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                deleteSelectedFiles()
            }
            .show()
    }

    private fun deleteSelectedFiles() {
        var deletedCount = 0
        val toDelete = selectedFiles.toList()

        for (file in toDelete) {
            try {
                if (P2PTransferManager.deleteReceivedFileAndSync(this, file)) {
                    deletedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        selectedFiles.clear()
        updateSelectionUI()
        loadReceivedFiles()

        val msg = getString(R.string.delete_success_toast, deletedCount)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun loadReceivedFiles() {
        filesList.clear()
        filesList.addAll(P2PTransferManager.getAllReceivedFiles())

        binding.tvCountBadge.text = getString(R.string.received_files_count_format, filesList.size)

        if (filesList.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvReceivedFiles.visibility = View.GONE
            binding.layoutSelectionBar.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvReceivedFiles.visibility = View.VISIBLE
            selectedFiles.retainAll(filesList.toSet())
            updateSelectionUI()
            adapter.notifyDataSetChanged()
        }
    }

    private fun openFile(file: File) {
        if (!MonetizedDomainRegistry.canOpenAndConsumeMonetizedVideo(file.name)) {
            MonetizedDomainRegistry.launchPaywallForMonetizedLimit(this)
            return
        }
        val opened = P2PTransferManager.openFile(this, file)
        if (!opened) {
            Toast.makeText(this, getString(R.string.progress_no_app_to_open), Toast.LENGTH_SHORT).show()
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

    inner class ReceivedFileAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        inner class FileViewHolder(val b: ItemReceivedFileBinding) : RecyclerView.ViewHolder(b.root)
        inner class NativeAdViewHolder(val container: ViewGroup) : RecyclerView.ViewHolder(container)

        private fun hasAd(): Boolean = nativeAd != null && filesList.isNotEmpty()

        private fun getAdPosition(): Int {
            if (!hasAd()) return -1
            return if (filesList.size >= NATIVE_AD_AFTER_COUNT) {
                NATIVE_AD_AFTER_COUNT
            } else {
                filesList.size
            }
        }

        override fun getItemCount(): Int {
            return filesList.size + (if (hasAd()) 1 else 0)
        }

        override fun getItemViewType(position: Int): Int {
            return if (hasAd() && position == getAdPosition()) {
                VIEW_TYPE_NATIVE_AD
            } else {
                VIEW_TYPE_FILE
            }
        }

        private fun getFileForPosition(position: Int): File {
            val adPos = getAdPosition()
            return if (adPos in 0..position) {
                filesList[position - 1]
            } else {
                filesList[position]
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_NATIVE_AD) {
                val inflater = LayoutInflater.from(parent.context)
                val adCardView = inflater.inflate(R.layout.layout_native_ad_card, parent, false)
                NativeAdViewHolder(adCardView as ViewGroup)
            } else {
                val b = ItemReceivedFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                FileViewHolder(b)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == VIEW_TYPE_NATIVE_AD) {
                val adHolder = holder as NativeAdViewHolder
                nativeAd?.let { ad ->
                    AdMediationManager.populateNativeAdView(ad, adHolder.container as NativeAdView)
                }
                return
            }

            val fileHolder = holder as FileViewHolder
            val file = getFileForPosition(position)
            val isSelected = selectedFiles.contains(file)

            fileHolder.b.cbSelectFile.isChecked = isSelected
            fileHolder.b.tvFileName.text = file.name

            val sizeStr = formatBytes(if (file.length() > 0) file.length() else 4_800_000L)
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
            fileHolder.b.tvFileInfo.text = "$sizeStr • $dateStr"

            val ext = file.extension.lowercase()
            val defaultIcon = when {
                ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> R.drawable.ic_image_photo
                ext in listOf("mp4", "mkv", "avi", "mov") -> R.drawable.ic_video_player
                ext in listOf("mp3", "wav", "aac") -> R.drawable.ic_music_note
                ext == "apk" -> R.drawable.ic_app_box
                else -> R.drawable.ic_file_doc
            }

            if (ext in listOf("jpg", "jpeg", "png", "webp", "mp4", "mkv")) {
                Glide.with(fileHolder.itemView.context)
                    .load(file)
                    .centerCrop()
                    .placeholder(defaultIcon)
                    .error(defaultIcon)
                    .into(fileHolder.b.ivFileThumb)
            } else {
                fileHolder.b.ivFileThumb.setImageResource(defaultIcon)
            }

            val toggleSelection = {
                if (selectedFiles.contains(file)) {
                    selectedFiles.remove(file)
                } else {
                    selectedFiles.add(file)
                }
                fileHolder.b.cbSelectFile.isChecked = selectedFiles.contains(file)
                updateSelectionUI()
            }

            fileHolder.b.cardReceivedFile.setOnClickListener {
                toggleSelection()
            }

            fileHolder.b.cbSelectFile.setOnClickListener {
                toggleSelection()
            }

            fileHolder.b.btnOpenFile.setOnClickListener {
                openFile(file)
            }
        }
    }
}
