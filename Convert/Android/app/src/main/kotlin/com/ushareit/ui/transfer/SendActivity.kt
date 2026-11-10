package com.ushareit.ui.transfer



import android.provider.OpenableColumns

import androidx.activity.result.PickVisualMediaRequest

import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia



import android.Manifest

import android.content.ClipData

import android.content.Intent

import android.content.pm.PackageManager

import android.database.Cursor

import android.graphics.Color

import android.net.Uri

import android.os.Build

import android.app.Activity
import android.os.Bundle

import android.os.Environment

import android.os.StatFs

import android.provider.ContactsContract

import android.provider.MediaStore

import android.text.Editable

import android.text.TextWatcher

import android.view.GestureDetector

import android.view.LayoutInflater

import android.view.MotionEvent

import android.view.VelocityTracker

import android.view.View

import android.view.ViewConfiguration

import android.view.ViewGroup

import android.view.animation.AccelerateInterpolator

import android.view.animation.DecelerateInterpolator

import android.view.inputmethod.EditorInfo

import android.view.inputmethod.InputMethodManager

import android.widget.ImageView

import android.widget.TextView

import android.widget.Toast

import androidx.activity.addCallback

import androidx.activity.result.contract.ActivityResultContracts

import androidx.appcompat.app.AppCompatActivity

import androidx.core.content.ContextCompat

import androidx.core.content.FileProvider

import androidx.core.view.ViewCompat

import androidx.core.view.WindowInsetsCompat

import androidx.recyclerview.widget.GridLayoutManager

import androidx.recyclerview.widget.LinearLayoutManager

import androidx.recyclerview.widget.RecyclerView

import com.bumptech.glide.Glide

import com.nextai.sharenow.R

import com.nextai.sharenow.databinding.*

import com.tencent.mmkv.MMKV

import com.ushareit.core.filemanager.FileCategory

import com.ushareit.core.filemanager.FileCategoryScanner

import com.ushareit.core.filemanager.FileItem

import com.ushareit.core.p2p.TransferFileInfo

import com.ushareit.core.ads.AdMediationManager

import com.ushareit.core.utils.ButtonLightSweep

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import java.io.File

import java.text.SimpleDateFormat

import java.util.*



enum class ActiveTab {

    RECENT, CONTACTS, FILES, VIDEOS, APPS, PHOTOS, MUSIC

}



data class UiFileItem(

    val name: String,

    val sizeText: String,

    val sizeBytes: Long,

    val category: FileCategory,

    var isSelected: Boolean = false,

    val uri: Uri? = null,

    val file: File? = null,

    val durationText: String = "",

    val subType: String = "",       // e.g. "PDF", "XLS", "DOC", "ZIP", or phone number

    val groupName: String = "",     // e.g. "Today", "Yesterday", "Apps", "Contacts"

    val isInstalledApp: Boolean = true,

    val packageName: String = ""

)



class SendActivity : com.ushareit.ui.base.BaseActivity() {

    private var bannerAdView: com.google.android.gms.ads.AdView? = null



    companion object {

        const val VIEW_MODE_GRID = 0

        const val VIEW_MODE_LIST = 1

        private const val KEY_VIEW_MODE = "key_send_file_view_mode"

    }



    private lateinit var binding: ActivitySendBinding

    private val allFiles = mutableListOf<UiFileItem>()

    private val displayedFiles = mutableListOf<UiFileItem>()

    private lateinit var adapter: ShareItFileAdapter



    private var currentTab: ActiveTab = ActiveTab.APPS

    private var subsegmentIndex: Int = 1 // 1: First pill, 2: Second pill, 3: Third pill

    private var isGroupCollapsed: Boolean = false

    private var currentViewMode: Int = VIEW_MODE_GRID



    private var isSearchActive: Boolean = false

    private var currentSearchQuery: String = ""



    private val tabList = listOf(

        ActiveTab.RECENT,

        ActiveTab.CONTACTS,

        ActiveTab.FILES,

        ActiveTab.VIDEOS,

        ActiveTab.APPS,

        ActiveTab.PHOTOS,

        ActiveTab.MUSIC

    )



    // Reusable Layout Managers to prevent destroying and recreating view pool on tab switch

    private val gridLayoutManager by lazy { GridLayoutManager(this, 2) }

    private val linearLayoutManager by lazy { LinearLayoutManager(this) }



    // Pre-categorized file caches for instant O(1) tab switching

    private val cachedAppsInstalled = mutableListOf<UiFileItem>()

    private val cachedAppsNotInstalled = mutableListOf<UiFileItem>()

    private val cachedPhotos = mutableListOf<UiFileItem>()

    private val cachedVideos = mutableListOf<UiFileItem>()

    private val cachedMusic = mutableListOf<UiFileItem>()

    private val cachedContacts = mutableListOf<UiFileItem>()

    private val cachedRecentReceived = mutableListOf<UiFileItem>()

    private val cachedRecentSent = mutableListOf<UiFileItem>()



    // Interactive Swipe & Touch Tracking State

    private var touchStartX = 0f

    private var touchStartY = 0f

    private var isHorizontalSwiping = false

    private var velocityTracker: VelocityTracker? = null



    private val permissionLauncher = registerForActivityResult(

        ActivityResultContracts.RequestMultiplePermissions()

    ) { permissions ->

        val anyGranted = permissions.values.any { it }

        if (anyGranted) {

            Toast.makeText(this, getString(R.string.permission_media_granted), Toast.LENGTH_SHORT).show()

        }

        com.ushareit.core.analytics.FirebaseAnalyticsManager.logPermissionResult("media_storage", anyGranted)

        loadAllData()

    }



    // Standard Android System Photo Picker (Tuân thủ Google Play Policy)

    private val systemPhotoPickerLauncher = registerForActivityResult(

        ActivityResultContracts.PickMultipleVisualMedia()

    ) { uris ->

        if (uris.isNotEmpty()) {

            handleSystemPickedMedia(uris)

        }

    }



    private fun launchSystemPhotoPicker(visualMediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo) {

        try {

            systemPhotoPickerLauncher.launch(

                androidx.activity.result.PickVisualMediaRequest(visualMediaType)

            )

        } catch (e: Exception) {

            // Fallback sang Intent GET_CONTENT nếu thiết bị không hỗ trợ Photo Picker

            try {

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {

                    type = "*/*"

                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))

                }

                fallbackPickerLauncher.launch(intent)

            } catch (ex: Exception) {

                ex.printStackTrace()

            }

        }

    }



    private val fallbackPickerLauncher = registerForActivityResult(

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

                handleSystemPickedMedia(uris)

            }

        }

    }



    private fun handleSystemPickedMedia(uris: List<Uri>) {

        val cr = contentResolver

        for (uri in uris) {

            var fileName = "media_${System.currentTimeMillis()}"

            var fileSize = 0L

            var mimeType = cr.getType(uri) ?: ""



            try {

                cr.query(uri, null, null, null, null)?.use { cursor ->

                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)

                    if (cursor.moveToFirst()) {

                        if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName

                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)

                    }

                }

            } catch (e: Exception) {

                e.printStackTrace()

            }



            val isVideo = mimeType.startsWith("video") || fileName.endsWith(".mp4", true) || fileName.endsWith(".mkv", true)

            val category = if (isVideo) FileCategory.VIDEO else FileCategory.PHOTO



            val item = UiFileItem(

                name = fileName,

                sizeText = formatBytes(fileSize),

                sizeBytes = fileSize,

                category = category,

                uri = uri,

                file = File(cacheDir, fileName),

                isInstalledApp = false,

                groupName = if (category == FileCategory.PHOTO) "Picked Photos" else "Picked Videos"

            )



            // Thêm vào allFiles nếu chưa có

            if (allFiles.none { it.uri == uri }) {

                allFiles.add(0, item)
                if (category == FileCategory.VIDEO) cachedVideos.add(0, item) else cachedPhotos.add(0, item)
                com.ushareit.core.filemanager.SharedFilePool.addPickedUris(this, listOf(uri))

            }

            // Tự động chọn file vừa pick

            item.isSelected = true

        }

        filterDataForCurrentView()

    }



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        com.ushareit.core.analytics.FirebaseAnalyticsManager.logTransferSendClick("send_activity")



        // Set status bar dynamically based on theme

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            window.statusBarColor = ContextCompat.getColor(this, R.color.shareit_white)

            val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

            @Suppress("DEPRECATION")

            window.decorView.systemUiVisibility = if (isNightMode) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        }



        binding = ActivitySendBinding.inflate(layoutInflater)

        setContentView(binding.root)



        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->

            val insets = windowInsets.getInsets(

                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

            )

            binding.root.setPadding(insets.left, insets.top, insets.right, insets.bottom)

            windowInsets

        }



        currentViewMode = try {

            MMKV.defaultMMKV()?.decodeInt(KEY_VIEW_MODE, VIEW_MODE_GRID) ?: VIEW_MODE_GRID

        } catch (e: Throwable) {

            VIEW_MODE_GRID

        }



        initViews()

        initFilesDashboard()

        checkAndRequestMediaPermissions()

        handleIntentTab()



        onBackPressedDispatcher.addCallback(this) {

            handleBackPress()

        }

    }



    override fun onNewIntent(intent: Intent?) {

        super.onNewIntent(intent)

        setIntent(intent)

        handleIntentTab()

    }



    override fun onPause() {

        try { bannerAdView?.pause() } catch (_: Exception) {}

        super.onPause()

    }



    override fun onResume() {

        super.onResume()

        try { bannerAdView?.resume() } catch (_: Exception) {}

        if (allFiles.isEmpty()) {

            loadAllData()

        }

    }



    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {

        if (isSearchActive) {

            return super.dispatchTouchEvent(ev)

        }



        when (ev.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                val loc = IntArray(2)

                binding.containerContent.getLocationOnScreen(loc)

                val contentTop = loc[1]

                val contentBottom = contentTop + binding.containerContent.height



                if (ev.rawY >= contentTop && ev.rawY <= contentBottom) {

                    touchStartX = ev.rawX

                    touchStartY = ev.rawY

                    isHorizontalSwiping = false

                    velocityTracker?.recycle()

                    velocityTracker = VelocityTracker.obtain()

                    velocityTracker?.addMovement(ev)

                } else {

                    velocityTracker?.recycle()

                    velocityTracker = null

                }

            }

            MotionEvent.ACTION_MOVE -> {

                if (velocityTracker != null) {

                    velocityTracker?.addMovement(ev)

                    val diffX = ev.rawX - touchStartX

                    val diffY = ev.rawY - touchStartY

                    val touchSlop = ViewConfiguration.get(this).scaledTouchSlop



                    if (!isHorizontalSwiping) {

                        if (Math.abs(diffX) > touchSlop * 1.2f && Math.abs(diffX) > Math.abs(diffY) * 1.35f) {

                            isHorizontalSwiping = true

                            binding.rvFiles.requestDisallowInterceptTouchEvent(true)

                            // Cancel child touches (e.g. click states on items)

                            val cancelEvent = MotionEvent.obtain(ev)

                            cancelEvent.action = MotionEvent.ACTION_CANCEL

                            super.dispatchTouchEvent(cancelEvent)

                            cancelEvent.recycle()

                        }

                    }



                    if (isHorizontalSwiping) {

                        val currentIndex = tabList.indexOf(currentTab)

                        val isAtStart = (currentIndex == 0 && diffX > 0)

                        val isAtEnd = (currentIndex == tabList.size - 1 && diffX < 0)

                        val dragFactor = if (isAtStart || isAtEnd) 0.18f else 0.45f

                        val currentDragX = diffX * dragFactor

                        binding.containerContent.translationX = currentDragX

                        binding.containerContent.alpha = (1f - (Math.abs(currentDragX) / resources.displayMetrics.widthPixels) * 0.35f).coerceIn(0.65f, 1f)

                        return true

                    }

                }

            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

                if (isHorizontalSwiping) {

                    isHorizontalSwiping = false

                    velocityTracker?.addMovement(ev)

                    velocityTracker?.computeCurrentVelocity(1000)

                    val vx = velocityTracker?.xVelocity ?: 0f

                    velocityTracker?.recycle()

                    velocityTracker = null



                    val diffX = ev.rawX - touchStartX

                    val screenWidth = resources.displayMetrics.widthPixels.toFloat()

                    val swipeThreshold = screenWidth * 0.16f

                    val currentIndex = tabList.indexOf(currentTab)



                    val canGoNext = currentIndex < tabList.size - 1

                    val canGoPrev = currentIndex > 0



                    val isSwipeNext = (diffX < -swipeThreshold || (diffX < -40 && vx < -400)) && canGoNext

                    val isSwipePrev = (diffX > swipeThreshold || (diffX > 40 && vx > 400)) && canGoPrev



                    if (isSwipeNext) {

                        completeSwipeTransition(toNext = true)

                    } else if (isSwipePrev) {

                        completeSwipeTransition(toNext = false)

                    } else {

                        binding.containerContent.animate()

                            .translationX(0f)

                            .alpha(1f)

                            .setDuration(180)

                            .setInterpolator(DecelerateInterpolator())

                            .start()

                    }

                    return true

                }

                velocityTracker?.recycle()

                velocityTracker = null

            }

        }

        return super.dispatchTouchEvent(ev)

    }



    private fun completeSwipeTransition(toNext: Boolean) {

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        val targetOutX = if (toNext) -screenWidth * 0.35f else screenWidth * 0.35f

        val targetInX = if (toNext) screenWidth * 0.35f else -screenWidth * 0.35f



        // Haptic feedback

        try {

            binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)

        } catch (_: Throwable) {}



        // Phase 1: Rapid, smooth glide out in swipe direction

        binding.containerContent.animate()

            .translationX(targetOutX)

            .alpha(0f)

            .setDuration(110)

            .setInterpolator(AccelerateInterpolator())

            .withEndAction {

                // Phase 2: Update Tab data while invisible

                val currentIndex = tabList.indexOf(currentTab)

                val newIndex = if (toNext) currentIndex + 1 else currentIndex - 1

                if (newIndex in tabList.indices) {

                    currentTab = tabList[newIndex]

                    updateTabStripUI(currentTab)

                    configureSubsegmentForTab(currentTab)

                    applyLayoutManagerForTab(currentTab)

                    filterDataForCurrentView()

                }



                // Phase 3: Slide in fresh content from the opposite side

                binding.containerContent.translationX = targetInX

                binding.containerContent.alpha = 0f

                binding.containerContent.animate()

                    .translationX(0f)

                    .alpha(1f)

                    .setDuration(220)

                    .setInterpolator(DecelerateInterpolator(1.8f))

                    .start()

            }

            .start()

    }



    private fun switchToNextTab() {

        val currentIndex = tabList.indexOf(currentTab)

        if (currentIndex in 0 until tabList.size - 1) {

            completeSwipeTransition(toNext = true)

        }

    }



    private fun switchToPreviousTab() {

        val currentIndex = tabList.indexOf(currentTab)

        if (currentIndex > 0) {

            completeSwipeTransition(toNext = false)

        }

    }



    private fun initViews() {

        binding.btnBack.setOnClickListener {

            handleBackPress()

        }



        binding.btnSearch.setOnClickListener {

            openSearch()

        }



        binding.btnSearchBack.setOnClickListener {

            closeSearch()

        }



        binding.btnSearchClear.setOnClickListener {

            binding.edtSearchQuery.text?.clear()

        }



        binding.edtSearchQuery.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {

                val query = s?.toString().orEmpty()

                binding.btnSearchClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE

                performSearch(query)

            }

        })



        binding.edtSearchQuery.setOnEditorActionListener { _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {

                hideKeyboard()

                true

            } else {

                false

            }

        }



        // Gắn hiệu ứng ánh sáng quét chéo trên nút NEXT

        ButtonLightSweep.attachTo(binding.btnActionSendNow)



        adapter = ShareItFileAdapter(displayedFiles) {

            updateSelectedSummary()

            updateGroupSelectAllCheckbox()

        }



        binding.rvFiles.setHasFixedSize(true)

        binding.rvFiles.setItemViewCacheSize(30)

        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {

            override fun getSpanSize(position: Int): Int {

                return if (::adapter.isInitialized && adapter.getItemViewType(position) == 4) 2 else 1

            }

        }

        binding.rvFiles.adapter = adapter

        initNativeAd()

        initBannerAd()



        // Setup System Photo/Video Picker Button

        binding.btnSystemPicker.setOnClickListener {

            val mediaType = when (currentTab) {

                ActiveTab.PHOTOS -> ActivityResultContracts.PickVisualMedia.ImageOnly

                ActiveTab.VIDEOS -> ActivityResultContracts.PickVisualMedia.VideoOnly

                else -> ActivityResultContracts.PickVisualMedia.ImageAndVideo

            }

            launchSystemPhotoPicker(mediaType)

        }



        binding.layoutEmptyView.btnEmptySystemPicker.setOnClickListener {

            val mediaType = when (currentTab) {

                ActiveTab.PHOTOS -> ActivityResultContracts.PickVisualMedia.ImageOnly

                ActiveTab.VIDEOS -> ActivityResultContracts.PickVisualMedia.VideoOnly

                else -> ActivityResultContracts.PickVisualMedia.ImageAndVideo

            }

            launchSystemPhotoPicker(mediaType)

        }



        // Setup 7 Tab Click Listeners

        binding.tabContainerRecent.setOnClickListener { switchTab(ActiveTab.RECENT) }

        binding.tabContainerContacts.setOnClickListener { switchTab(ActiveTab.CONTACTS) }

        binding.tabContainerFiles.setOnClickListener { switchTab(ActiveTab.FILES) }

        binding.tabContainerVideos.setOnClickListener { switchTab(ActiveTab.VIDEOS) }

        binding.tabContainerApps.setOnClickListener { switchTab(ActiveTab.APPS) }

        binding.tabContainerPhotos.setOnClickListener { switchTab(ActiveTab.PHOTOS) }

        binding.tabContainerMusic.setOnClickListener { switchTab(ActiveTab.MUSIC) }



        // Subsegment Capsule Click Listeners

        binding.btnSub1.setOnClickListener { onSubsegmentSelected(1) }

        binding.btnSub2.setOnClickListener { onSubsegmentSelected(2) }

        binding.btnSub3.setOnClickListener { onSubsegmentSelected(3) }



        // Group Header Collapse & Select All

        binding.btnGroupCollapse.setOnClickListener {

            isGroupCollapsed = !isGroupCollapsed

            binding.ivGroupChevron.setImageResource(

                if (isGroupCollapsed) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up

            )

            binding.rvFiles.visibility = if (isGroupCollapsed) View.GONE else View.VISIBLE

        }



        binding.ivGroupSelectAll.setOnClickListener {

            toggleSelectAllCurrentList()

        }



        // Toggle View Mode: Dạng danh sách (List có share) <-> Dạng lưới 2 cột (Grid)

        updateViewModeToggleIcon()

        binding.btnToggleViewMode.setOnClickListener {

            toggleViewMode()

        }



        // Bottom Bar Action Send

        binding.btnActionSendNow.setOnClickListener {

            onFilesSelected()

        }



        updateSelectedSummary()

    }



    private fun handleIntentTab() {

        when (intent.getStringExtra("SELECTED_TAB")?.uppercase()) {

            "PHOTOS" -> switchTab(ActiveTab.PHOTOS)

            "VIDEOS" -> switchTab(ActiveTab.VIDEOS)

            "APPS" -> switchTab(ActiveTab.APPS)

            "MUSIC" -> switchTab(ActiveTab.MUSIC)

            "FILES" -> switchTab(ActiveTab.FILES)

            "CONTACTS" -> switchTab(ActiveTab.CONTACTS)

            "RECENT" -> switchTab(ActiveTab.RECENT)

            else -> switchTab(ActiveTab.APPS)

        }

    }



    private fun switchTab(tab: ActiveTab, isSwipeForward: Boolean? = null) {

        val oldIndex = tabList.indexOf(currentTab)

        val newIndex = tabList.indexOf(tab)

        val isForward = isSwipeForward ?: (if (oldIndex != -1 && newIndex != -1 && oldIndex != newIndex) newIndex > oldIndex else null)



        currentTab = tab

        subsegmentIndex = if (tab == ActiveTab.VIDEOS || tab == ActiveTab.PHOTOS) 2 else 1 // Default "Recent" for photos/videos, "Installed" for apps



        // Haptic feedback for tactile feel

        try {

            binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)

        } catch (_: Throwable) {}



        // 1. Update Tab Bar UI

        updateTabStripUI(tab)



        // 2. Configure Subsegment Capsule

        configureSubsegmentForTab(tab)



        // 3. Configure Recycler Layout Manager & Empty State

        applyLayoutManagerForTab(tab)



        // 4. Filter and display files

        if (isSearchActive && currentSearchQuery.isNotBlank()) {

            performSearch(currentSearchQuery)

        } else {

            filterDataForCurrentView()

        }



        // 5. Ultra-smooth slide & fade transition for content

        if (isForward != null) {

            val slideOffset = resources.displayMetrics.widthPixels * 0.25f

            binding.containerContent.translationX = if (isForward) slideOffset else -slideOffset

            binding.containerContent.alpha = 0.25f

            binding.containerContent.animate()

                .translationX(0f)

                .alpha(1f)

                .setDuration(220)

                .setInterpolator(DecelerateInterpolator(1.8f))

                .start()

        } else {

            binding.containerContent.alpha = 0.5f

            binding.containerContent.animate().alpha(1f).setDuration(160).start()

        }

    }



    private fun updateTabStripUI(activeTab: ActiveTab) {

        val tabMap = mapOf(

            ActiveTab.RECENT to Pair(binding.tvTabRecent, binding.indicatorTabRecent),

            ActiveTab.CONTACTS to Pair(binding.tvTabContacts, binding.indicatorTabContacts),

            ActiveTab.FILES to Pair(binding.tvTabFiles, binding.indicatorTabFiles),

            ActiveTab.VIDEOS to Pair(binding.tvTabVideos, binding.indicatorTabVideos),

            ActiveTab.APPS to Pair(binding.tvTabApps, binding.indicatorTabApps),

            ActiveTab.PHOTOS to Pair(binding.tvTabPhotos, binding.indicatorTabPhotos),

            ActiveTab.MUSIC to Pair(binding.tvTabMusic, binding.indicatorTabMusic)

        )



        val activeBlue = ContextCompat.getColor(this, R.color.shareit_blue)

        val inactiveGray = ContextCompat.getColor(this, R.color.shareit_text_secondary)



        tabMap.forEach { (tab, views) ->

            val (tv, indicator) = views

            if (tab == activeTab) {

                tv.setTextColor(activeBlue)

                tv.animate().scaleX(1.06f).scaleY(1.06f).setDuration(180).start()

                indicator.visibility = View.VISIBLE

                indicator.alpha = 0.2f

                indicator.scaleX = 0.3f

                indicator.animate().alpha(1f).scaleX(1f).setDuration(200).start()

            } else {

                tv.setTextColor(inactiveGray)

                tv.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()

                indicator.visibility = View.INVISIBLE

                indicator.scaleX = 0.3f

            }

        }



        val containerMap = mapOf(

            ActiveTab.RECENT to binding.tabContainerRecent,

            ActiveTab.CONTACTS to binding.tabContainerContacts,

            ActiveTab.FILES to binding.tabContainerFiles,

            ActiveTab.VIDEOS to binding.tabContainerVideos,

            ActiveTab.APPS to binding.tabContainerApps,

            ActiveTab.PHOTOS to binding.tabContainerPhotos,

            ActiveTab.MUSIC to binding.tabContainerMusic

        )

        containerMap[activeTab]?.let { targetView ->

            binding.scrollCategoryTabs.post {

                val scrollX = targetView.left - (binding.scrollCategoryTabs.width - targetView.width) / 2

                binding.scrollCategoryTabs.smoothScrollTo(scrollX.coerceAtLeast(0), 0)

            }

        }

    }



    private fun configureSubsegmentForTab(tab: ActiveTab) {

        when (tab) {

            ActiveTab.APPS -> {

                binding.layoutSubsegmentContainer.visibility = View.VISIBLE

                binding.btnSub1.text = getString(R.string.sub_installed)

                binding.btnSub2.text = getString(R.string.sub_not_installed)

                binding.btnSub3.visibility = View.GONE

                updateSubsegmentPillUI(subsegmentIndex)

            }

            ActiveTab.PHOTOS, ActiveTab.VIDEOS -> {

                binding.layoutSubsegmentContainer.visibility = View.VISIBLE

                binding.btnSub1.text = getString(R.string.sub_safebox)

                binding.btnSub2.text = getString(R.string.sub_recent)

                binding.btnSub3.text = getString(R.string.sub_folders)

                binding.btnSub3.visibility = View.VISIBLE

                updateSubsegmentPillUI(subsegmentIndex)

            }

            ActiveTab.RECENT -> {

                binding.layoutSubsegmentContainer.visibility = View.VISIBLE

                binding.btnSub1.text = getString(R.string.sub_received)

                binding.btnSub2.text = getString(R.string.sub_sent)

                binding.btnSub3.visibility = View.GONE

                updateSubsegmentPillUI(subsegmentIndex)

            }

            ActiveTab.FILES, ActiveTab.CONTACTS, ActiveTab.MUSIC -> {

                binding.layoutSubsegmentContainer.visibility = View.GONE

            }

        }

    }



    private fun onSubsegmentSelected(index: Int) {

        subsegmentIndex = index

        updateSubsegmentPillUI(index)

        if (isSearchActive && currentSearchQuery.isNotBlank()) {

            performSearch(currentSearchQuery)

        } else {

            filterDataForCurrentView()

            binding.rvFiles.alpha = 0.5f

            binding.rvFiles.animate().alpha(1f).setDuration(160).start()

        }

    }



    private fun updateSubsegmentPillUI(selectedIndex: Int) {

        val pills = listOf(binding.btnSub1, binding.btnSub2, binding.btnSub3)

        val titleColor = ContextCompat.getColor(this, R.color.shareit_text_title)

        val grayColor = ContextCompat.getColor(this, R.color.shareit_text_secondary)



        pills.forEachIndexed { idx, pill ->

            if (idx + 1 == selectedIndex) {

                pill.setBackgroundResource(R.drawable.bg_capsule_active)

                pill.setTextColor(titleColor)

            } else {

                pill.setBackgroundColor(Color.TRANSPARENT)

                pill.setTextColor(grayColor)

            }

        }

    }



    private fun applyLayoutManagerForTab(tab: ActiveTab) {

        // Reset collapse state

        isGroupCollapsed = false

        binding.ivGroupChevron.setImageResource(R.drawable.ic_chevron_up)



        if (tab == ActiveTab.FILES && binding.layoutFilesDashboardView.root.visibility == View.VISIBLE) {

            binding.layoutFilesDashboardView.root.visibility = View.VISIBLE

            binding.layoutGroupHeaderBar.visibility = View.GONE

            binding.rvFiles.visibility = View.GONE

            binding.layoutEmptyView.root.visibility = View.GONE

            return

        }



        binding.layoutFilesDashboardView.root.visibility = View.GONE

        binding.layoutGroupHeaderBar.visibility = View.VISIBLE

        binding.rvFiles.visibility = View.VISIBLE



        val targetLM = if (tab == ActiveTab.CONTACTS) {

            linearLayoutManager

        } else {

            if (currentViewMode == VIEW_MODE_GRID) gridLayoutManager else linearLayoutManager

        }



        if (binding.rvFiles.layoutManager !== targetLM) {

            binding.rvFiles.layoutManager = targetLM

        }

    }



    private fun filterDataForCurrentView(docFilter: String? = null) {

        if (currentTab == ActiveTab.FILES && docFilter == null) {

            binding.layoutFilesDashboardView.root.visibility = View.VISIBLE

            binding.rvFiles.visibility = View.GONE

            binding.layoutGroupHeaderBar.visibility = View.GONE

            binding.layoutEmptyView.root.visibility = View.GONE

            return

        }



        displayedFiles.clear()



        when (currentTab) {

            ActiveTab.APPS -> {

                val isInstalled = (subsegmentIndex == 1)

                val apps = if (isInstalled) cachedAppsInstalled else cachedAppsNotInstalled

                displayedFiles.addAll(apps)

                binding.tvGroupTitleCount.text = if (isInstalled) "${getString(R.string.group_apps)} (${apps.size})" else "${getString(R.string.sub_not_installed)} (${apps.size})"

            }

            ActiveTab.PHOTOS -> {

                displayedFiles.addAll(cachedPhotos)

                binding.tvGroupTitleCount.text = "${getString(R.string.group_today)} (${cachedPhotos.size})"

            }

            ActiveTab.VIDEOS -> {

                displayedFiles.addAll(cachedVideos)

                binding.tvGroupTitleCount.text = "${getString(R.string.group_yesterday)} (${cachedVideos.size})"

            }

            ActiveTab.MUSIC -> {

                displayedFiles.addAll(cachedMusic)

                binding.tvGroupTitleCount.text = "${getString(R.string.category_music)} (${cachedMusic.size})"

            }

            ActiveTab.CONTACTS -> {

                displayedFiles.addAll(cachedContacts)

                binding.tvGroupTitleCount.text = "${getString(R.string.group_contacts)} (${cachedContacts.size})"

            }

            ActiveTab.RECENT -> {

                val isReceived = (subsegmentIndex == 1)

                val recents = if (isReceived) cachedRecentReceived else cachedRecentSent

                displayedFiles.addAll(recents)

                binding.tvGroupTitleCount.text = if (isReceived) "${getString(R.string.sub_received)} (${recents.size})" else "${getString(R.string.sub_sent)} (${recents.size})"

            }

            ActiveTab.FILES -> {

                // Filtered documents from dashboard

                binding.layoutFilesDashboardView.root.visibility = View.GONE

                binding.rvFiles.visibility = View.VISIBLE

                binding.layoutGroupHeaderBar.visibility = View.VISIBLE

                applyLayoutManagerForTab(ActiveTab.FILES)



                val docs = if (docFilter == "ALL" || docFilter == null) {

                    allFiles.filter { it.category == FileCategory.DOCUMENT && it.subType.isEmpty() }

                } else if (docFilter == "ZIP") {

                    allFiles.filter { it.category == FileCategory.ZIP || it.name.endsWith(".zip", true) || it.name.endsWith(".rar", true) }

                } else if (docFilter == "LARGE") {

                    allFiles.filter { it.sizeBytes > 50 * 1024 * 1024L }

                } else {

                    allFiles.filter { it.name.endsWith(".$docFilter", true) }

                }

                displayedFiles.addAll(docs)

                binding.tvGroupTitleCount.text = "${docFilter ?: getString(R.string.category_docs)} (${docs.size})"

            }

        }



        // Show empty state if no files
        val isFilesDashboardVisible = (currentTab == ActiveTab.FILES && docFilter == null)
        if (!isFilesDashboardVisible && displayedFiles.isEmpty()) {
            binding.layoutEmptyView.tvEmptyText.text = "No file"
            binding.layoutEmptyView.root.visibility = View.VISIBLE
            binding.rvFiles.visibility = View.GONE
            updateEmptyStateNativeAd()
        } else if (!isFilesDashboardVisible) {
            binding.layoutEmptyView.root.visibility = View.GONE
            if (!isGroupCollapsed) binding.rvFiles.visibility = View.VISIBLE
        }



        adapter.notifyDataSetChanged()

        updateGroupSelectAllCheckbox()

    }



    private fun toggleSelectAllCurrentList() {

        if (displayedFiles.isEmpty()) return

        val allSelected = displayedFiles.all { it.isSelected }

        displayedFiles.forEach { it.isSelected = !allSelected }

        adapter.notifyDataSetChanged()

        updateSelectedSummary()

        updateGroupSelectAllCheckbox()

    }



    private fun updateGroupSelectAllCheckbox() {

        if (displayedFiles.isEmpty()) {

            binding.ivGroupSelectAll.setImageResource(R.drawable.icon_unselected)

            return

        }

        val allSelected = displayedFiles.all { it.isSelected }

        binding.ivGroupSelectAll.setImageResource(

            if (allSelected) R.drawable.icon_selected else R.drawable.icon_unselected

        )

    }



    private fun initBannerAd() {
        try {
            bannerAdView = AdMediationManager.loadCollapsibleBanner(this, binding.bannerContainerSend, AdMediationManager.BANNER_ID, "bottom")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    private var cachedNativeAd: com.google.android.gms.ads.nativead.NativeAd? = null

    private fun initNativeAd() {
        AdMediationManager.loadNativeAd(this, onLoaded = { nativeAd ->
            if (!isFinishing && !isDestroyed) {
                cachedNativeAd = nativeAd
                if (::adapter.isInitialized) {
                    adapter.setNativeAd(nativeAd)
                }
                val filesTabSlot = binding.layoutFilesDashboardView.root.findViewById<ViewGroup>(R.id.layout_native_ad_files_tab)
                if (filesTabSlot != null) {
                    AdMediationManager.bindNativeAdToContainer(filesTabSlot, nativeAd)
                }
                updateEmptyStateNativeAd()
            }
        })
    }

    private fun updateEmptyStateNativeAd() {
        val nativeAd = cachedNativeAd
        val emptySlot = binding.layoutEmptyView.layoutNativeAdEmpty
        if (nativeAd != null && !com.ushareit.core.subscription.BillingManager.isVipUser()) {
            AdMediationManager.bindNativeAdToContainer(emptySlot, nativeAd)
            emptySlot.visibility = View.VISIBLE
        } else {
            emptySlot.visibility = View.GONE
        }
    }



    private fun initFilesDashboard() {

        val dashboard = binding.layoutFilesDashboardView



        // Document Category Tiles

        dashboard.tileDocAll.setOnClickListener { filterDataForCurrentView("ALL") }

        dashboard.tileDocPdf.setOnClickListener { filterDataForCurrentView("pdf") }

        dashboard.tileDocExcel.setOnClickListener { filterDataForCurrentView("xls") }

        dashboard.tileDocPpt.setOnClickListener { filterDataForCurrentView("ppt") }

        dashboard.tileDocTxt.setOnClickListener { filterDataForCurrentView("txt") }

        dashboard.tileDocWord.setOnClickListener { filterDataForCurrentView("doc") }

        dashboard.tileDocWps.setOnClickListener { filterDataForCurrentView("wps") }

        dashboard.layoutDocsHeader.setOnClickListener { filterDataForCurrentView("ALL") }



        // Categorized rows

        dashboard.rowZipFiles.setOnClickListener { filterDataForCurrentView("ZIP") }

        dashboard.rowLargeFiles.setOnClickListener { filterDataForCurrentView("LARGE") }

        dashboard.rowInternalStorage.setOnClickListener { filterDataForCurrentView("ALL") }



        // WhatsApp Transfer

        dashboard.btnWaStatus.setOnClickListener {

            Toast.makeText(this, getString(R.string.files_whatsapp_status), Toast.LENGTH_SHORT).show()

        }

        dashboard.btnWaMedia.setOnClickListener {

            Toast.makeText(this, getString(R.string.files_whatsapp_media), Toast.LENGTH_SHORT).show()

        }



        // Calculate and display storage

        try {

            val stat = StatFs(Environment.getDataDirectory().path)

            val blockSize = stat.blockSizeLong

            val totalBlocks = stat.blockCountLong

            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize

            val freeBytes = availableBlocks * blockSize

            val usedBytes = totalBytes - freeBytes



            val usedGb = String.format(Locale.US, "%.1f GB", usedBytes / (1024.0 * 1024.0 * 1024.0))

            val totalGb = String.format(Locale.US, "%.0f GB", totalBytes / (1024.0 * 1024.0 * 1024.0))

            val percent = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0



            dashboard.tvStorageCapacity.text = "$usedGb / $totalGb"

            dashboard.pbStorageBar.progress = percent

        } catch (e: Throwable) {

            e.printStackTrace()

        }

    }



    private fun checkAndRequestMediaPermissions() {

        val needed = mutableListOf<String>()

        // Tuân thủ chính sách Google Play: KHÔNG yêu cầu READ_MEDIA_IMAGES hoặc READ_MEDIA_VIDEO

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {

                needed.add(Manifest.permission.READ_MEDIA_AUDIO)

            }

        } else {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)

            }

        }



        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {

            needed.add(Manifest.permission.READ_CONTACTS)

        }



        loadAllData()



        if (needed.isNotEmpty()) {

            try {

                permissionLauncher.launch(needed.toTypedArray())

            } catch (e: Throwable) {

                e.printStackTrace()

            }

        }

    }



    private fun loadAllData() {

        allFiles.clear()



        CoroutineScope(Dispatchers.Main).launch {

            try {

                val scanner = FileCategoryScanner(this@SendActivity)



                // 1. Quét ứng dụng

                val realApps = withContext(Dispatchers.IO) {

                    scanner.scanCategory(FileCategory.APP)

                }

                realApps.forEach { item ->

                    allFiles.add(

                        UiFileItem(

                            name = item.name,

                            sizeText = formatBytes(item.size),

                            sizeBytes = item.size,

                            category = FileCategory.APP,

                            uri = item.uri,

                            file = item.file,

                            isInstalledApp = true,

                            groupName = "Apps",

                            packageName = item.packageName

                        )

                    )

                }



                // Quét APK chưa cài đặt từ bộ nhớ

                withContext(Dispatchers.IO) {

                    try {

                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                        val apkFiles = downloadDir?.listFiles { f -> f.extension.equals("apk", true) } ?: emptyArray()

                        for (apk in apkFiles) {

                            var pkg = ""

                            try {

                                val pi = packageManager.getPackageArchiveInfo(apk.absolutePath, 0)

                                if (pi != null && !pi.packageName.isNullOrBlank()) {

                                    pkg = pi.packageName

                                }

                            } catch (e: Throwable) {}

                            allFiles.add(

                                UiFileItem(

                                    name = apk.name,

                                    sizeText = formatBytes(apk.length()),

                                    sizeBytes = apk.length(),

                                    category = FileCategory.APP,

                                    uri = Uri.fromFile(apk),

                                    file = apk,

                                    isInstalledApp = false,

                                    groupName = "Not Installed",

                                    packageName = pkg

                                )

                            )

                        }

                    } catch (e: Throwable) {}

                }



                // 2. Quét Hình ảnh

                val realPhotos = withContext(Dispatchers.IO) {

                    scanner.scanCategory(FileCategory.PHOTO)

                }

                realPhotos.forEach { item ->

                    allFiles.add(

                        UiFileItem(

                            name = item.name,

                            sizeText = formatBytes(item.size),

                            sizeBytes = item.size,

                            category = FileCategory.PHOTO,

                            uri = item.uri,

                            file = item.file,

                            groupName = "Today"

                        )

                    )

                }



                // 3. Quét Video

                val realVideos = withContext(Dispatchers.IO) {

                    scanner.scanCategory(FileCategory.VIDEO)

                }

                realVideos.forEach { item ->

                    allFiles.add(

                        UiFileItem(

                            name = item.name,

                            sizeText = formatBytes(item.size),

                            sizeBytes = item.size,

                            category = FileCategory.VIDEO,

                            uri = item.uri,

                            file = item.file,

                            durationText = if (item.durationText.isNotBlank()) item.durationText else "00:15",

                            groupName = "Yesterday"

                        )

                    )

                }



                // 4. Quét Nhạc

                val realMusic = withContext(Dispatchers.IO) {

                    scanner.scanCategory(FileCategory.MUSIC)

                }

                realMusic.forEach { item ->

                    allFiles.add(

                        UiFileItem(

                            name = item.name,

                            sizeText = formatBytes(item.size),

                            sizeBytes = item.size,

                            category = FileCategory.MUSIC,

                            uri = item.uri,

                            file = item.file,

                            durationText = item.durationText

                        )

                    )

                }



                // 5. Quét Tài liệu

                val realDocs = withContext(Dispatchers.IO) {

                    scanner.scanCategory(FileCategory.DOCUMENT)

                }

                realDocs.forEach { item ->

                    allFiles.add(

                        UiFileItem(

                            name = item.name,

                            sizeText = formatBytes(item.size),

                            sizeBytes = item.size,

                            category = FileCategory.DOCUMENT,

                            uri = item.uri,

                            file = item.file

                        )

                    )

                }



                // 6. Quét Danh bạ (Contacts)

                if (ContextCompat.checkSelfPermission(this@SendActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {

                    val contacts = withContext(Dispatchers.IO) {

                        loadContactsFromDevice()

                    }

                    allFiles.addAll(contacts)

                }



                // Update subtitle counts in Files Dashboard

                val zipCount = allFiles.count { it.name.endsWith(".zip", true) || it.name.endsWith(".rar", true) }

                binding.layoutFilesDashboardView.tvZipSubtitle.text = "$zipCount files"



                val largeCount = allFiles.count { it.sizeBytes > 50 * 1024 * 1024L }

                binding.layoutFilesDashboardView.tvLargeSubtitle.text = "$largeCount files"



                // Pre-categorize all files for instant O(1) tab switching and smooth swipe animations

                cachedAppsInstalled.clear()

                cachedAppsNotInstalled.clear()

                cachedPhotos.clear()

                cachedVideos.clear()

                cachedMusic.clear()

                cachedContacts.clear()

                cachedRecentReceived.clear()

                cachedRecentSent.clear()



                for (item in allFiles) {

                    when (item.category) {

                        FileCategory.APP -> {

                            if (item.isInstalledApp) cachedAppsInstalled.add(item)

                            else cachedAppsNotInstalled.add(item)

                        }

                        FileCategory.PHOTO -> cachedPhotos.add(item)

                        FileCategory.VIDEO -> cachedVideos.add(item)

                        FileCategory.MUSIC -> cachedMusic.add(item)

                        FileCategory.DOCUMENT -> {

                            if (item.subType.isNotEmpty()) cachedContacts.add(item)

                        }

                        else -> {}

                    }

                }



            } catch (e: Throwable) {

                e.printStackTrace()

            }



            if (isSearchActive && currentSearchQuery.isNotBlank()) {

                performSearch(currentSearchQuery)

            } else {

                filterDataForCurrentView()

            }

            updateSelectedSummary()

        }

    }



    private fun loadContactsFromDevice(): List<UiFileItem> {

        val list = mutableListOf<UiFileItem>()

        try {

            val projection = arrayOf(

                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,

                ContactsContract.CommonDataKinds.Phone.NUMBER

            )

            val cursor: Cursor? = contentResolver.query(

                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,

                projection,

                null,

                null,

                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

            )

            cursor?.use { c ->

                val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                val seenNames = mutableSetOf<String>()



                while (c.moveToNext()) {

                    val name = if (nameIndex >= 0) c.getString(nameIndex) ?: "" else ""

                    val number = if (numberIndex >= 0) c.getString(numberIndex) ?: "" else ""

                    if (name.isNotBlank() && !seenNames.contains(name)) {

                        seenNames.add(name)

                        list.add(

                            UiFileItem(

                                name = name,

                                sizeText = number,

                                sizeBytes = 1024L,

                                category = FileCategory.DOCUMENT,

                                subType = number,

                                groupName = "Contacts"

                            )

                        )

                    }

                }

            }

        } catch (e: Throwable) {

            e.printStackTrace()

        }

        return list

    }



    private fun updateSelectedSummary() {

        val selected = allFiles.filter { it.isSelected }

        val totalBytes = selected.sumOf { it.sizeBytes }

        val count = selected.size



        binding.tvSelectedCount.text = getString(R.string.selected_count_format, count)

        binding.tvSelectedSize.text = getString(R.string.selected_total_size, formatBytes(totalBytes))

    }



    private fun formatBytes(bytes: Long): String {

        val mb = bytes / (1024.0 * 1024.0)

        return if (mb >= 1024) {

            String.format(Locale.US, "%.2f GB", mb / 1024.0)

        } else if (mb >= 0.1) {

            String.format(Locale.US, "%.1f MB", mb)

        } else {

            val kb = bytes / 1024.0

            String.format(Locale.US, "%.0f KB", kb)

        }

    }



    private fun onFilesSelected() {

        val selected = allFiles.filter { it.isSelected }

        if (selected.isEmpty()) {

            Toast.makeText(this, getString(R.string.toast_select_at_least_one), Toast.LENGTH_SHORT).show()

            return

        }



        val fileNames = ArrayList(selected.map { it.name })

        val totalBytes = selected.sumOf { it.sizeBytes }

        com.ushareit.core.analytics.FirebaseAnalyticsManager.logTransferSessionStart(fileNames.size, totalBytes)



        val fileInfos = ArrayList(selected.map { item ->

            // If contact, generate temporary .vcf

            var path = item.file?.absolutePath

            var uriStr = item.uri?.toString()

            if (item.subType.isNotEmpty() && item.file == null) {

                try {

                    val vcf = File(cacheDir, "${item.name.replace(" ", "_")}.vcf")

                    vcf.writeText("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:${item.name}\r\nTEL:${item.subType}\r\nEND:VCARD\r\n")

                    path = vcf.absolutePath

                    uriStr = Uri.fromFile(vcf).toString()

                } catch (e: Throwable) {}

            }



            TransferFileInfo(

                name = if (item.subType.isNotEmpty() && !item.name.endsWith(".vcf")) "${item.name}.vcf" else item.name,

                size = item.sizeBytes,

                mimeType = when (item.category) {

                    FileCategory.PHOTO -> "image/*"

                    FileCategory.VIDEO -> "video/*"

                    FileCategory.MUSIC -> "audio/*"

                    FileCategory.APP -> "application/vnd.android.package-archive"

                    else -> if (item.subType.isNotEmpty()) "text/x-vcard" else "*/*"

                },

                uriString = uriStr,

                filePath = path

            )

        })



        val intent = Intent(this, DeviceDiscoveryActivity::class.java).apply {

            putStringArrayListExtra("SELECTED_FILE_NAMES", fileNames)

            putExtra("TOTAL_BYTES", totalBytes)

            putExtra("SELECTED_FILE_INFOS", fileInfos)

        }

        startActivity(intent)

    }



    private fun updateViewModeToggleIcon() {

        if (currentViewMode == VIEW_MODE_GRID) {

            binding.btnToggleViewMode.setImageResource(R.drawable.ic_view_list)

            binding.btnToggleViewMode.contentDescription = getString(R.string.view_mode_list)

        } else {

            binding.btnToggleViewMode.setImageResource(R.drawable.ic_view_grid)

            binding.btnToggleViewMode.contentDescription = getString(R.string.view_mode_grid)

        }

    }



    private fun toggleViewMode() {

        currentViewMode = if (currentViewMode == VIEW_MODE_GRID) VIEW_MODE_LIST else VIEW_MODE_GRID

        try {

            MMKV.defaultMMKV()?.encode(KEY_VIEW_MODE, currentViewMode)

        } catch (e: Throwable) {

            // ignore

        }

        updateViewModeToggleIcon()

        applyLayoutManagerForTab(currentTab)

        adapter.notifyDataSetChanged()

    }



    @Deprecated("Deprecated in Java")

    override fun onBackPressed() {

        if (isSearchActive) {

            closeSearch()

        } else if (currentTab == ActiveTab.FILES && binding.layoutFilesDashboardView.root.visibility == View.GONE) {

            filterDataForCurrentView(null)

        } else {

            super.onBackPressed()

        }

    }



    private fun handleBackPress() {

        if (isSearchActive) {

            closeSearch()

        } else if (currentTab == ActiveTab.FILES && binding.layoutFilesDashboardView.root.visibility == View.GONE) {

            filterDataForCurrentView(null)

        } else {

            finish()

        }

    }



    private fun openSearch() {

        isSearchActive = true

        binding.layoutNormalHeader.visibility = View.GONE

        binding.layoutSearchBar.visibility = View.VISIBLE

        binding.edtSearchQuery.requestFocus()

        showKeyboard(binding.edtSearchQuery)

        val query = binding.edtSearchQuery.text?.toString().orEmpty()

        if (query.isNotBlank()) {

            performSearch(query)

        }

    }



    private fun closeSearch() {

        isSearchActive = false

        currentSearchQuery = ""

        hideKeyboard()

        binding.edtSearchQuery.text?.clear()

        binding.layoutSearchBar.visibility = View.GONE

        binding.layoutNormalHeader.visibility = View.VISIBLE

        filterDataForCurrentView()

    }



    private fun showKeyboard(view: View) {

        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager

        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)

    }



    private fun hideKeyboard() {

        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager

        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)

    }



    private fun performSearch(query: String) {

        val trimmed = query.trim()

        currentSearchQuery = trimmed

        if (trimmed.isEmpty()) {

            filterDataForCurrentView()

            return

        }



        binding.layoutFilesDashboardView.root.visibility = View.GONE

        binding.layoutGroupHeaderBar.visibility = View.VISIBLE



        val currentTabItems = getCurrentTabItems()

        val matchesInTab = currentTabItems.filter { matchesQuery(it, trimmed) }



        displayedFiles.clear()

        if (matchesInTab.isNotEmpty()) {

            displayedFiles.addAll(matchesInTab)

            binding.tvGroupTitleCount.text = "${getString(R.string.search_results)} (${matchesInTab.size})"

        } else {

            val allMatches = allFiles.filter { matchesQuery(it, trimmed) }

            displayedFiles.addAll(allMatches)

            if (allMatches.isNotEmpty()) {

                binding.tvGroupTitleCount.text = "${getString(R.string.search_all_results)} (${allMatches.size})"

            } else {

                binding.tvGroupTitleCount.text = "${getString(R.string.search_results)} (0)"

            }

        }



        if (displayedFiles.isEmpty()) {
            binding.layoutEmptyView.tvEmptyText.text = getString(R.string.search_no_results_for, trimmed)
            binding.layoutEmptyView.root.visibility = View.VISIBLE
            binding.rvFiles.visibility = View.GONE
            updateEmptyStateNativeAd()
        } else {
            binding.layoutEmptyView.root.visibility = View.GONE
            binding.rvFiles.visibility = View.VISIBLE
        }



        adapter.notifyDataSetChanged()

        updateGroupSelectAllCheckbox()

    }



    private fun getCurrentTabItems(): List<UiFileItem> {

        return when (currentTab) {

            ActiveTab.APPS -> {

                val isInstalled = (subsegmentIndex == 1)

                allFiles.filter { it.category == FileCategory.APP && (it.isInstalledApp == isInstalled || allFiles.none { f -> f.category == FileCategory.APP && f.isInstalledApp == isInstalled }) }

            }

            ActiveTab.PHOTOS -> allFiles.filter { it.category == FileCategory.PHOTO }

            ActiveTab.VIDEOS -> allFiles.filter { it.category == FileCategory.VIDEO }

            ActiveTab.MUSIC -> allFiles.filter { it.category == FileCategory.MUSIC }

            ActiveTab.CONTACTS -> allFiles.filter { it.subType.isNotEmpty() && it.category == FileCategory.DOCUMENT }

            ActiveTab.RECENT -> allFiles.filter { it.category != FileCategory.APP }

            ActiveTab.FILES -> allFiles.filter { it.category == FileCategory.DOCUMENT || it.category == FileCategory.ZIP }

        }

    }



    private fun matchesQuery(item: UiFileItem, query: String): Boolean {

        if (item.name.contains(query, ignoreCase = true)) return true

        if (item.packageName.contains(query, ignoreCase = true)) return true

        if (item.subType.contains(query, ignoreCase = true)) return true

        return false

    }



    private fun shareSingleFile(item: UiFileItem) {

        CoroutineScope(Dispatchers.Main).launch {

            try {

                var shareUri: Uri? = null



                if (item.category == FileCategory.APP || item.packageName.isNotBlank() || item.name.endsWith(".apk", true)) {

                    val progressToast = Toast.makeText(this@SendActivity, getString(R.string.toast_preparing_share, item.name), Toast.LENGTH_SHORT)

                    progressToast.show()



                    shareUri = withContext(Dispatchers.IO) {

                        try {

                            val apkSource = item.file?.takeIf { it.exists() } ?: run {

                                if (item.packageName.isNotBlank()) {

                                    val appInfo = packageManager.getApplicationInfo(item.packageName, 0)

                                    File(appInfo.sourceDir).takeIf { it.exists() }

                                } else null

                            }



                            if (apkSource != null) {

                                val shareDir = File(cacheDir, "shared_apks").apply { mkdirs() }

                                val safeName = item.name.removeSuffix(".apk").replace(Regex("[^a-zA-Z0-9_.-]"), "_") + ".apk"

                                val destApk = File(shareDir, safeName)

                                if (!destApk.exists() || destApk.length() != apkSource.length()) {

                                    apkSource.inputStream().use { input ->

                                        destApk.outputStream().use { output ->

                                            input.copyTo(output)

                                        }

                                    }

                                }

                                FileProvider.getUriForFile(

                                    this@SendActivity,

                                    "${packageName}.fileprovider",

                                    destApk

                                )

                            } else null

                        } catch (e: Throwable) {

                            e.printStackTrace()

                            null

                        }

                    }

                } else if (item.uri != null && item.uri.scheme == "content") {

                    shareUri = item.uri

                } else if (item.file != null && item.file.exists()) {

                    shareUri = try {

                        FileProvider.getUriForFile(

                            this@SendActivity,

                            "${packageName}.fileprovider",

                            item.file

                        )

                    } catch (e: Throwable) {

                        withContext(Dispatchers.IO) {

                            try {

                                val shareDir = File(cacheDir, "shared_files").apply { mkdirs() }

                                val destFile = File(shareDir, item.name)

                                item.file.inputStream().use { input ->

                                    destFile.outputStream().use { output ->

                                        input.copyTo(output)

                                    }

                                }

                                FileProvider.getUriForFile(

                                    this@SendActivity,

                                    "${packageName}.fileprovider",

                                    destFile

                                )

                            } catch (e2: Throwable) {

                                null

                            }

                        }

                    }

                } else if (item.uri != null && item.uri.scheme == "file") {

                    val localFile = File(item.uri.path ?: "")

                    if (localFile.exists()) {

                        shareUri = try {

                            FileProvider.getUriForFile(

                                this@SendActivity,

                                "${packageName}.fileprovider",

                                localFile

                            )

                        } catch (e: Throwable) {

                            null

                        }

                    }

                }



                if (shareUri == null) {

                    Toast.makeText(this@SendActivity, getString(R.string.toast_cannot_share_file, item.name), Toast.LENGTH_SHORT).show()

                    return@launch

                }



                val mimeType = try {

                    contentResolver.getType(shareUri)

                } catch (e: Throwable) {

                    null

                } ?: when {

                    item.category == FileCategory.PHOTO -> "image/*"

                    item.category == FileCategory.VIDEO -> "video/*"

                    item.category == FileCategory.MUSIC -> "audio/*"

                    item.category == FileCategory.APP || item.name.endsWith(".apk", true) -> "application/vnd.android.package-archive"

                    item.name.endsWith(".pdf", true) -> "application/pdf"

                    item.name.endsWith(".zip", true) -> "application/zip"

                    item.name.endsWith(".rar", true) -> "application/x-rar-compressed"

                    item.name.endsWith(".doc", true) || item.name.endsWith(".docx", true) -> "application/msword"

                    item.name.endsWith(".xls", true) || item.name.endsWith(".xlsx", true) -> "application/vnd.ms-excel"

                    item.name.endsWith(".ppt", true) || item.name.endsWith(".pptx", true) -> "application/vnd.ms-powerpoint"

                    item.name.endsWith(".txt", true) -> "text/plain"

                    else -> "*/*"

                }



                val shareIntent = Intent(Intent.ACTION_SEND).apply {

                    type = mimeType

                    putExtra(Intent.EXTRA_STREAM, shareUri)

                    putExtra(Intent.EXTRA_SUBJECT, item.name)

                    putExtra(Intent.EXTRA_TEXT, "${getString(R.string.app_name)}: ${item.name}")

                    clipData = ClipData.newUri(contentResolver, item.name, shareUri)

                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                }



                val chooser = Intent.createChooser(shareIntent, "${getString(R.string.share_chooser_title)}: ${item.name}")

                try {

                    val resInfoList = packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)

                    for (resolveInfo in resInfoList) {

                        val targetPkg = resolveInfo.activityInfo.packageName

                        grantUriPermission(targetPkg, shareUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    }

                } catch (e: Throwable) {

                    // ignore

                }



                startActivity(chooser)

            } catch (e: Throwable) {

                Toast.makeText(this@SendActivity, getString(R.string.toast_share_error, e.message ?: ""), Toast.LENGTH_SHORT).show()

            }

        }

    }



    private fun loadAppIcon(item: UiFileItem, iv: ImageView) {

        try {

            val pm = packageManager

            if (item.packageName.isNotBlank()) {

                val icon = pm.getApplicationIcon(item.packageName)

                iv.setImageDrawable(icon)

            } else if (item.file != null && item.file.exists()) {

                val pInfo = pm.getPackageArchiveInfo(item.file.absolutePath, 0)

                val appInfo = pInfo?.applicationInfo

                if (appInfo != null) {

                    appInfo.sourceDir = item.file.absolutePath

                    appInfo.publicSourceDir = item.file.absolutePath

                    iv.setImageDrawable(appInfo.loadIcon(pm))

                } else {

                    iv.setImageResource(R.drawable.img_apk)

                }

            } else {

                iv.setImageResource(R.drawable.img_apk)

            }

        } catch (e: Throwable) {

            iv.setImageResource(R.drawable.img_apk)

        }

    }



    private fun getFileIconRes(item: UiFileItem): Int {

        return when {

            item.category == FileCategory.MUSIC -> R.drawable.img_music

            item.name.endsWith(".pdf", true) -> R.drawable.pdf

            item.name.endsWith(".xls", true) || item.name.endsWith(".xlsx", true) -> R.drawable.xls

            item.name.endsWith(".ppt", true) || item.name.endsWith(".pptx", true) -> R.drawable.ppt

            item.name.endsWith(".txt", true) -> R.drawable.txt

            item.name.endsWith(".doc", true) || item.name.endsWith(".docx", true) -> R.drawable.doc

            item.name.endsWith(".zip", true) || item.category == FileCategory.ZIP -> R.drawable.zip

            item.name.endsWith(".rar", true) -> R.drawable.rar

            else -> R.drawable.icon_document

        }

    }



    // ==========================================

    // RECYCLERVIEW ADAPTER (List Mode with Share vs 2-Column Grid Mode with Selection Tick)

    // ==========================================



    inner class ShareItFileAdapter(

        private val items: List<UiFileItem>,

        private val onSelectionChanged: () -> Unit

    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {



        private val TYPE_CONTACT = 1

        private val TYPE_GRID_2 = 2

        private val TYPE_LIST = 3

        private val TYPE_NATIVE_AD = 4



        private var nativeAd: com.google.android.gms.ads.nativead.NativeAd? = null



        fun setNativeAd(ad: com.google.android.gms.ads.nativead.NativeAd?) {

            nativeAd = ad

            notifyDataSetChanged()

        }



        private fun getAdPosition(): Int {

            // Hiển thị dưới 4 hàng file:

            // Chế độ lưới (2 cột): 4 hàng = 8 mục -> vị trí hiển thị là index 8

            // Chế độ danh sách (1 cột): 4 hàng = 4 mục -> vị trí hiển thị là index 4

            return if (currentViewMode == VIEW_MODE_GRID) 8 else 4

        }



        override fun getItemViewType(position: Int): Int {

            if (nativeAd != null && position == getAdPosition() && items.size >= getAdPosition()) {

                return TYPE_NATIVE_AD

            }

            if (currentTab == ActiveTab.CONTACTS) {

                return TYPE_CONTACT

            }

            return if (currentViewMode == VIEW_MODE_GRID) {

                TYPE_GRID_2

            } else {

                TYPE_LIST

            }

        }



        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

            val inflater = LayoutInflater.from(parent.context)

            return when (viewType) {

                TYPE_NATIVE_AD -> {

                    val adView = inflater.inflate(R.layout.layout_native_ad_card, parent, false) as com.google.android.gms.ads.nativead.NativeAdView

                    NativeAdViewHolder(adView)

                }

                TYPE_CONTACT -> {

                    val b = ItemContactRowShareitBinding.inflate(inflater, parent, false)

                    ContactRowViewHolder(b)

                }

                TYPE_GRID_2 -> {

                    val b = ItemFileGridCardBinding.inflate(inflater, parent, false)

                    GridCardViewHolder(b)

                }

                else -> {

                    val b = ItemFileRowShareitBinding.inflate(inflater, parent, false)

                    ListRowViewHolder(b)

                }

            }

        }



        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

            if (holder is NativeAdViewHolder) {

                nativeAd?.let { AdMediationManager.populateNativeAdView(it, holder.adView) }

                return

            }

            val adPos = getAdPosition()

            val itemIndex = if (nativeAd != null && items.size >= adPos && position > adPos) position - 1 else position

            if (itemIndex in items.indices) {

                val item = items[itemIndex]

                when (holder) {

                    is ContactRowViewHolder -> holder.bind(item)

                    is GridCardViewHolder -> holder.bind(item)

                    is ListRowViewHolder -> holder.bind(item)

                }

            }

        }



        override fun getItemCount(): Int {

            val adPos = getAdPosition()

            return if (nativeAd != null && items.size >= adPos) items.size + 1 else items.size

        }



        inner class NativeAdViewHolder(val adView: com.google.android.gms.ads.nativead.NativeAdView) : RecyclerView.ViewHolder(adView)



        // 1. CONTACT ROW

        inner class ContactRowViewHolder(val b: ItemContactRowShareitBinding) : RecyclerView.ViewHolder(b.root) {

            fun bind(item: UiFileItem) {

                b.tvContactName.text = item.name

                b.tvContactPhone.text = item.sizeText



                val firstLetter = item.name.trim().take(1).uppercase()

                if (firstLetter.isNotBlank() && firstLetter[0] in 'A'..'Z') {

                    b.tvAvatarLetter.visibility = View.VISIBLE

                    b.tvAvatarLetter.text = firstLetter

                    b.tvAvatarLetter.setBackgroundColor(Color.parseColor("#4C6EF5"))

                    b.ivContactAvatar.visibility = View.GONE

                } else {

                    b.tvAvatarLetter.visibility = View.GONE

                    b.ivContactAvatar.visibility = View.VISIBLE

                    b.ivContactAvatar.setImageResource(R.drawable.contacts_default)

                }



                updateCheckState(item)



                itemView.setOnClickListener {

                    item.isSelected = !item.isSelected

                    updateCheckState(item)

                    onSelectionChanged()

                }



                b.ivCheckCircle.setOnClickListener {

                    item.isSelected = !item.isSelected

                    updateCheckState(item)

                    onSelectionChanged()

                }

            }



            private fun updateCheckState(item: UiFileItem) {

                b.ivCheckCircle.setImageResource(

                    if (item.isSelected) R.drawable.icon_selected else R.drawable.icon_unselected

                )

            }

        }



        // 2. DẠNG LƯỚI: 1 HÀNG CHIA 2 Ô VUÔNG (Bấm vào File hiển thị tick được chọn để gửi file đi)

        inner class GridCardViewHolder(val b: ItemFileGridCardBinding) : RecyclerView.ViewHolder(b.root) {

            fun bind(item: UiFileItem) {

                val cleanName = if (item.category == FileCategory.APP) item.name.removeSuffix(".apk") else item.name

                b.tvFileName.text = cleanName

                b.tvFileInfo.text = item.sizeText



                when (item.category) {

                    FileCategory.PHOTO, FileCategory.VIDEO -> {

                        if (item.category == FileCategory.VIDEO && item.durationText.isNotBlank()) {

                            b.tvDurationBadge.text = item.durationText

                            b.tvDurationBadge.visibility = View.VISIBLE

                        } else {

                            b.tvDurationBadge.visibility = View.GONE

                        }

                        Glide.with(itemView.context)

                            .load(item.uri ?: item.file)

                            .centerCrop()

                            .placeholder(if (item.category == FileCategory.VIDEO) R.drawable.img_video else R.drawable.img_empty)

                            .into(b.ivFileThumb)

                    }

                    FileCategory.APP -> {

                        b.tvDurationBadge.visibility = View.GONE

                        loadAppIcon(item, b.ivFileThumb)

                    }

                    else -> {

                        b.tvDurationBadge.visibility = View.GONE

                        b.ivFileThumb.setImageResource(getFileIconRes(item))

                    }

                }



                updateCheckState(item)



                // Bấm vào File hiển thị tick được chọn để gửi file đi

                itemView.setOnClickListener {

                    item.isSelected = !item.isSelected

                    updateCheckState(item)

                    onSelectionChanged()

                }



                b.ivCheckCircle.setOnClickListener {

                    item.isSelected = !item.isSelected

                    updateCheckState(item)

                    onSelectionChanged()

                }

            }



            private fun updateCheckState(item: UiFileItem) {

                b.ivCheckCircle.setImageResource(

                    if (item.isSelected) R.drawable.icon_selected else R.drawable.icon_unselected

                )

                b.viewSelectedOverlay.visibility = if (item.isSelected) View.VISIBLE else View.GONE

            }

        }



        // 3. DẠNG LIST: CÓ Ô TÍCH CHỌN FILE, CUỐI DÒNG CÓ ICON SHARE (Bấm share gửi qua MXH)

        inner class ListRowViewHolder(val b: ItemFileRowShareitBinding) : RecyclerView.ViewHolder(b.root) {

            fun bind(item: UiFileItem) {

                val cleanName = if (item.category == FileCategory.APP) item.name.removeSuffix(".apk") else item.name

                b.tvFileName.text = cleanName



                val infoDetail = when {

                    item.category == FileCategory.APP && item.packageName.isNotBlank() ->

                        "${item.packageName} • ${item.sizeText}"

                    item.category == FileCategory.VIDEO && item.durationText.isNotBlank() ->

                        "${item.sizeText} • ${item.durationText}"

                    item.subType.isNotBlank() ->

                        "${item.sizeText} • ${item.subType}"

                    else -> item.sizeText

                }

                b.tvFileInfo.text = infoDetail



                when (item.category) {

                    FileCategory.PHOTO, FileCategory.VIDEO -> {

                        Glide.with(itemView.context)

                            .load(item.uri ?: item.file)

                            .centerCrop()

                            .placeholder(if (item.category == FileCategory.VIDEO) R.drawable.img_video else R.drawable.img_empty)

                            .into(b.ivFileIcon)

                    }

                    FileCategory.APP -> {

                        loadAppIcon(item, b.ivFileIcon)

                    }

                    else -> {

                        b.ivFileIcon.setImageResource(getFileIconRes(item))

                    }

                }



                updateCheckState(item)



                // Ô tích chọn file hoặc chạm vào dòng: chọn file để gửi P2P

                itemView.setOnClickListener {

                    item.isSelected = !item.isSelected

                    updateCheckState(item)

                    onSelectionChanged()

                }



                b.ivCheckCircle.setOnClickListener {

                    item.isSelected = !item.isSelected

                    updateCheckState(item)

                    onSelectionChanged()

                }



                // Cuối dòng có Icon Share: bấm share gửi file dưới dạng share cho tất cả các nền tảng mạng xã hội

                b.btnShareItem.setOnClickListener {

                    shareSingleFile(item)

                }

            }



            private fun updateCheckState(item: UiFileItem) {

                b.ivCheckCircle.setImageResource(

                    if (item.isSelected) R.drawable.icon_selected else R.drawable.icon_unselected

                )

            }

        }

    }

}

