package com.ushareit.core.downloader

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Multi-Platform Universal Video Sniffer Engine (SnifferEngine)
 * Supports Facebook, TikTok (No-Watermark), Instagram, YouTube, Twitter/X, Vimeo, Dailymotion,
 * XNXX/XVideos/Pornhub/1822+ Monetized Domains, and Universal HTML5/HLS/MP4 extraction on ALL websites.
 */
class SnifferEngine {

    companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; SM-A075F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36"

        fun sanitizeMediaUrl(raw: String): String {
            var url = raw.trim()
            url = url.replace("\\/", "/")
            url = url.replace("\\u0026", "&")
            url = url.replace("\\u0025", "%")
            url = url.replace("\\u003D", "=")
            url = url.replace("\\u003d", "=")
            url = url.replace("&amp;", "&")
            url = url.replace("u0026", "&")
            url = url.replace("u00253D", "%3D")
            url = url.replace("u00253d", "%3D")
            return url
        }

        fun stripByteRangeParams(rawUrl: String): String {
            var cleaned = sanitizeMediaUrl(rawUrl)
                .replace(Regex("([?&])bytestart=\\d+&?"), "$1")
                .replace(Regex("([?&])byteend=\\d+&?"), "$1")
                .replace("?&", "?")
                .trimEnd('&', '?')
            return cleaned
        }

        private val metaAudioByVideoId = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val metaAudioByXpvId = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val recentMetaAudioUrls = java.util.concurrent.CopyOnWriteArrayList<Pair<Long, String>>()

        data class MetaEfgInfo(
            val videoId: String = "",
            val xpvAssetId: String = "",
            val vencodeTag: String = "",
            val isAudioOnly: Boolean = false,
            val isDashVideoWithoutAudio: Boolean = false,
            val durationSec: Int = 0,
            val bitrate: Long = 0L,
            val qualityRank: Int = 0,
            val qualityHint: String? = null
        )

        fun parseMetaEfgInfo(url: String): MetaEfgInfo {
            try {
                val efgRaw = when {
                    url.contains("efg=") -> url.substringAfter("efg=").substringBefore('&')
                    else -> ""
                }
                if (efgRaw.isNotEmpty()) {
                    val urlDecoded = java.net.URLDecoder.decode(efgRaw, "UTF-8")
                    val decodedBytes = try {
                        android.util.Base64.decode(
                            urlDecoded,
                            android.util.Base64.URL_SAFE or android.util.Base64.DEFAULT
                        )
                    } catch (_: Exception) {
                        android.util.Base64.decode(urlDecoded, android.util.Base64.DEFAULT)
                    }
                    val efgJson = String(decodedBytes, Charsets.UTF_8)
                    val lowerEfg = efgJson.lowercase()
                    val obj = JSONObject(efgJson)
                    val vencodeTag = obj.optString("vencode_tag", "").lowercase()
                    val rawVid = obj.optString("video_id", "")
                        .ifEmpty { obj.optLong("video_id", 0L).takeIf { it > 0L }?.toString() ?: "" }
                    val rawXpv = obj.optString("xpv_asset_id", "")
                        .ifEmpty { obj.optLong("xpv_asset_id", 0L).takeIf { it > 0L }?.toString() ?: "" }
                    val videoId = rawVid.ifEmpty { rawXpv }
                    val durationSec = obj.optInt("duration_s", 0)
                    val bitrate = obj.optLong("bitrate", 0L)

                    val isAudio = vencodeTag.contains("audio") ||
                        vencodeTag.contains("heaac") ||
                        vencodeTag.contains("aac") ||
                        vencodeTag.contains("opus") ||
                        lowerEfg.contains("_audio")

                    val isDashVideoOnly = !isAudio && (
                        vencodeTag.startsWith("dash_") ||
                            vencodeTag.contains("_dash") ||
                            vencodeTag.contains("vp9") ||
                            vencodeTag.contains("av1") ||
                            vencodeTag.contains("h264-basic") ||
                            vencodeTag.contains("baseline_")
                        ) && !vencodeTag.contains("progressive")

                    val qualityRank = when {
                        isAudio -> 1
                        vencodeTag.contains("1080") || lowerEfg.contains("1080p") -> 1080
                        vencodeTag.contains("720") || lowerEfg.contains("720p") || vencodeTag.contains("hd") -> 720
                        vencodeTag.contains("540") || lowerEfg.contains("540p") -> 540
                        vencodeTag.contains("480") || lowerEfg.contains("480p") -> 480
                        vencodeTag.contains("360") || lowerEfg.contains("360p") || vencodeTag.contains("baseline") -> 360
                        else -> 400
                    }

                    val qualityHint = when {
                        vencodeTag.contains("1080") || lowerEfg.contains("1080p") -> "1080p Full HD MP4"
                        vencodeTag.contains("720") || lowerEfg.contains("720p") || vencodeTag.contains("hd") -> "720p HD MP4"
                        vencodeTag.contains("540") || vencodeTag.contains("480") || lowerEfg.contains("480p") -> "480p SD MP4"
                        vencodeTag.contains("360") || lowerEfg.contains("360p") || vencodeTag.contains("baseline") -> "360p Standard MP4"
                        isAudio -> "Audio Only (.MP3)"
                        else -> null
                    }
                    return MetaEfgInfo(
                        videoId = videoId,
                        xpvAssetId = rawXpv,
                        vencodeTag = vencodeTag,
                        isAudioOnly = isAudio,
                        isDashVideoWithoutAudio = isDashVideoOnly,
                        durationSec = durationSec,
                        bitrate = bitrate,
                        qualityRank = qualityRank,
                        qualityHint = qualityHint
                    )
                }
            } catch (_: Exception) {}
            return MetaEfgInfo()
        }

        fun registerMetaStream(rawUrl: String): MetaEfgInfo {
            val cleanUrl = stripByteRangeParams(rawUrl)
            val info = parseMetaEfgInfo(cleanUrl)
            if (info.isAudioOnly && cleanUrl.startsWith("http")) {
                if (info.videoId.isNotEmpty()) {
                    metaAudioByVideoId[info.videoId] = cleanUrl
                }
                if (info.xpvAssetId.isNotEmpty()) {
                    metaAudioByXpvId[info.xpvAssetId] = cleanUrl
                }
                recentMetaAudioUrls.add(System.currentTimeMillis() to cleanUrl)
                while (recentMetaAudioUrls.size > 120) {
                    recentMetaAudioUrls.removeAt(0)
                }
            }
            return info
        }

        fun findCompanionAudioUrl(videoUrl: String): String? {
            val info = parseMetaEfgInfo(videoUrl)
            if (info.videoId.isNotEmpty()) {
                metaAudioByVideoId[info.videoId]?.let { return it }
            }
            if (info.xpvAssetId.isNotEmpty()) {
                metaAudioByXpvId[info.xpvAssetId]?.let { return it }
            }
            val now = System.currentTimeMillis()
            val recent = recentMetaAudioUrls.lastOrNull { (ts, _) -> now - ts < 45_000L }?.second
            return recent
        }

        fun extractFacebookVideoId(pageUrl: String): String {
            val trimmed = pageUrl.trim()
            val candidate = when {
                trimmed.contains("/reel/") -> trimmed.substringAfter("/reel/").substringBefore('/').substringBefore('?').substringBefore('&')
                trimmed.contains("/videos/") -> trimmed.substringAfter("/videos/").substringBefore('/').substringBefore('?').substringBefore('&')
                trimmed.contains("v=") -> trimmed.substringAfter("v=").substringBefore('&').substringBefore('?').substringBefore('#')
                trimmed.contains("fullscreen_video/") -> trimmed.substringAfter("fullscreen_video/").substringBefore('?').substringBefore('&')
                else -> ""
            }
            return if (candidate.isNotEmpty() && candidate.all { it.isDigit() }) candidate else ""
        }

        private val FACEBOOK_RESERVED_PATHS = setOf(
            "watch", "reel", "reels", "videos", "photo", "photos", "login", "checkpoint",
            "recover", "groups", "marketplace", "events", "gaming", "messages", "notifications",
            "settings", "help", "privacy", "policies", "bookmarks", "friends", "stories", "share"
        )

        private val INSTAGRAM_RESERVED_PATHS = setOf(
            "reel", "reels", "p", "tv", "stories", "explore", "direct", "accounts", "about", "legal", "developer"
        )

        private val YOUTUBE_RESERVED_PATHS = setOf(
            "watch", "shorts", "playlist", "feed", "results", "gaming", "music", "premium", "account", "reporthistory", "embed"
        )

        fun isInstagramProfileUrl(rawUrl: String): Boolean {
            val trimmed = rawUrl.trim()
            if (!trimmed.startsWith("http", ignoreCase = true)) return false
            val lower = trimmed.lowercase()
            if (!lower.contains("instagram.com")) return false
            return try {
                val u = android.net.Uri.parse(trimmed)
                val segments = (u.path ?: "").trim('/').split('/').filter { it.isNotBlank() }
                if (segments.isEmpty()) return false
                val first = segments[0].lowercase()
                if (first in INSTAGRAM_RESERVED_PATHS) return false
                segments.size == 1 || (segments.size == 2 && segments[1].lowercase() in setOf("reels", "tagged", "channel"))
            } catch (_: Exception) {
                false
            }
        }

        fun toInstagramProfileReelsUrl(rawUrl: String): String {
            if (!isInstagramProfileUrl(rawUrl)) return rawUrl.trim()
            return try {
                val u = android.net.Uri.parse(rawUrl.trim())
                val segments = (u.path ?: "").trim('/').split('/').filter { it.isNotBlank() }
                val handle = segments.firstOrNull()?.removePrefix("@") ?: return rawUrl.trim()
                "https://www.instagram.com/$handle/reels/"
            } catch (_: Exception) {
                rawUrl.trim()
            }
        }

        fun isYouTubeChannelUrl(rawUrl: String): Boolean {
            val trimmed = rawUrl.trim()
            if (!trimmed.startsWith("http", ignoreCase = true)) return false
            val lower = trimmed.lowercase()
            if (!lower.contains("youtube.com")) return false
            return try {
                val u = android.net.Uri.parse(trimmed)
                val segments = (u.path ?: "").trim('/').split('/').filter { it.isNotBlank() }
                if (segments.isEmpty()) return false
                val first = segments[0].lowercase()
                if (first in YOUTUBE_RESERVED_PATHS) return false
                first.startsWith("@") || first in setOf("channel", "c", "user") ||
                    segments.size == 1 ||
                    (segments.size == 2 && segments[1].lowercase() in setOf("shorts", "videos", "streams", "featured"))
            } catch (_: Exception) {
                false
            }
        }

        fun toYouTubeChannelShortsUrl(rawUrl: String): String {
            if (!isYouTubeChannelUrl(rawUrl)) return rawUrl.trim()
            return try {
                val u = android.net.Uri.parse(rawUrl.trim())
                val segments = (u.path ?: "").trim('/').split('/').filter { it.isNotBlank() }
                if (segments.isEmpty()) return rawUrl.trim()
                val first = segments[0]
                when {
                    first.startsWith("@") -> "https://www.youtube.com/$first/shorts"
                    first.lowercase() in setOf("channel", "c", "user") && segments.size >= 2 -> {
                        "https://www.youtube.com/$first/${segments[1]}/shorts"
                    }
                    else -> "https://www.youtube.com/@$first/shorts"
                }
            } catch (_: Exception) {
                rawUrl.trim()
            }
        }

        fun isChannelOrPageBatchUrl(rawUrl: String): Boolean {
            return isFacebookPageOrProfileUrl(rawUrl) || isInstagramProfileUrl(rawUrl) || isYouTubeChannelUrl(rawUrl)
        }

        fun toChannelBatchTargetUrl(rawUrl: String): String {
            return when {
                isFacebookPageOrProfileUrl(rawUrl) -> toFacebookPageReelsUrl(rawUrl)
                isInstagramProfileUrl(rawUrl) -> toInstagramProfileReelsUrl(rawUrl)
                isYouTubeChannelUrl(rawUrl) -> toYouTubeChannelShortsUrl(rawUrl)
                else -> rawUrl.trim()
            }
        }

        fun buildDescriptiveVideoFileName(
            videoId: String,
            rawTitle: String? = null,
            rawDescription: String? = null
        ): String {
            val safeId = videoId.replace(Regex("[^A-Za-z0-9_-]"), "").ifEmpty { System.currentTimeMillis().toString() }
            val combined = listOfNotNull(rawDescription?.trim(), rawTitle?.trim())
                .filter { it.isNotEmpty() && !it.startsWith("Video by ", ignoreCase = true) }
                .joinToString("\n")

            if (combined.isBlank()) return "$safeId.mp4"

            val hashtagRegex = Regex("#[\\p{L}\\p{N}_]+")
            val tags = LinkedHashSet<String>()
            for (match in hashtagRegex.findAll(combined)) {
                tags.add(match.value)
            }

            val body = combined
                .replace(hashtagRegex, " ")
                .replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1F]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(85)
                .trim()

            val tagStr = tags.take(6).joinToString(" ").take(55).trim()
            val prefix = listOf(body, tagStr).filter { it.isNotEmpty() }.joinToString(" - ")
            return if (prefix.isNotEmpty()) "$prefix [$safeId].mp4" else "$safeId.mp4"
        }

        fun isFacebookPageOrProfileUrl(rawUrl: String): Boolean {
            val trimmed = rawUrl.trim()
            if (!trimmed.startsWith("http", ignoreCase = true)) return false
            val lower = trimmed.lowercase()
            if (!lower.contains("facebook.com") && !lower.contains("fb.com")) return false
            if (extractFacebookVideoId(trimmed).isNotEmpty()) return false
            return try {
                val u = android.net.Uri.parse(trimmed)
                val path = (u.path ?: "").trim('/')
                if (path.isEmpty()) return false
                if (path.equals("profile.php", ignoreCase = true)) {
                    return !u.getQueryParameter("id").isNullOrBlank()
                }
                val segments = path.split('/').filter { it.isNotBlank() }
                if (segments.isEmpty()) return false
                val first = segments[0].lowercase()
                if (first == "people" && segments.size >= 3) return true
                if (first in FACEBOOK_RESERVED_PATHS) return false
                segments.size == 1 || (segments.size == 2 && segments[1].lowercase() in setOf("reels", "videos", "about", "posts"))
            } catch (_: Exception) {
                false
            }
        }

        fun toFacebookPageReelsUrl(rawUrl: String): String {
            if (!isFacebookPageOrProfileUrl(rawUrl)) return normalizeFacebookUrl(rawUrl)
            return try {
                val u = android.net.Uri.parse(rawUrl.trim())
                val path = (u.path ?: "").trim('/')
                if (path.equals("profile.php", ignoreCase = true)) {
                    val id = u.getQueryParameter("id") ?: ""
                    return "https://www.facebook.com/profile.php?id=$id&sk=reels_tab"
                }
                val segments = path.split('/').filter { it.isNotBlank() }
                if (segments[0].equals("people", ignoreCase = true) && segments.size >= 3) {
                    return "https://www.facebook.com/profile.php?id=${segments[2]}&sk=reels_tab"
                }
                val slug = if (segments[0].equals("pg", ignoreCase = true) && segments.size >= 2) segments[1] else segments[0]
                "https://www.facebook.com/$slug/reels/"
            } catch (_: Exception) {
                normalizeFacebookUrl(rawUrl)
            }
        }

        fun normalizeFacebookUrl(pageUrl: String): String {
            val trimmed = pageUrl.trim()
            val videoId = extractFacebookVideoId(trimmed)
            return when {
                trimmed.contains("/reel/") && videoId.isNotEmpty() -> {
                    "https://www.facebook.com/reel/$videoId"
                }
                videoId.isNotEmpty() -> {
                    "https://www.facebook.com/watch/?v=$videoId"
                }
                isFacebookPageOrProfileUrl(trimmed) -> {
                    toFacebookPageReelsUrl(trimmed)
                }
                trimmed.startsWith("http") -> {
                    trimmed.replace("m.facebook.com", "www.facebook.com")
                }
                else -> trimmed
            }
        }

        fun getRefererForUrl(url: String, explicitReferer: String? = null): String? {
            if (!explicitReferer.isNullOrEmpty()) return explicitReferer
            val lower = url.lowercase()
            return when {
                lower.contains("fbcdn.net") || lower.contains("facebook.com") -> "https://www.facebook.com/"
                lower.contains("tiktokcdn") || lower.contains("tiktokv.com") || lower.contains("tiktok.com") || lower.contains("byteoversea") || lower.contains("ibytedtos") -> "https://www.tiktok.com/"
                lower.contains("instagram.com") || lower.contains("cdninstagram") -> "https://www.instagram.com/"
                lower.contains("twimg.com") || lower.contains("twitter.com") || lower.contains("x.com") -> "https://twitter.com/"
                lower.contains("googlevideo.com") || lower.contains("youtube.com") -> "https://www.youtube.com/"
                lower.contains("vimeo.com") || lower.contains("vimeocdn.com") -> "https://vimeo.com/"
                lower.contains("dailymotion.com") || lower.contains("dmcdn.net") -> "https://www.dailymotion.com/"
                lower.contains("xhamster") || lower.contains("xhcdn") || lower.contains("cdnsolutions.media") || lower.contains("ahcdn") -> "https://xhamster.com/"
                lower.contains("xvideos") || lower.contains("xvcdn") -> "https://www.xvideos.com/"
                lower.contains("pornhub") || lower.contains("phncdn") -> "https://www.pornhub.com/"
                lower.contains("redtube") -> "https://www.redtube.com/"
                else -> {
                    try {
                        val u = android.net.Uri.parse(url)
                        if (u.scheme != null && u.host != null) "${u.scheme}://${u.host}/" else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        private val AD_VIDEO_KEYWORDS = listOf(
            "exoclick", "exosrv", "tsyndicate", "trafficjunky", "juicyads",
            "popads", "popcash", "adsterra", "propellerads", "magsrv", "realsrv",
            "doubleclick", "googlesyndication", "googleadservices", "adservice", "adserver",
            "preroll", "midroll", "vast", "ima3", "bcvcdn.com/hlsm/", "aucdn.net",
            "stripchat", "chaturbate", "livejasmin", "bongacams", "flirtify", "faphouse",
            "cam4", "myfreecams", "jerkmate", "clickadu", "hilltopads", "bidgear",
            "galaksion", "acint.net", "etahub", "outbrain", "taboola", "mgid",
            "auct.xhamster", "collector.xhamster", "modusygunaciro",
            "thumb-", "flixcdn.com", "/thumbs/", "preview.mp4", "preview.m3u8",
            "thumb.mp4", "thumb.m3u8", ".sprite.", "/ads/", "ad_video", "promo_spot"
        )

        fun isAdOrBannerVideoUrl(url: String): Boolean {
            val lower = url.lowercase()
            if (AD_VIDEO_KEYWORDS.any { lower.contains(it) }) return true
            if (lower.contains("cdnsolutions.media") && lower.contains("thumb")) return true
            return false
        }
    }

    data class ProbedStreamInfo(
        val sizeBytes: Long,
        val durationSeconds: Int = 0,
        val isLikelyAd: Boolean = false,
        val hlsVariants: List<VideoQuality> = emptyList()
    )

    private val httpClient = OkHttpClient.Builder()
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                return okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { addr ->
                    if (addr is java.net.Inet4Address) 0 else 1
                }
            }
        })
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val probeHttpClient = httpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * 1. TikTok Video Extractor (No-Watermark HD MP4 + Original Audio MP3 + Direct Feed Stream Fallback)
     */
    suspend fun extractTikTokVideo(pageUrl: String, htmlContent: String? = null): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            var targetTikTokUrl = pageUrl.trim()
            if (!targetTikTokUrl.contains("/video/") && !targetTikTokUrl.contains("vt.tiktok.com") && !targetTikTokUrl.contains("vm.tiktok.com")) {
                if (!htmlContent.isNullOrBlank()) {
                    val videoPathMatcher = Pattern.compile("""/@[a-zA-Z0-9._-]+/video/(\d{15,22})""").matcher(htmlContent)
                    if (videoPathMatcher.find()) {
                        targetTikTokUrl = "https://www.tiktok.com${videoPathMatcher.group(0)}"
                    } else {
                        val idMatcher = Pattern.compile(""""(?:id|itemId|video_id)"\s*:\s*"(7\d{18})"""").matcher(htmlContent)
                        if (idMatcher.find()) {
                            targetTikTokUrl = "https://www.tiktok.com/@tiktok/video/${idMatcher.group(1)}"
                        }
                    }
                }
            }

            fun resolveTikWmUrl(raw: String): String {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return ""
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
                if (trimmed.startsWith("/")) return "https://www.tikwm.com$trimmed"
                return "https://www.tikwm.com/$trimmed"
            }

            if (targetTikTokUrl.contains("/video/") || targetTikTokUrl.contains("vt.tiktok.com") || targetTikTokUrl.contains("vm.tiktok.com") || targetTikTokUrl.contains("/t/")) {
                val apiUrl = "https://www.tikwm.com/api/?hd=1&url=" + URLEncoder.encode(targetTikTokUrl, "UTF-8")
                val req = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", "https://www.tikwm.com/")
                    .build()
                val res = httpClient.newCall(req).execute()
                val bodyStr = res.body?.string() ?: ""
                res.close()

                if (bodyStr.isNotEmpty()) {
                    val json = JSONObject(bodyStr)
                    if (json.optInt("code", -1) == 0 && json.optJSONObject("data") != null) {
                        val data = json.getJSONObject("data")
                        val rawTitle = data.optString("title", "TikTok_Video").takeIf { it.isNotBlank() } ?: "TikTok_Video"
                        val cleanTitle = rawTitle.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(50) + ".mp4"
                        val playUrl = resolveTikWmUrl(data.optString("play", ""))
                        val wmPlayUrl = resolveTikWmUrl(data.optString("wmplay", ""))
                        val hdPlayUrl = resolveTikWmUrl(data.optString("hdplay", "")).ifEmpty { playUrl }.ifEmpty { wmPlayUrl }
                        val musicUrl = resolveTikWmUrl(data.optString("music", ""))
                        val thumb = resolveTikWmUrl(data.optString("cover", ""))
                        val duration = data.optInt("duration", 30)
                        val sizeBytes = data.optLong("size", 15_000_000L)

                        val qualities = mutableListOf<VideoQuality>()
                        if (hdPlayUrl.startsWith("http")) {
                            qualities.add(
                                VideoQuality(
                                    label = "1080p HD (No Watermark)",
                                    format = "mp4",
                                    approximateSize = sizeBytes.coerceAtLeast(8_000_000L),
                                    downloadUrl = hdPlayUrl
                                )
                            )
                        }
                        if (playUrl.startsWith("http") && playUrl != hdPlayUrl) {
                            qualities.add(
                                VideoQuality(
                                    label = "720p Standard (No Watermark)",
                                    format = "mp4",
                                    approximateSize = (sizeBytes * 2) / 3,
                                    downloadUrl = playUrl
                                )
                            )
                        }
                        if (musicUrl.startsWith("http")) {
                            qualities.add(
                                VideoQuality(
                                    label = "Audio Only (MP3)",
                                    format = "mp3",
                                    approximateSize = 2_500_000L,
                                    downloadUrl = musicUrl,
                                    isAudioOnly = true
                                )
                            )
                        }

                        if (qualities.isNotEmpty()) {
                            Log.i("ShareNow_Sniffer", "TikWM resolved ${qualities.size} streams for $targetTikTokUrl")
                            return@withContext VideoInfo(
                                title = cleanTitle,
                                sourcePageUrl = targetTikTokUrl,
                                thumbnailUrl = thumb,
                                durationSeconds = duration,
                                qualities = qualities,
                                extractedHeaders = mapOf(
                                    "User-Agent" to DESKTOP_USER_AGENT,
                                    "Referer" to "https://www.tikwm.com/"
                                )
                            )
                        }
                    }
                }
            }

            // Fallback: Extract direct playAddr / downloadAddr / video/tos stream from TikTok DOM/JSON
            if (!htmlContent.isNullOrBlank()) {
                val playAddrRegex = Pattern.compile(""""(?:playAddr|downloadAddr)"\s*:\s*"(https?:\\?/\\?/[^"]+)"""")
                val playMatcher = playAddrRegex.matcher(htmlContent)
                val directStreams = LinkedHashSet<String>()
                while (playMatcher.find()) {
                    val s = sanitizeMediaUrl(playMatcher.group(1) ?: "")
                    if (s.startsWith("http")) directStreams.add(s)
                }
                val tosRegex = Pattern.compile("""https?:\\?/\\?/[^\s"'<>\\]+/video/tos/[^\s"'<>\\]+""")
                val tosMatcher = tosRegex.matcher(htmlContent)
                while (tosMatcher.find()) {
                    val s = sanitizeMediaUrl(tosMatcher.group(0) ?: "")
                    if (s.startsWith("http")) directStreams.add(s)
                }
                if (directStreams.isNotEmpty()) {
                    val primary = directStreams.first()
                    val qualities = listOf(
                        VideoQuality("1080p Full HD MP4", "mp4", 16_000_000L, primary),
                        VideoQuality("720p HD MP4", "mp4", 9_500_000L, primary),
                        VideoQuality("Audio Only (MP3)", "mp3", 2_500_000L, primary, isAudioOnly = true)
                    )
                    return@withContext VideoInfo(
                        title = "TikTok_Video_${System.currentTimeMillis()}.mp4",
                        sourcePageUrl = pageUrl,
                        thumbnailUrl = "",
                        durationSeconds = 30,
                        qualities = qualities,
                        extractedHeaders = mapOf(
                            "User-Agent" to MOBILE_USER_AGENT,
                            "Referer" to "https://www.tiktok.com/"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("ShareNow_Sniffer", "TikTok extractor fallback: ${e.message}")
        }
        extractUniversalVideo(pageUrl, htmlContent)
    }

    /**
     * 2. Facebook Video / Reels / Watch Extractor
     */
    suspend fun extractFacebookVideo(pageUrl: String, htmlContent: String? = null): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val initialUrl = normalizeFacebookUrl(pageUrl)
            var currentUrl = initialUrl
            var html = ""
            var redirectCount = 0

            while (redirectCount < 5) {
                val reqBuilder = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,vi;q=0.8")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-User", "?1")
                    .header("Sec-Fetch-Dest", "document")
                try {
                    val cookie = android.webkit.CookieManager.getInstance().getCookie(currentUrl)
                    if (!cookie.isNullOrBlank()) reqBuilder.header("Cookie", cookie)
                } catch (_: Exception) {}
                val request = reqBuilder.build()

                val response: Response = httpClient.newCall(request).execute()
                val code = response.code
                if (code in 300..399) {
                    val location = response.header("Location")
                    response.close()
                    if (!location.isNullOrEmpty()) {
                        currentUrl = if (location.startsWith("http")) location else "https://www.facebook.com$location"
                        redirectCount++
                        continue
                    }
                }

                if (!response.isSuccessful) {
                    response.close()
                    break
                }

                html = response.body?.string() ?: ""
                response.close()
                break
            }

            val targetVideoId = extractFacebookVideoId(pageUrl)
                .ifEmpty { extractFacebookVideoId(currentUrl) }

            val combinedHtml = buildString {
                if (html.isNotBlank()) append(html).append("\n")
                if (!htmlContent.isNullOrBlank()) append(htmlContent)
            }
            if (combinedHtml.isBlank()) return@withContext null

            // Nếu có targetVideoId cụ thể (ví dụ cuộn tới Reel/Video thứ N), ưu tiên cắt vùng JSON quanh ID đó trước
            val scopedHtml = if (targetVideoId.isNotEmpty()) {
                val idx = combinedHtml.indexOf(targetVideoId)
                if (idx >= 0) {
                    val start = (idx - 4000).coerceAtLeast(0)
                    val end = (idx + 35000).coerceAtMost(combinedHtml.length)
                    combinedHtml.substring(start, end)
                } else {
                    combinedHtml
                }
            } else {
                combinedHtml
            }

            fun findFirstStream(source: String, patternStr: String): String? {
                val m = Pattern.compile(patternStr).matcher(source)
                while (m.find()) {
                    val raw = m.group(1) ?: ""
                    val cleaned = stripByteRangeParams(raw)
                    if (cleaned.startsWith("http") && !parseMetaEfgInfo(cleaned).isAudioOnly) {
                        return cleaned
                    }
                }
                return null
            }

            val hdPattern = """"(?:browser_native_hd_url|playable_url_quality_hd|hd_src_no_ratelimit|hd_src)"\s*:\s*"(https?:[^"]+)""""
            val sdPattern = """"(?:browser_native_sd_url|playable_url|sd_src_no_ratelimit|sd_src|progressive_url)"\s*:\s*"(https?:[^"]+)""""

            var hdUrl: String? = findFirstStream(scopedHtml, hdPattern) ?: findFirstStream(combinedHtml, hdPattern)
            var sdUrl: String? = findFirstStream(scopedHtml, sdPattern) ?: findFirstStream(combinedHtml, sdPattern)

            val fbcdnRegex = Pattern.compile("""https:(?:\\/\\/|//)[^\s"'<>\\]+\.fbcdn\.net(?:/o1/v/t2/[^\s"'<>\\]+|[^\s"'<>\\]+\.mp4[^\s"'<>\\]*)""")
            val allFbcdnMatcher = fbcdnRegex.matcher(combinedHtml)
            while (allFbcdnMatcher.find()) {
                val candidate = stripByteRangeParams(allFbcdnMatcher.group(0) ?: "")
                registerMetaStream(candidate)
            }

            if (hdUrl == null && sdUrl == null) {
                val fbcdnMatcher = fbcdnRegex.matcher(scopedHtml)
                while (fbcdnMatcher.find()) {
                    val candidate = stripByteRangeParams(fbcdnMatcher.group(0) ?: "")
                    val efg = registerMetaStream(candidate)
                    if (!efg.isAudioOnly) {
                        if (targetVideoId.isEmpty() || efg.videoId.isEmpty() || efg.videoId == targetVideoId) {
                            if (efg.qualityRank >= 720 && hdUrl == null) {
                                hdUrl = candidate
                            } else if (sdUrl == null) {
                                sdUrl = candidate
                            }
                        } else if (sdUrl == null) {
                            sdUrl = candidate
                        }
                    }
                }
                if (sdUrl == null && hdUrl == null && scopedHtml !== combinedHtml) {
                    val fullMatcher = fbcdnRegex.matcher(combinedHtml)
                    while (fullMatcher.find()) {
                        val candidate = stripByteRangeParams(fullMatcher.group(0) ?: "")
                        val efg = registerMetaStream(candidate)
                        if (!efg.isAudioOnly) {
                            if (targetVideoId.isEmpty() || efg.videoId.isEmpty() || efg.videoId == targetVideoId) {
                                sdUrl = candidate
                                break
                            } else if (sdUrl == null) {
                                sdUrl = candidate
                            }
                        }
                    }
                }
            }

            if (hdUrl == null && sdUrl == null) return@withContext extractUniversalVideo(pageUrl, combinedHtml)

            val resolvedPrimary = hdUrl ?: sdUrl ?: ""
            val efgVideoId = parseMetaEfgInfo(resolvedPrimary).videoId
            val videoId = targetVideoId
                .ifEmpty { efgVideoId }
                .ifEmpty { System.currentTimeMillis().toString() }

            val qualities = mutableListOf<VideoQuality>()
            if (!hdUrl.isNullOrEmpty()) {
                qualities.add(
                    VideoQuality(
                        label = "1080p Full HD",
                        format = "mp4",
                        approximateSize = 48_500_000L,
                        downloadUrl = hdUrl
                    )
                )
            }
            if (!sdUrl.isNullOrEmpty() && sdUrl != hdUrl) {
                qualities.add(
                    VideoQuality(
                        label = if (hdUrl.isNullOrEmpty()) "720p HD" else "720p / 480p SD",
                        format = "mp4",
                        approximateSize = 24_200_000L,
                        downloadUrl = sdUrl
                    )
                )
            }

            val primaryUrl = hdUrl ?: sdUrl ?: ""
            qualities.add(
                VideoQuality(
                    label = "Audio Only (MP3)",
                    format = "mp3",
                    approximateSize = 4_500_000L,
                    downloadUrl = primaryUrl,
                    isAudioOnly = true
                )
            )

            var fbCaption = ""
            val ogDescMatch = Pattern.compile("""<meta\s+property=["']og:(?:description|title)["']\s+content=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(combinedHtml)
            if (ogDescMatch.find()) {
                fbCaption = android.text.Html.fromHtml(ogDescMatch.group(1) ?: "", android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            }
            if (fbCaption.isBlank()) {
                val msgMatch = Pattern.compile(""""message"\s*:\s*\{\s*"text"\s*:\s*"([^"]+)"""").matcher(combinedHtml)
                if (msgMatch.find()) {
                    fbCaption = try { JSONObject("""{"t":"${msgMatch.group(1)}"}""").optString("t", "") } catch (_: Exception) { msgMatch.group(1) ?: "" }
                }
            }

            VideoInfo(
                title = buildDescriptiveVideoFileName(videoId, fbCaption),
                sourcePageUrl = currentUrl,
                thumbnailUrl = "",
                durationSeconds = 60,
                qualities = qualities,
                extractedHeaders = mapOf(
                    "User-Agent" to DESKTOP_USER_AGENT,
                    "Referer" to "https://www.facebook.com/"
                )
            )
        } catch (e: Exception) {
            Log.e("ShareNow_Sniffer", "Facebook extract error", e)
            null
        }
    }

    /**
     * 2B. Facebook Page Reels Batch Extractor (discovers all Reels on https://www.facebook.com/<page>/reels/)
     */
    suspend fun extractFacebookPageReelsBatch(pageUrl: String, htmlContent: String? = null): List<VideoInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoInfo>()
        try {
            val reelsTabUrl = toFacebookPageReelsUrl(pageUrl)
            val fetchedHtml = fetchHtmlWithOkHttp(reelsTabUrl)
            val combinedHtml = buildString {
                if (fetchedHtml.isNotBlank()) append(fetchedHtml).append("\n")
                if (!htmlContent.isNullOrBlank()) append(htmlContent)
            }
            if (combinedHtml.isBlank()) return@withContext emptyList()

            val fbcdnRegex = Pattern.compile("""https:(?:\\/\\/|//)[^\s"'<>\\]+\.fbcdn\.net(?:/o1/v/t2/[^\s"'<>\\]+|[^\s"'<>\\]+\.mp4[^\s"'<>\\]*)""")
            val fbMatcher = fbcdnRegex.matcher(combinedHtml)
            while (fbMatcher.find()) {
                registerMetaStream(stripByteRangeParams(fbMatcher.group(0) ?: ""))
            }

            val seenIds = LinkedHashSet<String>()
            val reelBlockRegex = Pattern.compile(
                """"(?:video_id|id)"\s*:\s*"(\d{8,25})"[\s\S]{1,4000}?"(?:browser_native_sd_url|playable_url)"\s*:\s*"(https?:[^"]+)"(?:[\s\S]{1,1800}?"(?:browser_native_hd_url|playable_url_quality_hd)"\s*:\s*"(https?:[^"]+)")?"""
            )
            val m = reelBlockRegex.matcher(combinedHtml)
            while (m.find()) {
                val vid = m.group(1) ?: continue
                if (!seenIds.add(vid)) continue
                val sd = m.group(2)?.let { stripByteRangeParams(it) } ?: ""
                val hd = m.group(3)?.let { stripByteRangeParams(it) } ?: ""
                val primary = hd.ifEmpty { sd }
                if (!primary.startsWith("http")) continue

                val qualities = mutableListOf<VideoQuality>()
                if (hd.startsWith("http")) {
                    qualities.add(VideoQuality("720p / 1080p HD MP4", "mp4", 18_000_000L, hd))
                }
                if (sd.startsWith("http") && sd != hd) {
                    qualities.add(VideoQuality("480p SD MP4", "mp4", 9_000_000L, sd))
                }
                results.add(
                    VideoInfo(
                        title = "Reel_$vid.mp4",
                        sourcePageUrl = "https://www.facebook.com/reel/$vid/",
                        thumbnailUrl = "",
                        durationSeconds = parseMetaEfgInfo(primary).durationSec.takeIf { it > 0 } ?: 30,
                        qualities = qualities,
                        extractedHeaders = mapOf(
                            "User-Agent" to DESKTOP_USER_AGENT,
                            "Referer" to "https://www.facebook.com/"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ShareNow_Sniffer", "Facebook Page Reels batch extract error", e)
        }
        results
    }

    /**
     * 3. Instagram Reels / Post Video Extractor
     */
    suspend fun extractInstagramVideo(pageUrl: String, htmlContent: String? = null): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            var html = htmlContent ?: ""
            if (html.isBlank()) {
                val request = Request.Builder()
                    .url(pageUrl)
                    .header("User-Agent", MOBILE_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                val response: Response = httpClient.newCall(request).execute()
                html = response.body?.string() ?: ""
                response.close()
            }

            if (html.isBlank()) return@withContext null

            var streamUrl: String? = null
            val ogVideoRegex = Pattern.compile("""<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
            val ogMatcher = ogVideoRegex.matcher(html)
            if (ogMatcher.find()) {
                streamUrl = sanitizeMediaUrl(ogMatcher.group(1) ?: "")
            }

            if (streamUrl.isNullOrEmpty()) {
                val jsonUrlRegex = Pattern.compile(""""(?:video_url|playable_url|playable_url_quality_hd)":"([^"]+)"""")
                val jsonMatcher = jsonUrlRegex.matcher(html)
                if (jsonMatcher.find()) {
                    streamUrl = sanitizeMediaUrl(jsonMatcher.group(1) ?: "")
                }
            }

            if (streamUrl.isNullOrEmpty()) {
                val versionsRegex = Pattern.compile(""""video_versions":\[\{"url":"([^"]+)"""")
                val vMatcher = versionsRegex.matcher(html)
                if (vMatcher.find()) {
                    streamUrl = sanitizeMediaUrl(vMatcher.group(1) ?: "")
                }
            }

            if (streamUrl.isNullOrEmpty()) {
                val cdnRegex = Pattern.compile("""https:\\/\\/[^"']*(?:cdninstagram\.com|\.fbcdn\.net)[^"']*\.mp4[^"']*""")
                val cdnMatcher = cdnRegex.matcher(html)
                if (cdnMatcher.find()) {
                    streamUrl = sanitizeMediaUrl(cdnMatcher.group(0) ?: "")
                }
            }

            if (streamUrl.isNullOrEmpty()) return@withContext extractUniversalVideo(pageUrl, html)

            streamUrl = streamUrl.replace(Regex("&?byte(start|end)=\\d+"), "")

            val reelId = pageUrl.substringAfter("/reel/").substringAfter("/p/").substringBefore("/").substringBefore("?")
            var igCaption = ""
            val igOgMatch = Pattern.compile("""<meta\s+property=["']og:(?:description|title)["']\s+content=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(html)
            if (igOgMatch.find()) {
                igCaption = android.text.Html.fromHtml(igOgMatch.group(1) ?: "", android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            }
            if (igCaption.isBlank()) {
                val capMatch = Pattern.compile(""""caption"\s*:\s*\{[^{}]*"text"\s*:\s*"([^"]+)"""").matcher(html)
                if (capMatch.find()) {
                    igCaption = try { JSONObject("""{"t":"${capMatch.group(1)}"}""").optString("t", "") } catch (_: Exception) { capMatch.group(1) ?: "" }
                }
            }
            val finalTitle = buildDescriptiveVideoFileName(reelId.ifBlank { "ig_${System.currentTimeMillis()}" }, igCaption)

            val qualities = listOf(
                VideoQuality("1080p Full HD", "mp4", 18_500_000L, streamUrl),
                VideoQuality("720p HD", "mp4", 9_200_000L, streamUrl),
                VideoQuality("Audio Only (MP3)", "mp3", 2_500_000L, streamUrl, isAudioOnly = true)
            )

            VideoInfo(
                title = finalTitle,
                sourcePageUrl = pageUrl,
                thumbnailUrl = "",
                durationSeconds = 30,
                qualities = qualities,
                extractedHeaders = mapOf(
                    "User-Agent" to MOBILE_USER_AGENT,
                    "Referer" to "https://www.instagram.com/"
                )
            )
        } catch (e: Exception) {
            Log.e("ShareNow_Sniffer", "Instagram extract error", e)
            null
        }
    }

    /**
     * 4. YouTube Video / Shorts Extractor
     */
    suspend fun extractYouTubeVideo(pageUrl: String, htmlContent: String? = null): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            var videoId = when {
                pageUrl.contains("watch?v=") -> pageUrl.substringAfter("watch?v=").substringBefore('&').substringBefore('?').substringBefore('#')
                pageUrl.contains("youtu.be/") -> pageUrl.substringAfter("youtu.be/").substringBefore('?').substringBefore('/').substringBefore('#')
                pageUrl.contains("/shorts/") -> pageUrl.substringAfter("/shorts/").substringBefore('?').substringBefore('/').substringBefore('#')
                pageUrl.contains("/embed/") -> pageUrl.substringAfter("/embed/").substringBefore('?').substringBefore('/').substringBefore('#')
                pageUrl.contains("v=") -> pageUrl.substringAfter("v=").substringBefore('&').substringBefore('?').substringBefore('#')
                else -> ""
            }

            if ((videoId.isEmpty() || videoId.length < 5) && !htmlContent.isNullOrBlank()) {
                val vidMatcher = Pattern.compile("""(?:"videoId"\s*:\s*"|watch\?v=|/shorts/)([a-zA-Z0-9_-]{11})""").matcher(htmlContent)
                if (vidMatcher.find()) {
                    videoId = vidMatcher.group(1) ?: ""
                }
            }

            if (videoId.isEmpty() || videoId.length < 5) return@withContext null

            var title = "YouTube_Video_$videoId"
            var thumbUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            if (!htmlContent.isNullOrBlank()) {
                val tMatch = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(htmlContent)
                if (tMatch.find()) {
                    title = tMatch.group(1)?.replace("- YouTube", "")?.trim() ?: title
                }
            } else {
                try {
                    val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
                    val oembedRes = httpClient.newCall(Request.Builder().url(oembedUrl).build()).execute()
                    if (oembedRes.isSuccessful) {
                        val oembedJson = JSONObject(oembedRes.body?.string() ?: "{}")
                        val oTitle = oembedJson.optString("title", "")
                        if (oTitle.isNotEmpty()) title = oTitle
                        val oThumb = oembedJson.optString("thumbnail_url", "")
                        if (oThumb.isNotEmpty()) thumbUrl = oThumb
                    }
                    oembedRes.close()
                } catch (_: Exception) {}
            }

            val targetWatchUrl = "https://www.youtube.com/watch?v=$videoId"

            // Fast Path: YouTube Innertube ANDROID_VR / ANDROID_TESTSUITE / ANDROID / IOS API (< 1 second direct MP4/HLS stream)
            val innertubeClients = listOf(
                Triple(
                    "ANDROID_VR",
                    "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                    """{"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true,"context":{"client":{"clientName":"ANDROID_VR","clientVersion":"1.60.19","deviceMake":"Oculus","deviceModel":"Quest 3","osName":"Android","osVersion":"12L","androidSdkVersion":32,"hl":"en","gl":"US"}}}"""
                ),
                Triple(
                    "ANDROID_TESTSUITE",
                    "com.google.android.youtube/1.9 (Linux; U; Android 12; US) gzip",
                    """{"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true,"context":{"client":{"clientName":"ANDROID_TESTSUITE","clientVersion":"1.9","androidSdkVersion":32,"osName":"Android","osVersion":"12","hl":"en","gl":"US"}}}"""
                ),
                Triple(
                    "ANDROID",
                    "com.google.android.youtube/20.03.35 (Linux; U; Android 14) gzip",
                    """{"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true,"context":{"client":{"clientName":"ANDROID","clientVersion":"20.03.35","androidSdkVersion":34,"osName":"Android","osVersion":"14","hl":"en","gl":"US"}}}"""
                ),
                Triple(
                    "IOS",
                    "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)",
                    """{"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true,"context":{"client":{"clientName":"IOS","clientVersion":"20.03.02","deviceMake":"Apple","deviceModel":"iPhone16,2","osName":"iPhone","osVersion":"18.2.1.22C161","hl":"en","gl":"US"}}}"""
                )
            )
            for ((clientName, clientUa, innertubePayload) in innertubeClients) {
                try {
                    val body = innertubePayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    val itReq = Request.Builder()
                        .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                        .header("User-Agent", clientUa)
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build()
                    val itRes = httpClient.newCall(itReq).execute()
                    val itStr = itRes.body?.string() ?: ""
                    itRes.close()
                    if (itRes.isSuccessful && itStr.isNotEmpty()) {
                        val itJson = JSONObject(itStr)
                        val playability = itJson.optJSONObject("playabilityStatus")?.optString("status", "")
                        Log.i("ShareNow_Sniffer", "YouTube Innertube ($clientName) playability=$playability for $videoId")
                        val videoDetails = itJson.optJSONObject("videoDetails")
                        val ytTitle = videoDetails?.optString("title", title)?.ifBlank { title } ?: title
                        val streamingData = itJson.optJSONObject("streamingData")
                        val formats = streamingData?.optJSONArray("formats")
                        val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")
                        val hlsManifestUrl = streamingData?.optString("hlsManifestUrl", "") ?: ""
                        val itQualities = mutableListOf<VideoQuality>()
                        var muxedVideoUrl = ""
                        var muxedSize = 25_000_000L
                        var audioStreamUrl = ""
                        var audioSize = 4_500_000L

                        if (formats != null) {
                            for (i in 0 until formats.length()) {
                                val fmtObj = formats.getJSONObject(i)
                                val fUrl = fmtObj.optString("url", "")
                                val qLabel = fmtObj.optString("qualityLabel", "720p")
                                val contentLen = fmtObj.optLong("contentLength", 25_000_000L)
                                if (fUrl.startsWith("http") && !fUrl.contains("sabr=1")) {
                                    if (muxedVideoUrl.isEmpty()) {
                                        muxedVideoUrl = fUrl
                                        muxedSize = contentLen
                                    }
                                    val mappedLabel = when {
                                        qLabel.contains("1080") -> "1080p Full HD MP4"
                                        qLabel.contains("720") -> "720p HD MP4"
                                        qLabel.contains("480") -> "480p SD MP4"
                                        else -> "1080p / 720p HD MP4"
                                    }
                                    itQualities.add(VideoQuality(mappedLabel, "mp4", contentLen, fUrl))
                                }
                            }
                        }

                        val requiresMuxedForDirectHttp = (clientName == "ANDROID" || clientName == "IOS")
                        if (adaptiveFormats != null) {
                            for (i in 0 until adaptiveFormats.length()) {
                                val fmtObj = adaptiveFormats.getJSONObject(i)
                                val mime = fmtObj.optString("mimeType", "")
                                val fUrl = fmtObj.optString("url", "")
                                val qLabel = fmtObj.optString("qualityLabel", "")
                                val contentLen = fmtObj.optLong("contentLength", 20_000_000L)
                                if (fUrl.startsWith("http") && !fUrl.contains("sabr=1")) {
                                    if (mime.startsWith("audio/mp4") || (audioStreamUrl.isEmpty() && mime.startsWith("audio/"))) {
                                        if (audioStreamUrl.isEmpty()) {
                                            audioStreamUrl = fUrl
                                            audioSize = contentLen.coerceAtLeast(3_000_000L)
                                        }
                                    } else if (mime.startsWith("video/mp4") && qLabel.isNotEmpty() && muxedVideoUrl.isEmpty()) {
                                        val label = when {
                                            qLabel.contains("1080") -> "1080p Full HD MP4"
                                            qLabel.contains("720") -> "720p HD MP4"
                                            qLabel.contains("480") -> "480p SD MP4"
                                            qLabel.contains("360") -> "360p Standard MP4"
                                            qLabel.contains("240") -> "240p Low MP4"
                                            qLabel.contains("144") -> "144p Data Saver MP4"
                                            else -> "$qLabel MP4"
                                        }
                                        if (itQualities.none { it.label == label }) {
                                            itQualities.add(VideoQuality(label, "mp4", contentLen, fUrl))
                                        }
                                    }
                                }
                            }
                        }

                        if (itQualities.isEmpty() && hlsManifestUrl.startsWith("http")) {
                            itQualities.add(VideoQuality("1080p / 720p Adaptive HLS", "m3u8", 35_000_000L, hlsManifestUrl))
                        }

                        if (itQualities.isNotEmpty()) {
                            val primaryStream = itQualities.first().downloadUrl
                            val finalAudioUrl = if (requiresMuxedForDirectHttp && muxedVideoUrl.isNotEmpty()) {
                                muxedVideoUrl
                            } else {
                                audioStreamUrl.ifEmpty { primaryStream }
                            }
                            itQualities.add(VideoQuality("Audio Only (MP3)", "mp3", audioSize, finalAudioUrl, isAudioOnly = true))
                            val ytDesc = videoDetails?.optString("shortDescription", "") ?: ""
                            val cleanYtTitle = buildDescriptiveVideoFileName(videoId, ytTitle, ytDesc)
                            Log.i("ShareNow_Sniffer", "YouTube Innertube ($clientName) resolved ${itQualities.size} streams for $cleanYtTitle")
                            return@withContext VideoInfo(
                                title = cleanYtTitle,
                                sourcePageUrl = targetWatchUrl,
                                thumbnailUrl = thumbUrl,
                                durationSeconds = videoDetails?.optString("lengthSeconds", "180")?.toIntOrNull() ?: 180,
                                qualities = itQualities,
                                extractedHeaders = mapOf(
                                    "User-Agent" to clientUa,
                                    "Referer" to "https://www.youtube.com/"
                                ),
                                isStreamM3u8 = primaryStream.contains(".m3u8")
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ShareNow_Sniffer", "Innertube ($clientName) fallback: ${e.message}")
                }
            }

            val resolverUrl = "https://loader.to/ajax/download.php?format=720&url=" + URLEncoder.encode(targetWatchUrl, "UTF-8")
            val req = Request.Builder()
                .url(resolverUrl)
                .header("User-Agent", DESKTOP_USER_AGENT)
                .build()

            val res = httpClient.newCall(req).execute()
            if (!res.isSuccessful) {
                res.close()
                return@withContext null
            }
            val initJsonStr = res.body?.string() ?: ""
            res.close()

            val initJson = JSONObject(initJsonStr)
            val progressUrl = initJson.optString("progress_url", "")
            val resolvedTitle = initJson.optString("title", title).ifEmpty { title }

            var directMp4Url: String? = null
            if (progressUrl.isNotEmpty()) {
                for (attempt in 1..15) {
                    kotlinx.coroutines.delay(1200)
                    try {
                        val pReq = Request.Builder().url(progressUrl).header("User-Agent", DESKTOP_USER_AGENT).build()
                        val pRes = httpClient.newCall(pReq).execute()
                        if (pRes.isSuccessful) {
                            val pData = JSONObject(pRes.body?.string() ?: "{}")
                            pRes.close()
                            if (pData.optInt("success", 0) == 1) {
                                val dlUrl = pData.optString("download_url", "")
                                if (dlUrl.startsWith("http")) {
                                    directMp4Url = dlUrl
                                    break
                                }
                            }
                        } else {
                            pRes.close()
                        }
                    } catch (_: Exception) {}
                }
            }

            if (directMp4Url.isNullOrEmpty()) return@withContext null

            val sanitizedTitle = resolvedTitle.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_") + ".mp4"
            val qualities = listOf(
                VideoQuality("720p HD MP4", "mp4", 35_000_000L, directMp4Url),
                VideoQuality("360p SD MP4", "mp4", 18_000_000L, directMp4Url),
                VideoQuality("Audio Only (MP3)", "mp3", 4_500_000L, directMp4Url, isAudioOnly = true)
            )

            VideoInfo(
                title = sanitizedTitle,
                sourcePageUrl = targetWatchUrl,
                thumbnailUrl = thumbUrl,
                durationSeconds = 180,
                qualities = qualities,
                extractedHeaders = mapOf(
                    "User-Agent" to DESKTOP_USER_AGENT,
                    "Referer" to "https://www.youtube.com/"
                )
            )
        } catch (e: Exception) {
            Log.e("ShareNow_Sniffer", "YouTube extract error", e)
            null
        }
    }

    /**
     * 5. Twitter / X Video Extractor via vxtwitter API + Universal fallback
     */
    suspend fun extractTwitterVideo(pageUrl: String, htmlContent: String? = null): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            var statusId = pageUrl.substringAfter("/status/").substringBefore('?').substringBefore('/')
            if ((statusId.isEmpty() || !statusId.all { it.isDigit() }) && !htmlContent.isNullOrBlank()) {
                val m = Pattern.compile("""/status/(\d{15,22})""").matcher(htmlContent)
                if (m.find()) statusId = m.group(1) ?: ""
            }
            if (statusId.isNotEmpty() && statusId.all { it.isDigit() }) {
                val apiUrl = "https://api.vxtwitter.com/Twitter/status/$statusId"
                val req = Request.Builder().url(apiUrl).header("User-Agent", DESKTOP_USER_AGENT).build()
                val res = httpClient.newCall(req).execute()
                val body = res.body?.string() ?: ""
                res.close()

                if (res.isSuccessful && body.isNotEmpty()) {
                    val json = JSONObject(body)
                    val mediaArr = json.optJSONArray("media_extended")
                    if (mediaArr != null && mediaArr.length() > 0) {
                        val qualities = mutableListOf<VideoQuality>()
                        for (i in 0 until mediaArr.length()) {
                            val m = mediaArr.getJSONObject(i)
                            val mUrl = m.optString("url", "")
                            val mType = m.optString("type", "")
                            if (mUrl.startsWith("http") && (mType == "video" || mType == "gif" || mUrl.contains(".mp4"))) {
                                qualities.add(
                                    VideoQuality(
                                        label = "1080p / 720p HD MP4",
                                        format = "mp4",
                                        approximateSize = 18_000_000L,
                                        downloadUrl = mUrl
                                    )
                                )
                            }
                        }
                        if (qualities.isNotEmpty()) {
                            val primary = qualities.first().downloadUrl
                            qualities.add(VideoQuality("Audio Only (MP3)", "mp3", 2_500_000L, primary, isAudioOnly = true))
                            val textTitle = json.optString("text", "X_Video_$statusId").replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(45)
                            return@withContext VideoInfo(
                                title = "${textTitle.ifEmpty { "X_Video_$statusId" }}.mp4",
                                sourcePageUrl = pageUrl,
                                thumbnailUrl = json.optString("user_profile_image_url", ""),
                                durationSeconds = 45,
                                qualities = qualities,
                                extractedHeaders = mapOf(
                                    "User-Agent" to DESKTOP_USER_AGENT,
                                    "Referer" to "https://x.com/"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ShareNow_Sniffer", "Twitter API fallback: ${e.message}")
        }
        extractUniversalVideo(pageUrl, htmlContent)
    }

    /**
     * 6. Vimeo Video Extractor
     */
    suspend fun extractVimeoVideo(pageUrl: String, htmlContent: String? = null): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            var vimeoId = ""
            val urlMatcher = Pattern.compile("""vimeo\.com/(?:.*/)?(\d{6,12})""").matcher(pageUrl)
            if (urlMatcher.find()) {
                vimeoId = urlMatcher.group(1) ?: ""
            }
            if (vimeoId.isEmpty() && !htmlContent.isNullOrBlank()) {
                val htmlMatcher = Pattern.compile("""(?:player\.vimeo\.com/video/|vimeo\.com/|clip_id"\s*:\s*"?)(\d{6,12})""").matcher(htmlContent)
                if (htmlMatcher.find()) {
                    vimeoId = htmlMatcher.group(1) ?: ""
                }
            }
            if (vimeoId.isNotEmpty() && vimeoId.all { it.isDigit() }) {
                val configUrl = "https://player.vimeo.com/video/$vimeoId/config"
                val req = Request.Builder().url(configUrl).header("User-Agent", DESKTOP_USER_AGENT).build()
                val res = httpClient.newCall(req).execute()
                if (res.isSuccessful) {
                    val jsonStr = res.body?.string() ?: ""
                    res.close()
                    val json = JSONObject(jsonStr)
                    val title = json.optJSONObject("video")?.optString("title", "Vimeo_$vimeoId") ?: "Vimeo_$vimeoId"
                    val filesObj = json.optJSONObject("request")?.optJSONObject("files")
                    val qualities = mutableListOf<VideoQuality>()

                    val progressiveArr = filesObj?.optJSONArray("progressive")
                    if (progressiveArr != null) {
                        for (i in 0 until progressiveArr.length()) {
                            val p = progressiveArr.getJSONObject(i)
                            val pUrl = p.optString("url", "")
                            val pQual = p.optString("quality", "720p")
                            if (pUrl.startsWith("http")) {
                                qualities.add(VideoQuality("$pQual HD MP4", "mp4", 35_000_000L, pUrl))
                            }
                        }
                    }

                    val hlsUrl = filesObj?.optJSONObject("hls")?.let { hlsObj ->
                        val defaultCdn = hlsObj.optString("default_cdn", "")
                        hlsObj.optJSONObject("cdns")?.optJSONObject(defaultCdn)?.optString("url", "")
                    } ?: ""
                    if (hlsUrl.startsWith("http")) {
                        qualities.add(VideoQuality("1080p HD Stream", "m3u8", 45_000_000L, hlsUrl))
                    }

                    if (qualities.isNotEmpty()) {
                        val primary = qualities.first().downloadUrl
                        qualities.add(VideoQuality("Audio Only (MP3)", "mp3", 3_500_000L, primary, isAudioOnly = true))
                        val sanitizedTitle = title.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_") + ".mp4"
                        return@withContext VideoInfo(
                            title = sanitizedTitle,
                            sourcePageUrl = pageUrl,
                            thumbnailUrl = "",
                            durationSeconds = 120,
                            qualities = qualities,
                            extractedHeaders = mapOf(
                                "User-Agent" to DESKTOP_USER_AGENT,
                                "Referer" to "https://vimeo.com/"
                            ),
                            isStreamM3u8 = primary.contains(".m3u8")
                        )
                    }
                } else {
                    res.close()
                }
            }
        } catch (e: Exception) {
            Log.e("ShareNow_Sniffer", "Vimeo extract error", e)
        }
        extractUniversalVideo(pageUrl, htmlContent)
    }

    /**
     * 7. Dailymotion Video Extractor
     */
    suspend fun extractDailymotionVideo(pageUrl: String, htmlContent: String? = null): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            var videoId = if (pageUrl.contains("/video/")) {
                pageUrl.substringAfter("/video/").substringBefore('?').substringBefore('_').substringBefore('/')
            } else ""
            if (videoId.isEmpty() && !htmlContent.isNullOrBlank()) {
                val m = Pattern.compile("""/video/([a-zA-Z0-9]{5,10})""").matcher(htmlContent)
                if (m.find()) videoId = m.group(1) ?: ""
            }
            if (videoId.isNotEmpty()) {
                val metaUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
                val req = Request.Builder().url(metaUrl).header("User-Agent", DESKTOP_USER_AGENT).build()
                val res = httpClient.newCall(req).execute()
                val body = res.body?.string() ?: ""
                res.close()
                if (res.isSuccessful && body.isNotEmpty()) {
                    val json = JSONObject(body)
                    val title = json.optString("title", "Dailymotion_$videoId").replace(Regex("[^\\p{L}\\p{N}._ -]"), "_") + ".mp4"
                    val qualitiesObj = json.optJSONObject("qualities")
                    val autoArr = qualitiesObj?.optJSONArray("auto")
                    if (autoArr != null && autoArr.length() > 0) {
                        val streamUrl = autoArr.getJSONObject(0).optString("url", "")
                        if (streamUrl.startsWith("http")) {
                            return@withContext VideoInfo(
                                title = title,
                                sourcePageUrl = pageUrl,
                                thumbnailUrl = "",
                                durationSeconds = json.optInt("duration", 120),
                                qualities = listOf(
                                    VideoQuality("1080p / 720p HD", if (streamUrl.contains(".m3u8")) "m3u8" else "mp4", 35_000_000L, streamUrl),
                                    VideoQuality("Audio Only (MP3)", "mp3", 3_500_000L, streamUrl, isAudioOnly = true)
                                ),
                                extractedHeaders = mapOf(
                                    "User-Agent" to DESKTOP_USER_AGENT,
                                    "Referer" to "https://www.dailymotion.com/"
                                ),
                                isStreamM3u8 = streamUrl.contains(".m3u8")
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ShareNow_Sniffer", "Dailymotion metadata fallback: ${e.message}")
        }
        extractUniversalVideo(pageUrl, htmlContent)
    }

    /**
     * 8. XNXX / XVideos / HTML5 Player Extractor
     */
    suspend fun extractXnxxVideo(pageUrl: String, htmlContent: String? = null): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val html = if (!htmlContent.isNullOrBlank()) htmlContent else fetchHtmlWithOkHttp(pageUrl)
            if (html.isBlank()) return@withContext null

            val titleRegex = Pattern.compile("""(?:html5player\.setVideoTitle|var\s+video_title)\s*[\(=]\s*['"]([^'"]+)['"]""")
            val titleMatcher = titleRegex.matcher(html)
            var videoTitle = if (titleMatcher.find()) titleMatcher.group(1)?.trim() else null

            if (videoTitle.isNullOrEmpty()) {
                val ogTitleRegex = Pattern.compile("""<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
                val ogMatcher = ogTitleRegex.matcher(html)
                if (ogMatcher.find()) videoTitle = ogMatcher.group(1)?.trim()
            }
            if (videoTitle.isNullOrEmpty()) {
                val pageTitleRegex = Pattern.compile("""<title>([^<]+)</title>""", Pattern.CASE_INSENSITIVE)
                val pageTitleMatcher = pageTitleRegex.matcher(html)
                if (pageTitleMatcher.find()) {
                    videoTitle = pageTitleMatcher.group(1)?.replace("- XNXX.COM", "")?.replace("- XVIDEOS.COM", "")?.trim()
                }
            }
            val finalTitle = (videoTitle?.takeIf { it.isNotBlank() } ?: "Web_Video_${System.currentTimeMillis()}")
                .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(60) + ".mp4"

            val highRegex = Pattern.compile("""(?:html5player\.setVideoUrlHigh|var\s+video_url_high)\s*[\(=]\s*['"]([^'"]+)['"]""")
            val highMatcher = highRegex.matcher(html)
            val highUrl = if (highMatcher.find()) sanitizeMediaUrl(highMatcher.group(1) ?: "") else null

            val lowRegex = Pattern.compile("""(?:html5player\.setVideoUrlLow|var\s+video_url_low)\s*[\(=]\s*['"]([^'"]+)['"]""")
            val lowMatcher = lowRegex.matcher(html)
            val lowUrl = if (lowMatcher.find()) sanitizeMediaUrl(lowMatcher.group(1) ?: "") else null

            val hlsRegex = Pattern.compile("""(?:html5player\.setVideoHLS|var\s+video_hls)\s*[\(=]\s*['"]([^'"]+)['"]""")
            val hlsMatcher = hlsRegex.matcher(html)
            val hlsUrl = if (hlsMatcher.find()) sanitizeMediaUrl(hlsMatcher.group(1) ?: "") else null

            val qualities = mutableListOf<VideoQuality>()
            if (!highUrl.isNullOrEmpty()) {
                qualities.add(VideoQuality("1080p / 720p High Quality", "mp4", 52_000_000L, highUrl))
            }
            if (!hlsUrl.isNullOrEmpty()) {
                qualities.add(VideoQuality("Adaptive HLS (m3u8)", "m3u8", 42_000_000L, hlsUrl))
            }
            if (!lowUrl.isNullOrEmpty()) {
                qualities.add(VideoQuality("360p Standard Quality", "mp4", 22_000_000L, lowUrl))
            }

            if (qualities.isEmpty()) {
                return@withContext extractUniversalVideo(pageUrl, html)
            }

            val primaryUrl = highUrl ?: hlsUrl ?: lowUrl ?: ""
            qualities.add(
                VideoQuality(
                    label = "Audio Only (MP3)",
                    format = "mp3",
                    approximateSize = 5_200_000L,
                    downloadUrl = primaryUrl,
                    isAudioOnly = true
                )
            )

            VideoInfo(
                title = finalTitle,
                sourcePageUrl = pageUrl,
                thumbnailUrl = "",
                durationSeconds = 120,
                qualities = qualities,
                extractedHeaders = mapOf(
                    "User-Agent" to DESKTOP_USER_AGENT,
                    "Referer" to pageUrl
                ),
                isStreamM3u8 = hlsUrl != null && highUrl == null
            )
        } catch (e: Exception) {
            Log.e("ShareNow_Sniffer", "XNXX extract error", e)
            null
        }
    }

    /**
     * 9. Pornhub / MediaDefinitions JSON Extractor
     */
    suspend fun extractPornhubVideo(pageUrl: String, htmlContent: String? = null): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val html = if (!htmlContent.isNullOrBlank()) htmlContent else fetchHtmlWithOkHttp(pageUrl)
            if (html.isBlank()) return@withContext null

            val titleRegex = Pattern.compile("""<title>([^<]+)</title>""", Pattern.CASE_INSENSITIVE)
            val titleMatcher = titleRegex.matcher(html)
            val title = if (titleMatcher.find()) titleMatcher.group(1)?.replace("- Pornhub.com", "")?.trim() else null
            val finalTitle = (title?.takeIf { it.isNotBlank() } ?: "Video_${System.currentTimeMillis()}")
                .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(60) + ".mp4"

            val mediaDefRegex = Pattern.compile("""mediaDefinitions\s*[:=]\s*(\[\s*\{.*?\}\s*\])""")
            val matcher = mediaDefRegex.matcher(html)
            val qualities = mutableListOf<VideoQuality>()

            if (matcher.find()) {
                val jsonStr = matcher.group(1) ?: ""
                try {
                    val jsonArray = JSONArray(jsonStr)
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val vUrl = item.optString("videoUrl", "")
                        val format = item.optString("format", "mp4")
                        val quality = item.optString("quality", "720")
                        if (vUrl.startsWith("http")) {
                            val cleanUrl = sanitizeMediaUrl(vUrl)
                            val label = when {
                                quality.contains("1080") -> "1080p Full HD"
                                quality.contains("720") -> "720p HD"
                                quality.contains("480") -> "480p SD"
                                else -> "$quality Standard"
                            }
                            qualities.add(
                                VideoQuality(
                                    label = label,
                                    format = if (format.contains("hls")) "m3u8" else "mp4",
                                    approximateSize = if (quality.contains("1080")) 60_000_000L else 30_000_000L,
                                    downloadUrl = cleanUrl
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            if (qualities.isEmpty()) return@withContext null

            VideoInfo(
                title = finalTitle,
                sourcePageUrl = pageUrl,
                thumbnailUrl = "",
                durationSeconds = 180,
                qualities = qualities,
                extractedHeaders = mapOf(
                    "User-Agent" to DESKTOP_USER_AGENT,
                    "Referer" to pageUrl
                )
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 10. Universal Video Extractor for ANY Website on the Internet
     * Scans:
     *  - OpenGraph <meta property="og:video"> / og:video:url / og:video:secure_url
     *  - Twitter <meta name="twitter:player:stream">
     *  - JSON-LD Schema.org VideoObject "contentUrl"
     *  - <video src="..."> and <source src="..."> tags
     *  - Embedded direct .mp4 / .m3u8 URLs in scripts and page HTML
     */
    suspend fun extractUniversalVideo(pageUrl: String, htmlContent: String? = null): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val html = if (!htmlContent.isNullOrBlank()) htmlContent else fetchHtmlWithOkHttp(pageUrl)
            if (html.isBlank()) return@withContext null

            var pageTitle: String? = null
            val ogTitleMatcher = Pattern.compile("""<meta\s+(?:property|name)=["']og:title["']\s+content=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(html)
            if (ogTitleMatcher.find()) {
                pageTitle = ogTitleMatcher.group(1)?.trim()
            }
            if (pageTitle.isNullOrEmpty()) {
                val titleMatcher = Pattern.compile("""<title>([^<]+)</title>""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (titleMatcher.find()) {
                    pageTitle = titleMatcher.group(1)?.trim()
                }
            }
            val finalTitle = (pageTitle?.takeIf { it.isNotBlank() } ?: "Web_Video_${System.currentTimeMillis()}")
                .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(60) + ".mp4"

            val foundUrls = LinkedHashSet<String>()

            fun addCandidate(rawUrl: String?) {
                if (rawUrl.isNullOrBlank()) return
                var clean = sanitizeMediaUrl(rawUrl)
                if (clean.startsWith("//")) {
                    clean = "https:$clean"
                } else if (clean.startsWith("/")) {
                    try {
                        val u = android.net.Uri.parse(pageUrl)
                        if (u.scheme != null && u.host != null) {
                            clean = "${u.scheme}://${u.host}$clean"
                        }
                    } catch (_: Exception) {}
                }
                if (!clean.startsWith("http")) return
                if (isAdOrBannerVideoUrl(clean)) return
                val lower = clean.lowercase()
                if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".gif") ||
                    lower.endsWith(".webp") || lower.endsWith(".svg") || lower.endsWith(".js") ||
                    lower.endsWith(".css") || lower.contains("preview") || lower.contains("thumb") ||
                    lower.contains("youtube.com/embed/") || lower.contains("youtube.com/watch") ||
                    lower.contains("youtu.be/") || lower.contains("player.vimeo.com/video/") ||
                    lower.contains("/embed/") || lower.contains("trailer")
                ) {
                    return
                }
                val isActualMediaStream = lower.contains(".mp4") || lower.contains(".m3u8") ||
                    lower.contains(".webm") || lower.contains(".mpd") ||
                    lower.contains("cdninstagram.com") || lower.contains(".fbcdn.net") ||
                    lower.contains("tiktokcdn.com") || lower.contains("video.twimg.com") ||
                    lower.contains("v.redd.it") || lower.contains("vimeocdn.com") ||
                    lower.contains("dmcdn.net") || lower.contains("xhcdn.com") ||
                    lower.contains("growcdnssedge.com") ||
                    lower.contains("xvideos-cdn.com") || lower.contains("phncdn.com") ||
                    lower.contains("googlevideo.com/videoplayback")
                if (!isActualMediaStream) return
                foundUrls.add(clean)
            }

            // A. OpenGraph & Twitter Meta Tags
            val metaRegex = Pattern.compile(
                """<meta\s+[^>]*?(?:property|name)=["'](?:og:video(?::url|:secure_url)?|twitter:player:stream)["'][^>]*?content=["']([^"']+)["']""",
                Pattern.CASE_INSENSITIVE
            )
            val metaMatcher = metaRegex.matcher(html)
            while (metaMatcher.find()) {
                addCandidate(metaMatcher.group(1))
            }

            // B. JSON-LD contentUrl / videoUrl / file / hlsUrl
            val jsonUrlRegex = Pattern.compile(
                """"(?:contentUrl|videoUrl|video_url|playable_url|hlsUrl|mp4Url|streamUrl|file)"\s*:\s*"(https?:\\?/\\?/[^"]+\.(?:mp4|m3u8|webm)[^"]*)"""",
                Pattern.CASE_INSENSITIVE
            )
            val jsonMatcher = jsonUrlRegex.matcher(html)
            while (jsonMatcher.find()) {
                addCandidate(jsonMatcher.group(1))
            }

            // C. HTML5 <video> and <source> tags
            val sourceRegex = Pattern.compile(
                """<(?:video|source)[^>]+src=['"]([^'"]+)['"]""",
                Pattern.CASE_INSENSITIVE
            )
            val sourceMatcher = sourceRegex.matcher(html)
            while (sourceMatcher.find()) {
                val candidate = sourceMatcher.group(1) ?: ""
                if (candidate.contains(".mp4", ignoreCase = true) ||
                    candidate.contains(".m3u8", ignoreCase = true) ||
                    candidate.contains(".webm", ignoreCase = true) ||
                    candidate.contains("video", ignoreCase = true)) {
                    addCandidate(candidate)
                }
            }

            // D. Direct .mp4 / .m3u8 URLs embedded in scripts/JSON (Always scan to catch main video behind pre-roll ads)
            val rawStreamRegex = Pattern.compile(
                """https?:\\?/\\?/[^\s"'<>\\]+\.(?:mp4|m3u8)(?:\?[^\s"'<>\\]*)?""",
                Pattern.CASE_INSENSITIVE
            )
            val rawMatcher = rawStreamRegex.matcher(html)
            while (rawMatcher.find()) {
                addCandidate(rawMatcher.group(0))
                if (foundUrls.size >= 10) break
            }

            if (foundUrls.isEmpty()) return@withContext null

            val qualities = mutableListOf<VideoQuality>()
            var index = 0
            for (u in foundUrls) {
                index++
                val isHls = u.contains(".m3u8", ignoreCase = true)
                val label = when {
                    u.contains("1080") -> "1080p Full HD"
                    u.contains("720") -> "720p HD"
                    u.contains("480") -> "480p SD"
                    u.contains("360") -> "360p Standard"
                    isHls -> "1080p / 720p Adaptive HLS"
                    index == 1 -> "1080p / 720p High Quality"
                    else -> "720p HD Stream #$index"
                }
                qualities.add(
                    VideoQuality(
                        label = label,
                        format = if (isHls) "m3u8" else "mp4",
                        approximateSize = if (isHls) 38_000_000L else 28_000_000L,
                        downloadUrl = u
                    )
                )
            }

            val primaryUrl = qualities.first().downloadUrl
            qualities.add(
                VideoQuality(
                    label = "Audio Only (MP3)",
                    format = "mp3",
                    approximateSize = 4_200_000L,
                    downloadUrl = primaryUrl,
                    isAudioOnly = true
                )
            )

            VideoInfo(
                title = finalTitle,
                sourcePageUrl = pageUrl,
                thumbnailUrl = "",
                durationSeconds = 90,
                qualities = qualities,
                extractedHeaders = mapOf(
                    "User-Agent" to DESKTOP_USER_AGENT,
                    "Referer" to pageUrl
                ),
                isStreamM3u8 = primaryUrl.contains(".m3u8", ignoreCase = true)
            )
        } catch (e: Exception) {
            Log.w("ShareNow_Sniffer", "Universal extract error: ${e.message}")
            null
        }
    }

    /**
     * Master Dispatcher for any URL on the web
     */
    suspend fun extractVideoFromUrl(url: String, htmlContent: String? = null): VideoInfo? {
        val lower = url.lowercase()
        val pathOnly = lower.substringBefore('?').substringBefore('#')
        if (pathOnly.endsWith(".mp4") || pathOnly.endsWith(".m3u8") || pathOnly.endsWith(".webm")) {
            val isHls = pathOnly.endsWith(".m3u8")
            val rawName = url.substringBefore('?').substringAfterLast('/').ifBlank { "Direct_Video.mp4" }
            val cleanName = rawName.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").let {
                if (it.endsWith(".mp4", true)) it else "$it.mp4"
            }
            val qualities = listOf(
                VideoQuality("1080p Full HD", if (isHls) "m3u8" else "mp4", 25_000_000L, url),
                VideoQuality("720p HD", if (isHls) "m3u8" else "mp4", 15_000_000L, url),
                VideoQuality("Audio Only (MP3)", "mp3", 3_500_000L, url, isAudioOnly = true)
            )
            return VideoInfo(
                title = cleanName,
                sourcePageUrl = url,
                thumbnailUrl = "",
                durationSeconds = 60,
                qualities = qualities,
                extractedHeaders = mapOf("User-Agent" to DESKTOP_USER_AGENT),
                isStreamM3u8 = isHls
            )
        }
        return when {
            lower.contains("tiktok.com") -> extractTikTokVideo(url, htmlContent)
            lower.contains("instagram.com") -> extractInstagramVideo(url, htmlContent)
            lower.contains("youtube.com") || lower.contains("youtu.be") -> extractYouTubeVideo(url, htmlContent)
            lower.contains("vimeo.com") -> extractVimeoVideo(url, htmlContent)
            lower.contains("dailymotion.com") || lower.contains("dai.ly") -> extractDailymotionVideo(url, htmlContent)
            lower.contains("twitter.com") || lower.contains("x.com") -> extractTwitterVideo(url, htmlContent)
            lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> extractFacebookVideo(url, htmlContent)
            lower.contains("xnxx.com") || lower.contains("xnxx2.com") || lower.contains("xvideos.com") -> extractXnxxVideo(url, htmlContent)
            lower.contains("pornhub.com") || lower.contains("redtube.com") || lower.contains("youporn.com") -> extractPornhubVideo(url, htmlContent) ?: extractXnxxVideo(url, htmlContent)
            MonetizedDomainRegistry.isMonetizedDomain(url) -> {
                extractPornhubVideo(url, htmlContent) ?: extractXnxxVideo(url, htmlContent)
            }
            else -> {
                extractPornhubVideo(url, htmlContent) ?: extractXnxxVideo(url, htmlContent)
            }
        }
    }

    private fun fetchHtmlWithOkHttp(targetUrl: String): String {
        return try {
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,vi;q=0.8")
            try {
                val cookie = android.webkit.CookieManager.getInstance().getCookie(targetUrl)
                if (!cookie.isNullOrBlank()) reqBuilder.header("Cookie", cookie)
            } catch (_: Exception) {}
            val response: Response = httpClient.newCall(reqBuilder.build()).execute()
            val body = response.body?.string() ?: ""
            response.close()
            body
        } catch (e: Exception) {
            Log.w("ShareNow_Sniffer", "Could not fetch HTML via OkHttp for: $targetUrl", e)
            ""
        }
    }

    /**
     * Probes actual file size (Content-Length / Content-Range) or HLS Master/Media Playlist duration & variants
     */
    suspend fun probeStreamInfo(
        streamUrl: String,
        refererUrl: String? = null,
        userAgent: String? = null
    ): ProbedStreamInfo = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeMediaUrl(streamUrl)
        val lower = cleanUrl.lowercase()
        val isAdUrl = isAdOrBannerVideoUrl(cleanUrl)
        val resolvedReferer = getRefererForUrl(cleanUrl, refererUrl)
        val isYouTubeCdn = lower.contains("googlevideo.com")
        val effectiveUa = when {
            !userAgent.isNullOrBlank() -> userAgent
            isYouTubeCdn && cleanUrl.contains("c=ANDROID") -> "com.google.android.youtube/20.03.35 (Linux; U; Android 14) gzip"
            isYouTubeCdn && cleanUrl.contains("c=IOS") -> "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
            else -> MOBILE_USER_AGENT
        }

        try {
            if (lower.contains(".m3u8")) {
                val reqBuilder = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", effectiveUa)
                if (!isYouTubeCdn && !resolvedReferer.isNullOrBlank()) {
                    reqBuilder.header("Referer", resolvedReferer)
                }
                try {
                    val cookie = android.webkit.CookieManager.getInstance().getCookie(cleanUrl)
                    if (!cookie.isNullOrBlank() && !isYouTubeCdn) reqBuilder.header("Cookie", cookie)
                } catch (_: Exception) {}

                val res = httpClient.newCall(reqBuilder.build()).execute()
                val playlistText = res.body?.string() ?: ""
                res.close()

                if (playlistText.contains("#EXT-X-STREAM-INF")) {
                    val variants = mutableListOf<VideoQuality>()
                    val lines = playlistText.lines()
                    var i = 0
                    while (i < lines.size) {
                        val line = lines[i].trim()
                        if (line.startsWith("#EXT-X-STREAM-INF")) {
                            val bwMatch = Pattern.compile("BANDWIDTH=(\\d+)").matcher(line)
                            val resMatch = Pattern.compile("RESOLUTION=(\\d+x\\d+)").matcher(line)
                            val bandwidth = if (bwMatch.find()) bwMatch.group(1)?.toLongOrNull() ?: 1_500_000L else 1_500_000L
                            val resolution = if (resMatch.find()) resMatch.group(1) ?: "" else ""
                            var nextIdx = i + 1
                            while (nextIdx < lines.size && (lines[nextIdx].isBlank() || lines[nextIdx].trim().startsWith("#"))) {
                                nextIdx++
                            }
                            if (nextIdx < lines.size) {
                                val rawVariantUrl = lines[nextIdx].trim()
                                val variantUrl = resolveUrl(cleanUrl, rawVariantUrl)
                                val height = resolution.substringAfter('x', "")
                                val qLabel = when {
                                    height == "2160" -> "2160p 4K HLS"
                                    height == "1440" -> "1440p 2K HLS"
                                    height == "1080" -> "1080p Full HD HLS"
                                    height == "720" -> "720p HD HLS"
                                    height == "480" -> "480p SD HLS"
                                    height == "360" -> "360p Standard HLS"
                                    height == "240" -> "240p Low HLS"
                                    height == "144" -> "144p Data Saver HLS"
                                    height.isNotEmpty() -> "${height}p HLS"
                                    variantUrl.contains("1080p", ignoreCase = true) -> "1080p Full HD HLS"
                                    variantUrl.contains("720p", ignoreCase = true) -> "720p HD HLS"
                                    variantUrl.contains("480p", ignoreCase = true) -> "480p SD HLS"
                                    variantUrl.contains("240p", ignoreCase = true) -> "240p Low HLS"
                                    else -> "Adaptive HLS (${bandwidth / 1000} kbps)"
                                }
                                val estimatedBytes = ((bandwidth * 180L) / 8L).coerceAtLeast(5_000_000L)
                                if (variants.none { it.downloadUrl == variantUrl }) {
                                    variants.add(VideoQuality(qLabel, "m3u8", estimatedBytes, variantUrl))
                                }
                            }
                            i = nextIdx
                        }
                        i++
                    }
                    val bestSize = variants.maxOfOrNull { it.approximateSize } ?: 35_000_000L
                    return@withContext ProbedStreamInfo(
                        sizeBytes = bestSize,
                        durationSeconds = 180,
                        isLikelyAd = isAdUrl,
                        hlsVariants = variants
                    )
                } else if (playlistText.contains("#EXTINF:")) {
                    var totalDur = 0.0
                    var segCount = 0
                    val extMatcher = Pattern.compile("#EXTINF:([0-9.]+)").matcher(playlistText)
                    while (extMatcher.find()) {
                        totalDur += extMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                        segCount++
                    }
                    val durSec = totalDur.toInt().coerceAtLeast(1)
                    val estBytes = (durSec * 320_000L).coerceAtLeast(segCount * 250_000L).coerceAtLeast(512_000L)
                    val likelyAd = isAdUrl || (durSec in 1..20 && segCount <= 6)
                    return@withContext ProbedStreamInfo(
                        sizeBytes = estBytes,
                        durationSeconds = durSec,
                        isLikelyAd = likelyAd
                    )
                }
            }

            // Direct MP4 / WebM / Audio stream: Range probe (bytes=0-1)
            val metaEfg = parseMetaEfgInfo(cleanUrl)
            val rangeReqBuilder = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", effectiveUa)
                .header("Range", "bytes=0-1")
            if (!isYouTubeCdn && !resolvedReferer.isNullOrBlank()) {
                rangeReqBuilder.header("Referer", resolvedReferer)
            }
            try {
                val cookie = android.webkit.CookieManager.getInstance().getCookie(cleanUrl)
                if (!cookie.isNullOrBlank() && !isYouTubeCdn) rangeReqBuilder.header("Cookie", cookie)
            } catch (_: Exception) {}

            val rangeRes = probeHttpClient.newCall(rangeReqBuilder.build()).execute()
            val contentRange = rangeRes.header("Content-Range") ?: ""
            val contentLength = rangeRes.header("Content-Length")?.toLongOrNull() ?: -1L
            rangeRes.close()

            var resolvedBytes = 0L
            if (contentRange.contains("/")) {
                val totalPart = contentRange.substringAfterLast('/').trim()
                resolvedBytes = totalPart.toLongOrNull() ?: 0L
            }
            if (resolvedBytes <= 2L && contentLength > 2L) {
                resolvedBytes = contentLength
            }

            if (resolvedBytes <= 2L) {
                val headReqBuilder = Request.Builder()
                    .url(cleanUrl)
                    .head()
                    .header("User-Agent", effectiveUa)
                if (!isYouTubeCdn && !resolvedReferer.isNullOrBlank()) {
                    headReqBuilder.header("Referer", resolvedReferer)
                }
                val headRes = probeHttpClient.newCall(headReqBuilder.build()).execute()
                val headLen = headRes.header("Content-Length")?.toLongOrNull() ?: 0L
                headRes.close()
                if (headLen > 0L) resolvedBytes = headLen
            }

            if (resolvedBytes <= 2L && metaEfg.durationSec > 0 && metaEfg.bitrate > 0L) {
                resolvedBytes = ((metaEfg.durationSec.toLong() * metaEfg.bitrate) / 8L).coerceAtLeast(256_000L)
            }

            val likelyAd = isAdUrl || (resolvedBytes in 1L..2_000_000L && (lower.contains("ad") || lower.contains("promo") || lower.contains("spot") || lower.contains("preroll")))
            ProbedStreamInfo(
                sizeBytes = resolvedBytes,
                durationSeconds = metaEfg.durationSec,
                isLikelyAd = likelyAd
            )
        } catch (e: Exception) {
            Log.d("ShareNow_Sniffer", "Probe stream size fallback for $cleanUrl: ${e.message}")
            val metaEfg = parseMetaEfgInfo(cleanUrl)
            val fallbackSize = if (metaEfg.durationSec > 0 && metaEfg.bitrate > 0L) {
                ((metaEfg.durationSec.toLong() * metaEfg.bitrate) / 8L).coerceAtLeast(256_000L)
            } else 0L
            ProbedStreamInfo(sizeBytes = fallbackSize, durationSeconds = metaEfg.durationSec, isLikelyAd = isAdUrl)
        }
    }

    private fun resolveUrl(baseUrl: String, relativeOrFull: String): String {
        val trimmed = relativeOrFull.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("//")) return "https:$trimmed"
        return try {
            java.net.URI(baseUrl).resolve(trimmed).toString()
        } catch (_: Exception) {
            val base = baseUrl.substringBefore('?').substringBeforeLast('/')
            "$base/$trimmed"
        }
    }
}

