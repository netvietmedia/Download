package com.ushareit.core.downloader

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.nextai.sharenow.R
import com.tencent.mmkv.MMKV
import com.ushareit.core.analytics.FirebaseAnalyticsManager
import com.ushareit.core.subscription.BillingManager
import com.ushareit.ui.paywall.PaywallActivity
import java.io.BufferedReader
import java.io.InputStreamReader

object MonetizedDomainRegistry {

    private const val TAG = "MonetizedDomains"
    const val MAX_FREE_MONETIZED_VIDEOS = 3

    private const val KEY_MONETIZED_DOWNLOADED_LIST = "pref_monetized_downloaded_files_csv"
    private const val KEY_MONETIZED_OPENED_SET = "pref_monetized_opened_videos_csv"

    private val domainSet = HashSet<String>()
    private val domainStemSet = HashSet<String>()

    private val nonMonetizedMainstreamAllowlist = setOf(
        "google.com", "youtube.com", "youtu.be", "googlevideo.com", "ytimg.com",
        "facebook.com", "fb.watch", "fbcdn.net", "instagram.com", "cdninstagram.com",
        "tiktok.com", "tiktokcdn.com", "tiktokv.com", "byteoversea.com", "ibytedtos.com",
        "twitter.com", "x.com", "twimg.com", "vimeo.com", "vimeocdn.com",
        "dailymotion.com", "dmcdn.net", "amazon.com", "apple.com", "microsoft.com",
        "netflix.com", "spotify.com", "soundcloud.com", "pinterest.com", "reddit.com",
        "linkedin.com", "github.com", "wikipedia.org", "cloudflare.com", "akamaized.net"
    )

    private val monetizedCdnKeywords = listOf(
        "xhcdn.com", "xhstream.com", "xhopen.com", "xhtab.com", "xhaccess.com",
        "xhchannel.com", "xhfeed.com", "xhvid.com", "cdnsolutions.media", "sacdnssedge.com",
        "xhamster", "xvideos-cdn.com", "xvideos", "phncdn.com", "pornhub",
        "ypncdn.com", "youporn", "rtcdn.com", "redtube", "bcvcdn.com", "ahcdn.com",
        "redgifs.com", "spankbang", "eporner", "tnaflix", "xnxx-cdn.com", "xnxx",
        "missav", "faphouse", "chaturbate", "stripchat", "vlxx", "viet69",
        "sextop1", "javhd", "supjav", "jable", "123av", "njav", "rule34",
        "simpcity", "erome", "onlyfans", "livejasmin", "bongacams", "beeg",
        "txxx", "youjizz"
    )

    private val monetizedTlds = setOf("xxx", "sex", "porn", "adult", "desi")

    @Volatile
    private var isInitialized = false
    private var lastPaywallShownTime = 0L

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                BillingManager.initBilling(context.applicationContext)
            } catch (_: Exception) {}
            try {
                context.assets.open("monetized_domains.txt").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                        lines.forEach { rawLine ->
                            val clean = cleanDomainString(rawLine)
                            if (clean.isNotEmpty() && !clean.startsWith("#") && !isMainstreamAllowlisted(clean)) {
                                domainSet.add(clean)
                                extractDomainStem(clean)?.let { stem ->
                                    if (stem.length >= 5) {
                                        domainStemSet.add(stem)
                                    }
                                }
                            }
                        }
                    }
                }
                domainSet.addAll(monetizedCdnKeywords)
                isInitialized = true
                Log.i(TAG, "Initialized ${domainSet.size} domains and ${domainStemSet.size} stems from assets/monetized_domains.txt")
            } catch (e: Exception) {
                Log.w(TAG, "Fallback domain list used", e)
                initFallback()
            }
        }
    }

    private fun initFallback() {
        val fallbackDomains = listOf(
            "pornhub.com", "pornhub.org", "xvideos.com", "xvideos.es", "xhamster.com", "xhamsterlive.com",
            "xhamster19.com", "xhamster46.desi", "xhamster.desi", "xnxx.com", "xnxx2.com", "xnxx.es",
            "spankbang.com", "eporner.com", "chaturbate.com", "stripchat.com", "stripchatgirls.com",
            "livejasmin.com", "onlyfans.com", "erome.com", "faphouse.com", "faphouse2.com", "xhaccess.com",
            "dmm.co.jp", "noodlemagazine.com", "ixxx.com", "simpcity.cr", "tnaflix.com", "qorno.com",
            "missav.ws", "missav.ai", "missav.live", "cityheaven.net", "marzaent.com", "sowve.com",
            "wvdme.com", "rule34.xxx", "viiukuhe.com", "xgroovy.com", "youporn.com", "pimpbunny.com",
            "njavtv.com", "nn125.com", "pawchive.pw", "thequizgo.com", "beeg.com", "theporndude.com",
            "gigglemagnetismunaired.com", "redgifs.com", "dlsite.com", "redtube.com", "youjizz.com", "txxx.com"
        )
        domainSet.addAll(fallbackDomains)
        domainSet.addAll(monetizedCdnKeywords)
        fallbackDomains.forEach { d ->
            extractDomainStem(d)?.takeIf { it.length >= 5 }?.let { domainStemSet.add(it) }
        }
        isInitialized = true
    }

    private fun isMainstreamAllowlisted(host: String): Boolean {
        if (nonMonetizedMainstreamAllowlist.contains(host)) return true
        return nonMonetizedMainstreamAllowlist.any { host.endsWith(".$it") }
    }

    private fun extractDomainStem(host: String): String? {
        val parts = host.split(".")
        if (parts.size < 2) return null
        val sld = if (parts.size >= 3 && parts[parts.size - 2].length <= 3 && parts.last().length == 2) {
            // e.g. dmm.co.jp -> dmm
            parts[parts.size - 3]
        } else {
            parts[parts.size - 2]
        }
        val stripped = sld.replace(Regex("\\d+$"), "")
        return stripped.ifEmpty { sld }
    }

    fun isMonetizedDomain(urlOrHost: String?): Boolean {
        if (urlOrHost.isNullOrBlank()) return false
        val cleanHost = extractHost(urlOrHost)
        if (cleanHost.isNotEmpty() && isMainstreamAllowlisted(cleanHost)) return false

        val lower = urlOrHost.lowercase()
        if (monetizedCdnKeywords.any { lower.contains(it) }) return true

        if (cleanHost.isEmpty()) return false
        if (domainSet.contains(cleanHost)) return true

        val parts = cleanHost.split(".")
        if (parts.size >= 2) {
            val tld = parts.last()
            if (monetizedTlds.contains(tld)) return true

            for (i in 0 until parts.size - 1) {
                val candidate = parts.drop(i).joinToString(".")
                if (domainSet.contains(candidate)) return true
            }

            val stem = extractDomainStem(cleanHost)
            if (!stem.isNullOrEmpty() && domainStemSet.contains(stem)) {
                return true
            }
        }
        return false
    }

    fun registerMonetizedDownload(
        fileName: String,
        refererUrl: String?,
        downloadUrl: String?,
        activePageUrl: String? = null
    ): Boolean {
        val isMonetized = isMonetizedDomain(refererUrl) ||
            isMonetizedDomain(downloadUrl) ||
            isMonetizedDomain(activePageUrl)
        if (!isMonetized) return false

        val cleanName = fileName.trim().ifEmpty { downloadUrl ?: "unknown_video" }
        try {
            val list = getDownloadedMonetizedFiles().toMutableList()
            if (!list.contains(cleanName)) {
                list.add(cleanName)
                MMKV.defaultMMKV().encode(KEY_MONETIZED_DOWNLOADED_LIST, list.joinToString("|"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error saving monetized file record: ${e.message}")
        }
        return true
    }

    fun unregisterMonetizedDownload(fileName: String) {
        val cleanName = fileName.trim()
        if (cleanName.isEmpty()) return
        try {
            val list = getDownloadedMonetizedFiles().toMutableList()
            if (list.remove(cleanName)) {
                MMKV.defaultMMKV().encode(KEY_MONETIZED_DOWNLOADED_LIST, list.joinToString("|"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing monetized file record: ${e.message}")
        }
    }

    /**
     * Kiểm tra xem người dùng có được phép tải thêm video mới từ danh sách 1,822 website hay không.
     * Cho phép tải miễn phí 3 video đầu tiên.
     * Từ video thứ 4 trở đi: trả về false để bật Payment Wall yêu cầu đăng ký gói VIP Sub.
     */
    fun canDownloadMonetizedVideo(
        refererUrl: String? = null,
        downloadUrl: String? = null,
        activePageUrl: String? = null
    ): Boolean {
        if (BillingManager.isVipUser()) return true
        val isMonetized = isMonetizedDomain(refererUrl) ||
            isMonetizedDomain(downloadUrl) ||
            isMonetizedDomain(activePageUrl)
        if (!isMonetized) return true

        val usedCount = maxOf(getDownloadedMonetizedFiles().size, getOpenedMonetizedVideos().size)
        if (usedCount >= MAX_FREE_MONETIZED_VIDEOS) {
            val targetKey = (downloadUrl ?: refererUrl ?: activePageUrl ?: "monetized_video").take(120)
            FirebaseAnalyticsManager.logBrowserMonetizedPaywallShow(targetKey, usedCount)
            return false
        }
        return true
    }

    fun isMonetizedVideoFile(
        fileName: String,
        refererUrl: String? = null,
        downloadUrl: String? = null,
        activePageUrl: String? = null
    ): Boolean {
        if (isMonetizedDomain(refererUrl) || isMonetizedDomain(downloadUrl) || isMonetizedDomain(activePageUrl)) return true
        val cleanName = fileName.trim()
        if (cleanName.isEmpty()) return false
        return getDownloadedMonetizedFiles().contains(cleanName)
    }

    /**
     * Cho phép mở xem 3 video đầu tiên tải từ danh sách 1,822 website.
     * Từ video thứ 4 trở đi: trả về false để bật Payment Wall yêu cầu đăng ký gói VIP Sub.
     */
    fun canOpenAndConsumeMonetizedVideo(
        fileNameOrKey: String,
        refererUrl: String? = null,
        downloadUrl: String? = null,
        activePageUrl: String? = null
    ): Boolean {
        if (BillingManager.isVipUser()) return true
        if (!isMonetizedVideoFile(fileNameOrKey, refererUrl, downloadUrl, activePageUrl)) return true

        val cleanKey = fileNameOrKey.trim().ifEmpty { downloadUrl ?: "unknown_video" }
        val downloadedList = getDownloadedMonetizedFiles()
        val openedList = getOpenedMonetizedVideos().toMutableList()

        // Nếu video nằm ở vị trí thứ 4 trở đi trong danh sách các video đã tải từ web monetized (index >= 3)
        val downloadIndex = downloadedList.indexOf(cleanKey)
        if (downloadIndex >= MAX_FREE_MONETIZED_VIDEOS) {
            FirebaseAnalyticsManager.logBrowserMonetizedPaywallShow(cleanKey, maxOf(openedList.size, downloadedList.size))
            return false
        }

        // Nếu đã tải đủ >= 3 video miễn phí mà video này không nằm trong 3 video đầu tiên
        if (downloadedList.size >= MAX_FREE_MONETIZED_VIDEOS &&
            downloadIndex !in 0 until MAX_FREE_MONETIZED_VIDEOS &&
            !openedList.contains(cleanKey)
        ) {
            FirebaseAnalyticsManager.logBrowserMonetizedPaywallShow(cleanKey, downloadedList.size)
            return false
        }

        // Nếu đã nằm trong 3 video đầu tiên đã được mở trước đó -> cho phép mở lại
        if (openedList.contains(cleanKey)) {
            FirebaseAnalyticsManager.logBrowserMonetizedVideoOpen(
                cleanKey,
                openedList.size,
                (MAX_FREE_MONETIZED_VIDEOS - openedList.size).coerceAtLeast(0)
            )
            return true
        }

        // Nếu chưa mở đủ 3 video đầu tiên -> ghi nhận và cho phép mở
        if (openedList.size < MAX_FREE_MONETIZED_VIDEOS) {
            openedList.add(cleanKey)
            saveOpenedMonetizedVideos(openedList)
            FirebaseAnalyticsManager.logBrowserMonetizedVideoOpen(
                cleanKey,
                openedList.size,
                (MAX_FREE_MONETIZED_VIDEOS - openedList.size).coerceAtLeast(0)
            )
            return true
        }

        // Từ video thứ 4 trở đi -> Chặn và bật Payment Wall
        FirebaseAnalyticsManager.logBrowserMonetizedPaywallShow(cleanKey, openedList.size)
        return false
    }

    fun getOpenedMonetizedCount(): Int {
        return getOpenedMonetizedVideos().size
    }

    fun getDownloadedMonetizedCount(): Int {
        return getDownloadedMonetizedFiles().size
    }

    fun getRemainingFreeMonetizedOpens(): Int {
        if (BillingManager.isVipUser()) return Int.MAX_VALUE
        val used = maxOf(getOpenedMonetizedVideos().size, getDownloadedMonetizedFiles().size)
        return (MAX_FREE_MONETIZED_VIDEOS - used).coerceAtLeast(0)
    }

    fun launchPaywallForMonetizedLimit(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPaywallShownTime < 1500L) return
        lastPaywallShownTime = now

        Toast.makeText(
            context,
            context.getString(R.string.browser_paywall_monetized_limit_toast),
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(context, PaywallActivity::class.java).apply {
            putExtra("EXTRA_REASON", "monetized_limit_reached")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    }

    private fun getDownloadedMonetizedFiles(): List<String> {
        return try {
            val raw = MMKV.defaultMMKV().decodeString(KEY_MONETIZED_DOWNLOADED_LIST, "") ?: ""
            if (raw.isBlank()) emptyList() else raw.split("|").filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getOpenedMonetizedVideos(): List<String> {
        return try {
            val raw = MMKV.defaultMMKV().decodeString(KEY_MONETIZED_OPENED_SET, "") ?: ""
            if (raw.isBlank()) emptyList() else raw.split("|").filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveOpenedMonetizedVideos(list: List<String>) {
        try {
            MMKV.defaultMMKV().encode(KEY_MONETIZED_OPENED_SET, list.distinct().joinToString("|"))
        } catch (_: Exception) {}
    }

    fun extractHost(raw: String): String {
        var str = raw.trim().lowercase()
        str = str.replace("http://", "").replace("https://", "")
        str = str.substringBefore("/").substringBefore("?").substringBefore(":")
        if (str.startsWith("www.")) {
            str = str.substring(4)
        }
        return str.trim()
    }

    private fun cleanDomainString(raw: String): String {
        return raw.trim().lowercase()
            .replace("http://", "")
            .replace("https://", "")
            .replace("www.", "")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore(":")
            .trim()
    }
}
