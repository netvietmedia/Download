# BUILD LOG & RELEASE HISTORY: SHARE NOW

## App Metadata
- **App Name:** Share Now
- **Application ID:** `com.nextai.sharenow`
- **Current Version Code:** `16`
- **Current Version Name:** `1.6`
- **Target SDK:** `36` (Android 16) | **Compile SDK:** `36` | **Min SDK:** `24`
- **AGP Version:** `8.6.0` | **Gradle:** `8.9` | **NDK:** `28.2.13676358`
- **Release Keystore:** `keystore/sharenow-release.jks` (alias: `sharenow`)

---

## Release History & Changes Log

### [v16] - Version Code: 16 | Version Name: 1.6 (2026-11-05)
- **Status:** **CURRENT PRODUCTION RELEASE CANDIDATE (RELEASE-READY)**
- **Artifacts:**
  - AAB: `Convert/Android/app/build/outputs/bundle/release/app-release.aab` (17,412,917 bytes ~ 16.61 MB)
  - APK: `Convert/Android/app/build/outputs/apk/release/app-release.apk` (14,684,935 bytes ~ 14.00 MB)
- **16 KB Memory Page Alignment Verification:**
  ```text
  zipalign.exe -c -v -P 16 4 app-release.apk
   lib/arm64-v8a/libmmkv.so (OK - 16 KB aligned)
   lib/arm64-v8a/libshareit_native_core.so (OK - 16 KB aligned)
  Verification successful
  ```
- **Key Updates & Enhancements:**
  1. **Kích hoạt Payment Wall chuẩn xác sau 3 video miễn phí trên 1,822 Website Monetized (`MonetizedDomainRegistry.kt`, `BrowserTabController.kt`, `DownloadEngine.kt`):**
     - Bổ sung `canDownloadMonetizedVideo()` kiểm tra ngay tại nút **DOWNLOAD NOW (`btnConfirmDownload`)**, **Preview (`btnPlayPreview`)** và `extractVideoDirectly()`.
     - Cho phép tải và mở miễn phí tối đa **3 video đầu tiên** (`MAX_FREE_MONETIZED_VIDEOS = 3`); từ **video thứ 4 trở đi** tự động đóng modal và bật ngay màn hình **Share Now Premium VIP (`PaywallActivity`)**.
     - Mở rộng bộ nhận diện tên miền trong `MonetizedDomainRegistry.kt`: khớp chính xác 1,822 domain trong `assets/monetized_domains.txt`, đối chiếu gốc tên miền (`domainStemSet`) cho các domain mirror đổi đuôi/số (`xhamster46.desi`, `xhstream.com`, `xnxx115.com`, `vlxx.bz`, v.v.) và các CDN phát luồng thực tế (`cdnsolutions.media`, `sacdnssedge.com`, `xhcdn.com`, `bcvcdn.com`, `phncdn.com`), đồng thời loại trừ an toàn danh sách nền tảng phổ thông (`nonMonetizedMainstreamAllowlist`).
     - Không tự động xuất ngầm video tải từ trang monetized ra thư mục Gallery công khai nếu tài khoản chưa nâng cấp VIP (`onDownloadSuccessSyncMedia`).
  2. **Xác thực gói VIP thực tế từ Google Play Billing (`BillingManager.kt`):**
     - Bổ sung `verifyActivePurchases()` truy vấn trực tiếp `SUBS` và `INAPP` từ Google Play Store khi khởi tạo để đồng bộ trạng thái VIP thật và xóa bỏ trạng thái VIP giả lập khi test trước đây.
  3. **Khắc phục triệt để lỗi tải video mất tiếng (`DownloadEngine.kt`, `SnifferEngine.kt`):**
     - Tự động thu hoạch luồng audio DASH (`onAudioStreamHarvested`) trên Facebook/Instagram/YouTube và dùng `MediaMuxer` (`muxVideoAndAudio`) ghép hình + tiếng khi luồng video tải về là DASH video-only.
  4. **Chống tải đúp, hiển thị chữ Đỏ khi `Checking...` & tối ưu tốc độ kiểm tra dung lượng:**
     - Chuẩn hóa URL bỏ tham số `bytestart`/`byteend` và khử trùng theo `videoId` (`parseMetaEfgInfo`) để ngăn chặn tạo tác vụ tải đúp.
     - Toàn bộ dòng video đang kiểm tra dung lượng (`Checking...`) hiển thị màu đỏ (`R.color.red_danger`) và chuyển về màu chuẩn ngay khi hoàn tất probe.
     - Giảm thời gian chờ probe xuống `1,800ms` và giới hạn tối đa 2 biến thể HLS giúp danh sách video phản hồi nhanh tức thì.
  5. **Sửa điều hướng Tab/Login/Reels & hiển thị đầy đủ ảnh Google reCAPTCHA (`BrowserTabController.kt`):**
     - Xử lý mượt các tương tác modal Đăng nhập, chuyển tab Reels/Watch/Shorts trên Facebook, YouTube, TikTok, Instagram, X, Dailymotion và hiển thị giao diện trang lỗi thân thiện kèm nút **Thử lại** khi mất mạng.
     - Mở khóa hoàn toàn tài nguyên `google.com/recaptcha`, `gstatic.com/recaptcha`, `recaptcha.net` để lưới ảnh xác thực Google reCAPTCHA 3x3 / 4x4 hiển thị đầy đủ 100%.
  6. **Đa ngôn ngữ & Kiểm thử thiết bị thực tế:**
     - Cập nhật đồng bộ nhãn `me_version` thành `v16` trên cả 13 ngôn ngữ (`values*/strings.xml`).
     - Đã cài đặt và kiểm thử trực tiếp thành công trên **Samsung Galaxy A07 (`192.168.1.8:5555`)** và **Samsung Galaxy A50s (`192.168.1.13:37513`)**.

---

### [v15] - Version Code: 15 | Version Name: 1.5 (2026-11-04)
- **Status:** SUPERSEDED by v16
- **Artifacts:**
  - AAB: `Convert/Android/app/build/outputs/bundle/release/app-release.aab` (17,384,714 bytes ~ 16.58 MB)
- **Key Updates & Enhancements:**
  - Nâng cấp bộ lọc luồng video mạng xã hội và cập nhật giao diện danh sách Video Detected.
  - Cập nhật đồng bộ nhãn `me_version` thành `v15` trên cả 13 ngôn ngữ (`values*/strings.xml`).

---

### [v14] - Version Code: 14 | Version Name: 1.4 (2026-11-03)
- **Status:** SUPERSEDED by v15
- **Artifacts:**
  - AAB: `Convert/Android/app/build/outputs/bundle/release/app-release.aab` (17,361,830 bytes ~ 16.56 MB)
  - APK: `Convert/Android/app/build/outputs/apk/release/app-release.apk` (14,668,439 bytes ~ 13.99 MB)
- **Key Updates & Enhancements:**
  - Tích hợp hoàn chỉnh Module Browser & Video Downloader, nút Bubble nổi kéo thả, chặn quảng cáo WebView và bộ 17 sự kiện đo lường Firebase Analytics cho Browser.

---

### [v13] - Version Code: 13 | Version Name: 1.3 (2026-10-31)
- **Status:** SUPERSEDED by v14
- **Key Updates & Enhancements:**
  - Bổ sung Firebase Tracking (`click_card_send`, `click_card_receive`, `in_app_purchased`) và tối ưu hóa vị trí quảng cáo AdMob (Collapsible Banner, Native Ad thư mục rỗng, App Open Ad, Interstitial menu 3 chấm).

---

### [v12] - Version Code: 12 | Version Name: 1.2 (2026-10-30)
- **Status:** SUPERSEDED by v13
- **Key Updates & Fixes:**
  - Gỡ bỏ hoàn toàn `READ_MEDIA_IMAGES` và `READ_MEDIA_VIDEO` đáp ứng chính sách Photo & Video Permissions Policy của Google Play.

---

### [v11] - [v7]
- Chuyển 100% sang AdMob Production Key thật, WebShare Foreground Service, chuẩn 16 KB page-alignment, Target SDK 36, Google Play Billing v9.0.0.

---

## Google Play Console 5 Mandatory Verification Checks
| # | Check Item | Requirement | v16 Status | Verification Evidence |
|---|---|---|---|---|
| 1 | Target SDK | >= 36 | **PASS** | `targetSdkVersion: '36'`, `compileSdkVersion: '36'` |
| 2 | Play Billing | Latest (>= 8.0.0 / 9.x) | **PASS** | `com.android.billingclient:billing:9.0.0` |
| 3 | File Size | AAB < 500 MB | **PASS** | `16.61 MB` (`17,412,917 bytes`, R8 shrink + minify enabled) |
| 4 | 16 KB Page Size | NDK r28, AGP 8.6.0+, ELF align 0x4000, zip-align 16 KB | **PASS** | `zipalign -P 16` PASSED (`libmmkv.so`, `libshareit_native_core.so`), `llvm-objdump` `align 2**14` |
| 5 | Security / Permissions | Tuân thủ Photo/Video policy, không dùng quyền nguy hiểm | **PASS** | Gỡ bỏ `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `QUERY_ALL_PACKAGES` |
