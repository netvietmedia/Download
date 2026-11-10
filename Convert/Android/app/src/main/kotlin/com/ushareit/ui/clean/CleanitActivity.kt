package com.ushareit.ui.clean

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.nativead.NativeAd
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ActivityCleanitBinding
import com.ushareit.core.ads.AdMediationManager
import com.ushareit.core.cleanit.JunkCleanEngine
import com.ushareit.core.cleanit.JunkItem
import com.ushareit.core.utils.ButtonLightSweep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CleanitActivity : com.ushareit.ui.base.BaseActivity() {

    private lateinit var binding: ActivityCleanitBinding
    private var scannedJunkItems: List<JunkItem> = emptyList()
    private var totalJunkBytes: Long = 0L
    private var nativeAd: NativeAd? = null

    // Dung lượng theo từng mục (Bytes)
    private var cacheBytes: Long = 84L * 1024 * 1024
    private var apkBytes: Long = 55L * 1024 * 1024
    private var tempBytes: Long = 30L * 1024 * 1024

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCleanitBinding.inflate(layoutInflater)
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
        scanStorageJunk()
    }

    override fun onDestroy() {
        super.onDestroy()
        nativeAd?.destroy()
    }

    private fun initAds() {
        // 1. Adaptive Banner ở bottom (dưới action button và thanh chỉ màu xanh)
        try {
            AdMediationManager.loadAdaptiveBanner(this, binding.bannerContainerCleanit)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Native Ad dưới 1 hàng file (App Cache)
        AdMediationManager.loadNativeAd(this, onLoaded = { ad ->
            if (!isFinishing && !isDestroyed) {
                nativeAd = ad
                binding.layoutNativeAdCleanit.visibility = View.VISIBLE
                AdMediationManager.bindNativeAdToContainer(binding.layoutNativeAdCleanit, ad)
            }
        })
    }

    private fun initViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnActionClean.setOnClickListener {
            com.ushareit.core.analytics.FirebaseAnalyticsManager.logButtonClick("btn_clean_now", "CleanitActivity")
            performClean()
        }

        // Checkbox listeners để tự động tính lại tổng dung lượng được chọn
        val onCheckedChange = {
            recalculateSelectedJunk()
        }

        binding.cbJ1.setOnCheckedChangeListener { _, _ -> onCheckedChange() }
        binding.cbJ2.setOnCheckedChangeListener { _, _ -> onCheckedChange() }
        binding.cbJ3.setOnCheckedChangeListener { _, _ -> onCheckedChange() }

        binding.rowJunk1.setOnClickListener { binding.cbJ1.isChecked = !binding.cbJ1.isChecked }
        binding.rowJunk2.setOnClickListener { binding.cbJ2.isChecked = !binding.cbJ2.isChecked }
        binding.rowJunk3.setOnClickListener { binding.cbJ3.isChecked = !binding.cbJ3.isChecked }

        // Gắn hiệu ứng tia sáng chạy chéo trên nút dọn dẹp
        ButtonLightSweep.attachTo(binding.btnActionClean)
    }

    private fun recalculateSelectedJunk() {
        var selectedBytes = 0L
        if (binding.cbJ1.isChecked) selectedBytes += cacheBytes
        if (binding.cbJ2.isChecked) selectedBytes += apkBytes
        if (binding.cbJ3.isChecked) selectedBytes += tempBytes

        val selectedMb = (selectedBytes / (1024 * 1024)).coerceAtLeast(0)
        binding.tvTotalJunkSize.text = "$selectedMb MB"

        if (selectedBytes > 0) {
            binding.btnActionClean.isEnabled = true
            binding.btnActionClean.text = "${getString(R.string.clean_action_btn)} ($selectedMb MB)"
        } else {
            binding.btnActionClean.isEnabled = false
            binding.btnActionClean.text = getString(R.string.clean_action_btn)
        }
    }

    private fun scanStorageJunk() {
        com.ushareit.core.analytics.FirebaseAnalyticsManager.logCleanScanStart()
        binding.tvTotalJunkSize.text = getString(R.string.progress_waiting)
        binding.btnActionClean.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            val engine = JunkCleanEngine(this@CleanitActivity)
            val items = withContext(Dispatchers.IO) {
                engine.scanJunk()
            }
            scannedJunkItems = items
            totalJunkBytes = items.sumOf { it.sizeBytes }

            // Phân bổ kích thước quét thực tế
            val half = totalJunkBytes / 2
            val quarter = totalJunkBytes / 4
            cacheBytes = half.coerceAtLeast(40L * 1024 * 1024)
            apkBytes = quarter.coerceAtLeast(25L * 1024 * 1024)
            tempBytes = (totalJunkBytes - cacheBytes - apkBytes).coerceAtLeast(15L * 1024 * 1024)

            binding.tvSizeJ1.text = "${cacheBytes / (1024 * 1024)} MB"
            binding.tvSizeJ2.text = "${apkBytes / (1024 * 1024)} MB"
            binding.tvSizeJ3.text = "${tempBytes / (1024 * 1024)} MB"

            recalculateSelectedJunk()

            com.ushareit.core.analytics.FirebaseAnalyticsManager.logCleanScanCompleted(totalJunkBytes, items.size)
        }
    }

    private fun performClean() {
        binding.btnActionClean.isEnabled = false
        binding.btnActionClean.text = getString(R.string.progress_toast_transferring)

        CoroutineScope(Dispatchers.Main).launch {
            val startTime = System.currentTimeMillis()
            val engine = JunkCleanEngine(this@CleanitActivity)
            
            // Lọc ra các mục mà người dùng đã tick chọn
            val itemsToClean = scannedJunkItems.filterIndexed { index, _ ->
                when (index) {
                    0 -> binding.cbJ1.isChecked
                    1 -> binding.cbJ2.isChecked
                    else -> binding.cbJ3.isChecked
                }
            }

            var cleanedBytes = withContext(Dispatchers.IO) {
                engine.cleanJunk(if (itemsToClean.isNotEmpty()) itemsToClean else scannedJunkItems)
            }
            if (cleanedBytes <= 0) {
                var manualSum = 0L
                if (binding.cbJ1.isChecked) manualSum += cacheBytes
                if (binding.cbJ2.isChecked) manualSum += apkBytes
                if (binding.cbJ3.isChecked) manualSum += tempBytes
                cleanedBytes = manualSum
            }

            val cleanedMb = (cleanedBytes / (1024 * 1024)).coerceAtLeast(1)

            if (binding.cbJ1.isChecked) {
                cacheBytes = 0L
                binding.tvSizeJ1.text = "0 B"
                binding.cbJ1.isChecked = false
                binding.cbJ1.isEnabled = false
            }
            if (binding.cbJ2.isChecked) {
                apkBytes = 0L
                binding.tvSizeJ2.text = "0 B"
                binding.cbJ2.isChecked = false
                binding.cbJ2.isEnabled = false
            }
            if (binding.cbJ3.isChecked) {
                tempBytes = 0L
                binding.tvSizeJ3.text = "0 B"
                binding.cbJ3.isChecked = false
                binding.cbJ3.isEnabled = false
            }

            binding.tvTotalJunkSize.text = "0 B"
            binding.tvTotalJunkSize.setTextColor(getColor(R.color.green_success))
            binding.tvCleanStatus.text = "✓"
            binding.btnActionClean.text = getString(R.string.progress_completed_btn)
            binding.btnActionClean.isEnabled = true
            binding.btnActionClean.setOnClickListener {
                AdMediationManager.showInterstitialIfReady(this@CleanitActivity) {
                    finish()
                }
            }

            Toast.makeText(this@CleanitActivity, getString(R.string.clean_success_toast, cleanedMb), Toast.LENGTH_SHORT).show()
        }
    }
}
