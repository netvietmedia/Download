package com.ushareit.ui.main

import com.ushareit.core.analytics.FirebaseAnalyticsManager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.ads.AdRequest
import com.ushareit.core.ads.AdMediationManager
import com.ushareit.core.subscription.BillingManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ActivityMainBinding
import com.nextai.sharenow.databinding.LayoutPopupMoreMenuBinding
import com.ushareit.core.p2p.P2PTransferManager
import com.ushareit.core.utils.ButtonLightSweep
import com.ushareit.core.webshare.WebShareServer
import com.ushareit.ui.clean.CleanitActivity
import com.ushareit.ui.coin.CoinStoreActivity
import com.ushareit.ui.muslim.MuslimDailyActivity
import com.ushareit.ui.paywall.PaywallActivity
import com.ushareit.ui.transfer.ReceiveActivity
import com.ushareit.ui.transfer.ReceivedFilesActivity
import com.ushareit.ui.transfer.SendActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : com.ushareit.ui.base.BaseActivity() {
    private var bannerAdView: com.google.android.gms.ads.AdView? = null

    private lateinit var binding: ActivityMainBinding
    private var browserTabController: com.ushareit.ui.browser.BrowserTabController? = null
    private var currentTab = Tab.HOME

    private enum class Tab {
        HOME, BROWSER, ME
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
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
                if (currentTab == Tab.BROWSER && browserTabController?.onBackPressed() == true) {
                    return
                }
                showExitConfirmationDialog()
            }
        })

        initViews()

        val savedTab = savedInstanceState?.getString("CURRENT_TAB") ?: intent.getStringExtra("CURRENT_TAB")
        if (savedTab == Tab.BROWSER.name) {
            switchTab(Tab.BROWSER)
            val initialUrl = intent.getStringExtra("BROWSER_URL")
            val autoDownload = intent.getBooleanExtra("AUTO_DOWNLOAD", false)
            if (!initialUrl.isNullOrBlank()) {
                browserTabController?.loadUrlOrSearch(
                    initialUrl,
                    autoOpenModalWhenReady = !autoDownload,
                    autoStartDownload = autoDownload
                )
            }
        }
        if (savedTab == Tab.ME.name) {
            switchTab(Tab.ME)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("CURRENT_TAB", currentTab.name)
    }

    private fun showExitConfirmationDialog() {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_exit_app, null)
            val dialog = AlertDialog.Builder(this, R.style.Theme_ShareNow_Dialog)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val btnYes = dialogView.findViewById<View>(R.id.btn_exit_yes)
            val btnCancel = dialogView.findViewById<View>(R.id.btn_exit_cancel)

            btnYes?.setOnClickListener {
                dialog.dismiss()
                finish()
            }

            btnCancel?.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Throwable) {
            e.printStackTrace()
            finish()
        }
    }

    private fun initNativeAds() {
        AdMediationManager.loadNativeAd(this, onLoaded = { nativeAd ->
            if (!isFinishing && !isDestroyed) {
                AdMediationManager.bindNativeAdToContainer(binding.layoutNativeAdHome, nativeAd)
            }
        })

        AdMediationManager.loadNativeAd(this, onLoaded = { nativeAd ->
            if (!isFinishing && !isDestroyed) {
                AdMediationManager.bindNativeAdToContainer(binding.includeTabMe.layoutNativeAdMe, nativeAd)
            }
        })
    }

    private fun initBannerAd() {
        try {
            if (!BillingManager.isVipUser()) {
                binding.layoutBannerAd.visibility = View.VISIBLE
                bannerAdView = AdMediationManager.loadAdaptiveBanner(this, binding.bannerContainerMain)
            } else {
                binding.layoutBannerAd.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            bannerAdView?.resume()
        } catch (_: Exception) {}
        updateReceivedFilesSummary()
        browserTabController?.onResume()
    }

    override fun onPause() {
        try {
            bannerAdView?.pause()
        } catch (_: Exception) {}
        super.onPause()
    }

    override fun onDestroy() {
        try {
            bannerAdView?.destroy()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun initViews() {
        initBannerAd()
        initNativeAds()

        // 1. Hero Action Buttons with Diagonal Light Sweep
        ButtonLightSweep.attachAlternating(binding.btnSend, binding.btnReceive)
        ButtonLightSweep.attachTo(binding.cardVip)

        binding.btnSend.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("btn_send", "MainActivity")
            FirebaseAnalyticsManager.logCardSendClick("MainActivity")
            onSendClicked()
        }

        binding.btnReceive.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("btn_receive", "MainActivity")
            FirebaseAnalyticsManager.logCardReceiveClick("MainActivity")
            onReceiveClicked()
        }

        // 2. Right Header Actions: Coins & 3-Dots Menu
        binding.btnCoinHeader.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("btn_coins", "MainActivity")
            onCoinClicked()
        }

        binding.btnMoreMenu.setOnClickListener { v ->
            FirebaseAnalyticsManager.logButtonClick("btn_more_menu", "MainActivity")
            showMoreOptionsMenu(v)
        }

        // 3. Feature Cards on Home Screen
        binding.cardCleanit.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("card_cleanit", "MainActivity")
            onCleanitClicked()
        }

        binding.cardReceivedFiles.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("card_received_files", "MainActivity")
            onReceivedFilesClicked()
        }

        binding.cardMuslim.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("card_muslim", "MainActivity")
            onMuslimClicked()
        }

        binding.cardVip.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("card_vip", "MainActivity")
            onVipClicked()
        }

        // 4. Quick Category Shortcuts
        binding.catApps.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("cat_apps", "MainActivity")
            val intent = Intent(this, SendActivity::class.java).apply {
                putExtra("SELECTED_TAB", "APPS")
            }
            startActivity(intent)
        }

        binding.catPhotos.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("cat_photos", "MainActivity")
            val intent = Intent(this, SendActivity::class.java).apply {
                putExtra("SELECTED_TAB", "PHOTOS")
            }
            startActivity(intent)
        }

        binding.catMusic.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("cat_music", "MainActivity")
            val intent = Intent(this, SendActivity::class.java).apply {
                putExtra("SELECTED_TAB", "MUSIC")
            }
            startActivity(intent)
        }

        binding.catVideos.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("cat_videos", "MainActivity")
            val intent = Intent(this, SendActivity::class.java).apply {
                putExtra("SELECTED_TAB", "VIDEOS")
            }
            startActivity(intent)
        }

        // 5. Bottom Navigation Bar (Trang chá»§ & TÃ´i)
        binding.navBtnHome.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("nav_home", "MainActivity")
            switchTab(Tab.HOME)
        }

        binding.navBtnBrowser.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("nav_browser", "MainActivity")
            switchTab(Tab.BROWSER)
        }

        binding.navBtnMe.setOnClickListener {
            FirebaseAnalyticsManager.logButtonClick("nav_me", "MainActivity")
            switchTab(Tab.ME)
        }

        // 6. Setup "TÃ´i" (Me) Tab Click Listeners
        initMeTabViews()
        browserTabController = com.ushareit.ui.browser.BrowserTabController(this, binding.includeTabBrowser.root)

        updateReceivedFilesSummary()
        browserTabController?.onResume()
    }

    private fun initMeTabViews() {
        val meBinding = binding.includeTabMe

        // Device & User Info
        val model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        meBinding.tvMeUsername.text = getString(R.string.me_username_default)
        meBinding.tvMeDeviceInfo.text = "$model â€¢ ${getString(R.string.me_device_status)}"
        try { meBinding.tvAppVersion.text = "v14" } catch (_: Exception) {}

        // Card Premium
        meBinding.cardMePremium.setOnClickListener {
            onVipClicked()
        }

        // Link Policy & TOS
        meBinding.btnMeTos.setOnClickListener {
            showTermsOfServiceDialog()
        }

        meBinding.btnMePolicy.setOnClickListener {
            showPrivacyPolicyDialog()
        }

        // Theme Selector
        meBinding.tvMeThemeStatus.text = getThemeStatusTitle()
        meBinding.btnMeTheme.setOnClickListener {
            showThemeSelectionDialog()
        }

        // Language Selector (i18n Card)
        meBinding.tvMeLanguageStatus.text = com.ushareit.core.locale.LocaleManager.getLanguageStatusTitle(this)
        meBinding.cardMeLanguage.setOnClickListener {
            showLanguageSelectionDialog()
        }
        meBinding.btnMeLanguage.setOnClickListener {
            showLanguageSelectionDialog()
        }

        // About Share Now
        meBinding.btnMeAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun getThemeStatusTitle(): String {
        return when (com.ushareit.core.theme.ThemeManager.getSavedThemeMode()) {
            com.ushareit.core.theme.ThemeManager.MODE_LIGHT -> getString(R.string.me_theme_light)
            com.ushareit.core.theme.ThemeManager.MODE_DARK -> getString(R.string.me_theme_dark)
            else -> getString(R.string.me_theme_system)
        }
    }

    private fun showThemeSelectionDialog() {
        val currentMode = com.ushareit.core.theme.ThemeManager.getSavedThemeMode()
        val options = arrayOf(
            getString(R.string.me_theme_system),
            getString(R.string.me_theme_light),
            getString(R.string.me_theme_dark)
        )
        val selectedIndex = when (currentMode) {
            com.ushareit.core.theme.ThemeManager.MODE_LIGHT -> 1
            com.ushareit.core.theme.ThemeManager.MODE_DARK -> 2
            else -> 0
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.me_theme_dialog_title)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val newMode = when (which) {
                    1 -> com.ushareit.core.theme.ThemeManager.MODE_LIGHT
                    2 -> com.ushareit.core.theme.ThemeManager.MODE_DARK
                    else -> com.ushareit.core.theme.ThemeManager.MODE_SYSTEM
                }
                com.ushareit.core.theme.ThemeManager.applyTheme(newMode)
                binding.includeTabMe.tvMeThemeStatus.text = getThemeStatusTitle()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showLanguageSelectionDialog() {
        val languages = com.ushareit.core.locale.LocaleManager.SUPPORTED_LANGUAGES
        val currentCode = com.ushareit.core.locale.LocaleManager.getSavedLanguage(this)
        val defaultBadge = getString(R.string.me_language_default_badge)
        val options = languages.map {
            if (it.code.equals(com.ushareit.core.locale.LocaleManager.DEFAULT_LANGUAGE, ignoreCase = true)) {
                "${it.nativeName} (${it.displayName}) ${it.flag} â€¢ $defaultBadge"
            } else {
                "${it.nativeName} (${it.displayName}) ${it.flag}"
            }
        }.toTypedArray()
        val selectedIndex = languages.indexOfFirst { it.code.equals(currentCode, ignoreCase = true) }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.me_language_dialog_title)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val selectedLang = languages[which]
                dialog.dismiss()
                if (!selectedLang.code.equals(currentCode, ignoreCase = true)) {
                    com.ushareit.core.locale.LocaleManager.applyLanguage(this, selectedLang.code)
                    binding.includeTabMe.tvMeLanguageStatus.text = com.ushareit.core.locale.LocaleManager.getLanguageStatusTitle(this)
                    val restartIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("CURRENT_TAB", Tab.ME.name)
                    }
                    startActivity(restartIntent)
                    finish()
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun switchTab(tab: Tab) {
        currentTab = tab
        if (tab == Tab.BROWSER) {
            FirebaseAnalyticsManager.logScreenView("BrowserTab", "BrowserTabController")
        }
        binding.layoutHomeContainer.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        binding.includeTabBrowser.layoutBrowserContainer.visibility = if (tab == Tab.BROWSER) View.VISIBLE else View.GONE
        binding.includeTabMe.layoutMeContainer.visibility = if (tab == Tab.ME) View.VISIBLE else View.GONE

        val activeColor = ContextCompat.getColor(this, R.color.nav_active)
        val inactiveColor = ContextCompat.getColor(this, R.color.nav_inactive)

        binding.ivNavHome.setColorFilter(if (tab == Tab.HOME) activeColor else inactiveColor)
        binding.tvNavHome.setTextColor(if (tab == Tab.HOME) activeColor else inactiveColor)

        binding.ivNavBrowser.setColorFilter(if (tab == Tab.BROWSER) activeColor else inactiveColor)
        binding.tvNavBrowser.setTextColor(if (tab == Tab.BROWSER) activeColor else inactiveColor)

        binding.ivNavMe.setColorFilter(if (tab == Tab.ME) activeColor else inactiveColor)
        binding.tvNavMe.setTextColor(if (tab == Tab.ME) activeColor else inactiveColor)
    }

    @Suppress("UNUSED_PARAMETER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
    private fun unusedOldSwitchTab(tab: Tab) {
        var currentTab = tab
        currentTab = tab
        if (tab == Tab.HOME) {
            binding.layoutHomeContainer.visibility = View.VISIBLE
            binding.includeTabMe.layoutMeContainer.visibility = View.GONE

            binding.ivNavHome.setColorFilter(ContextCompat.getColor(this, R.color.nav_active))
            binding.tvNavHome.setTextColor(ContextCompat.getColor(this, R.color.nav_active))

            binding.ivNavMe.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive))
            binding.tvNavMe.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive))
        } else {
            binding.layoutHomeContainer.visibility = View.GONE
            binding.includeTabMe.layoutMeContainer.visibility = View.VISIBLE

            binding.ivNavMe.setColorFilter(ContextCompat.getColor(this, R.color.nav_active))
            binding.tvNavMe.setTextColor(ContextCompat.getColor(this, R.color.nav_active))

            binding.ivNavHome.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive))
            binding.tvNavHome.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive))
        }
    }

    private fun updateReceivedFilesSummary() {
        val count = P2PTransferManager.getAllReceivedFiles().size
        if (count > 0) {
            binding.tvReceivedFilesSummary.text = getString(R.string.received_summary_has_files, count)
        } else {
            binding.tvReceivedFilesSummary.text = getString(R.string.received_summary_empty)
        }
    }

    // --- 3-DOTS OVERFLOW MENU ---
    private fun showMoreOptionsMenu(anchorView: View) {
        val popupBinding = LayoutPopupMoreMenuBinding.inflate(layoutInflater)
        val popupWindow = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
            elevation = 32f
        }

        val handleMenuItemClick = { action: () -> Unit ->
            popupWindow.dismiss()
            AdMediationManager.showInterstitialIfReady(this) {
                action()
            }
        }

        // 1. Invite
        popupBinding.menuItemInvite.setOnClickListener {
            handleMenuItemClick { onMenuInviteClicked() }
        }

        // 2. Scan QR code
        popupBinding.menuItemScanQr.setOnClickListener {
            handleMenuItemClick { onMenuScanQrClicked() }
        }

        // 3. Connect to PC
        popupBinding.menuItemConnectPc.setOnClickListener {
            handleMenuItemClick { onMenuConnectPcClicked() }
        }

        // 4. Connect to iOS
        popupBinding.menuItemConnectIos.setOnClickListener {
            handleMenuItemClick { onMenuConnectIosClicked() }
        }

        // 5. Share with KaiOS
        popupBinding.menuItemShareKaios.setOnClickListener {
            handleMenuItemClick { onMenuShareKaiosClicked() }
        }

        // 6. Phone Clone
        popupBinding.menuItemPhoneClone.setOnClickListener {
            handleMenuItemClick { onMenuPhoneCloneClicked() }
        }

        // 7. Groups Share
        popupBinding.menuItemGroupShare.setOnClickListener {
            handleMenuItemClick { onMenuGroupShareClicked() }
        }

        // 8. Near Play
        popupBinding.menuItemNearPlay.setOnClickListener {
            handleMenuItemClick { onMenuNearPlayClicked() }
        }

        // Show dropdown aligned to right
        val xOffset = -dpToPx(180)
        val yOffset = dpToPx(8)
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // --- 3-DOTS MENU ACTIONS ---

    private fun onMenuInviteClicked() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                getString(R.string.menu_invite_text)
            )
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_invite_chooser)))
    }

    private fun onMenuScanQrClicked() {
        // Quick QR Scanner / Receiver Pairing
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_qr_title)
            .setMessage(R.string.dialog_qr_desc)
            .setPositiveButton(R.string.dialog_qr_open_radar) { _, _ ->
                onReceiveClicked()
            }
            .setNegativeButton(R.string.btn_close, null)
            .show()
    }

    private fun onMenuConnectPcClicked() {
        startActivity(Intent(this, com.ushareit.ui.webshare.WebShareActivity::class.java))
    }

    private fun onMenuConnectIosClicked() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_connect_ios_title)
            .setMessage(R.string.dialog_connect_ios_desc)
            .setPositiveButton(R.string.dialog_connect_ios_btn) { _, _ ->
                onReceiveClicked()
            }
            .setNegativeButton(R.string.btn_close, null)
            .show()
    }

    private fun onMenuShareKaiosClicked() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_share_kaios_title)
            .setMessage(R.string.dialog_share_kaios_desc)
            .setPositiveButton(R.string.btn_understand, null)
            .show()
    }

    private fun onMenuPhoneCloneClicked() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_phone_clone_title)
            .setMessage(R.string.dialog_phone_clone_desc)
            .setPositiveButton(R.string.dialog_phone_clone_new) { _, _ ->
                onReceiveClicked()
            }
            .setNeutralButton(R.string.dialog_phone_clone_old) { _, _ ->
                onSendClicked()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun onMenuGroupShareClicked() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_group_share_title)
            .setMessage(R.string.dialog_group_share_desc)
            .setPositiveButton(R.string.dialog_group_share_create) { _, _ ->
                onSendClicked()
            }
            .setNeutralButton(R.string.dialog_group_share_join) { _, _ ->
                onReceiveClicked()
            }
            .setNegativeButton(R.string.btn_close, null)
            .show()
    }

    private fun onMenuNearPlayClicked() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_near_play_title)
            .setMessage(R.string.dialog_near_play_desc)
            .setPositiveButton(R.string.dialog_near_play_scan) { _, _ ->
                Toast.makeText(this, R.string.dialog_near_play_toast, Toast.LENGTH_SHORT).show()
                onReceiveClicked()
            }
            .setNegativeButton(R.string.btn_close, null)
            .show()
    }

    // --- ME TAB DIALOGS (Policy, TOS, About) ---

    private fun showTermsOfServiceDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_tos_title)
            .setMessage(R.string.dialog_tos_content)
            .setPositiveButton(R.string.btn_understand, null)
            .show()
    }

    private fun showPrivacyPolicyDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_policy_title)
            .setMessage(R.string.dialog_policy_content)
            .setPositiveButton(R.string.btn_agree, null)
            .setNeutralButton(R.string.btn_view_online) { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url)))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.mipmap.ic_launcher)
            .setTitle(R.string.dialog_about_title)
            .setMessage(R.string.dialog_about_content)
            .setPositiveButton(R.string.btn_close, null)
            .setNeutralButton(R.string.btn_view_online) { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url)))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "192.168.43.1"
    }

    // --- NAVIGATION ACTIONS ---

    fun onSendClicked() {
        com.ushareit.core.analytics.FirebaseAnalyticsManager.logTransferSendClick("home_header")
        startActivity(Intent(this, SendActivity::class.java))
    }

    fun onReceiveClicked() {
        com.ushareit.core.analytics.FirebaseAnalyticsManager.logTransferReceiveClick("home_header")
        startActivity(Intent(this, ReceiveActivity::class.java))
    }

    fun onCleanitClicked() {
        startActivity(Intent(this, CleanitActivity::class.java))
    }

    fun onReceivedFilesClicked() {
        startActivity(Intent(this, ReceivedFilesActivity::class.java))
    }

    fun onMuslimClicked() {
        startActivity(Intent(this, MuslimDailyActivity::class.java))
    }

    fun onCoinClicked() {
        com.ushareit.core.analytics.FirebaseAnalyticsManager.logCoinRewardView(1250)
        startActivity(Intent(this, CoinStoreActivity::class.java))
    }

    fun onVipClicked() {
        com.ushareit.core.analytics.FirebaseAnalyticsManager.logPaywallView("home_vip_card")
        startActivity(Intent(this, PaywallActivity::class.java))
    }
}
