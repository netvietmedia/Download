package com.ushareit.ui.muslim

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ActivityMuslimDailyBinding
import com.ushareit.core.ads.AdMediationManager
import com.ushareit.core.muslim.PrayerTimesCalculator

class MuslimDailyActivity : com.ushareit.ui.base.BaseActivity() {

    private lateinit var binding: ActivityMuslimDailyBinding
    private var nativeAd: NativeAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMuslimDailyBinding.inflate(layoutInflater)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        nativeAd?.destroy()
    }

    private fun initAds() {
        // 1. Adaptive Banner ở đáy màn hình
        try {
            AdMediationManager.loadAdaptiveBanner(this, binding.bannerContainerMuslim)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Native Ad dưới 4 hàng đầu tiên (Fajr, Dhuhr, Asr, Maghrib)
        AdMediationManager.loadNativeAd(this, onLoaded = { ad ->
            if (!isFinishing && !isDestroyed) {
                nativeAd = ad
                binding.layoutNativeAdMuslim.visibility = View.VISIBLE
                AdMediationManager.bindNativeAdToContainer(binding.layoutNativeAdMuslim, ad)
            }
        })
    }

    private fun initViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Calculate prayer times
        val times = PrayerTimesCalculator.calculateTimes(21.0285, 105.8542)
        binding.tvFajr.text = times.fajr
        binding.tvDhuhr.text = times.dhuhr
        binding.tvAsr.text = times.asr
        binding.tvMaghrib.text = times.maghrib
        binding.tvIsha.text = times.isha

        binding.tvNextPrayer.text = "Asr • ${times.asr}"

        // Xử lý CheckBox chọn xóa tệp trong danh sách Offline Duas & Recitations
        setupSelectionAndDeletion()
    }

    private fun setupSelectionAndDeletion() {
        val checkBoxes = listOf(binding.cbMuslim1, binding.cbMuslim2, binding.cbMuslim3)
        val rows = listOf(binding.rowMuslimFile1, binding.rowMuslimFile2, binding.rowMuslimFile3)

        fun updateDeleteButton() {
            val selectedCount = checkBoxes.count { it.isChecked && it.visibility == View.VISIBLE }
            if (selectedCount > 0) {
                binding.btnDeleteMuslimSelected.visibility = View.VISIBLE
                binding.btnDeleteMuslimSelected.text = getString(R.string.delete_selected_files, selectedCount)
            } else {
                binding.btnDeleteMuslimSelected.visibility = View.GONE
            }
        }

        for (i in checkBoxes.indices) {
            val cb = checkBoxes[i]
            val row = rows[i]
            cb.setOnCheckedChangeListener { _, _ -> updateDeleteButton() }
            row.setOnClickListener {
                cb.isChecked = !cb.isChecked
            }
        }

        binding.btnDeleteMuslimSelected.setOnClickListener {
            com.ushareit.core.analytics.FirebaseAnalyticsManager.logButtonClick("btn_delete_muslim_files", "MuslimDailyActivity")
            val selectedCount = checkBoxes.count { it.isChecked && it.visibility == View.VISIBLE }
            if (selectedCount > 0) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.delete_confirm_title))
                    .setMessage(getString(R.string.delete_confirm_msg, selectedCount))
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                        for (i in checkBoxes.indices) {
                            if (checkBoxes[i].isChecked && checkBoxes[i].visibility == View.VISIBLE) {
                                rows[i].visibility = View.GONE
                                checkBoxes[i].isChecked = false
                            }
                        }
                        updateDeleteButton()
                        Toast.makeText(this, getString(R.string.delete_success_toast, selectedCount), Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }
    }
}
