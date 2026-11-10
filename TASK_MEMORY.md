# PROJECT MEMORY & TASK LOG: SHARE NOW

## 1. Project Identity & Build Metadata
- **Application Name:** Share Now
- **Application ID / Package Name:** `com.nextai.sharenow`
- **Namespace:** `com.nextai.sharenow`
- **Current Version Code:** `16`
- **Current Version Name:** `1.6`
- **Target SDK:** Android 16 (API Level 36) | **Compile SDK:** 36 | **Min SDK:** 24
- **Android Gradle Plugin (AGP):** `8.6.0` (Hỗ trợ 16 KB zip-alignment)
- **Gradle Wrapper:** `8.9` (`org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m`)
- **NDK Version:** `28.2.13676358` (NDK r28.2 mặc định hỗ trợ 16 KB ELF alignment `2**14`)
- **Supported ABIs:** `arm64-v8a` (100% 16 KB memory page aligned)
- **Release Keystore:** `Convert/Android/keystore/sharenow-release.jks` (alias: `sharenow`, pass: `sharenow2026`)
- **Worktree / Fork:** `F:\APP\Share File\Share it\Work tree-download-game`

---

## 2. Key Directories & Project Structure
- **Android App Root:** `Convert/Android`
  - `build.gradle.kts`: Root Gradle config (AGP 8.6.0, Kotlin 2.0.21, GMS 4.4.2, Crashlytics 3.0.2)
  - `app/build.gradle.kts`: App module config (API 36, R8 Minify, Billing 9.0.0, MMKV 2.0.2)
  - `app/src/main/AndroidManifest.xml`: Permissions, Queries, FileProvider authority `com.nextai.sharenow.fileprovider`
  - `app/src/main/cpp/`: C++ JNI core (`native-core.cpp`, `CMakeLists.txt` với cờ 16 KB)
  - `app/src/main/kotlin/com/ushareit/`: 100% Kotlin architecture
    - `core/ads/AdMediationManager.kt`: Quản lý toàn bộ vòng đời AdMob (Banner, Collapsible Banner, Native, Interstitial, App Open, Rewarded)
    - `core/analytics/FirebaseAnalyticsManager.kt`: Bộ theo dõi sự kiện Firebase & AdMob Revenue
    - `core/subscription/BillingManager.kt`: Quản lý Google Play Billing v9 & xác thực giao dịch thật (`verifyActivePurchases`)
    - `core/downloader/SnifferEngine.kt`: Bộ phân tích & bóc tách luồng video/audio đa nền tảng (YouTube, TikTok, Facebook, Instagram, X, Vimeo, Dailymotion, HLS/MP4)
    - `core/downloader/DownloadEngine.kt`: Bộ tải đa luồng, ghép сегment HLS & trộn âm thanh `MediaMuxer` (`muxVideoAndAudio`)
    - `core/downloader/MonetizedDomainRegistry.kt`: Quản lý hạn mức 3 video miễn phí trên danh sách 1,822 website (`assets/monetized_domains.txt`) + tên miền mirror/CDN
    - `ui/browser/BrowserTabController.kt`: Trình duyệt tích hợp Sniffer Bubble, AdBlocker, Tab Manager, Video Detected Modal & Downloads Manager
    - `core/p2p/`: Turbo P2P socket engine (Wi-Fi Direct / Hotspot)
    - `ui/webshare/WebShareServer.kt` & `WebShareService.kt`: Foreground Service chia sẻ file qua PC liên tục
  - `BUILD_LOG.md`: Nhật ký chi tiết các lần build và mã SHA/bằng chứng xác thực
- **Bộ Công Cụ Phân Tích Dữ Liệu GA4 / Firebase:** `GA4/`
  - `pull_ga4_events.py`: Kết nối trực tiếp GA4 Data API Property `558520028` qua Service Account kéo 6/6 báo cáo
  - `revenue_optimizer.py`: Phân tích eCPM, ROAS, phễu UX và xuất khuyến nghị tối ưu hóa
  - `generate_dashboard.py`: Sinh giao diện Dashboard HTML tương tác (`dashboard.html`)
  - `run_ga4_pipeline.bat` & `run_ga4_pipeline.ps1`: Script 1-click tự động chạy toàn bộ luồng

---

## 3. Ghi Nhớ Lỗi Thường Gặp & Bộ Quy Tắc Phát Hành Google Play (BẮT BUỘC)

### Lỗi 1: Target API Level quá cũ (< 36)
- **Quy tắc:** `compileSdk = 36`, `targetSdk = 36`. Tuyệt đối không hạ thấp `targetSdk` dưới 36.

### Lỗi 2: Tuân thủ chính sách Ảnh & Video (Photo and Video Permissions Policy)
- **Quy tắc:** Đã gỡ bỏ hoàn toàn `READ_MEDIA_IMAGES` và `READ_MEDIA_VIDEO` từ manifest và code từ v12 để tuân thủ chính sách Google Play API 33+.

### Lỗi 3: "Your app does not support 16 KB memory page sizes"
- **Quy tắc:**
  - AGP `8.6.0`, NDK `28.2.13676358`.
  - Cmake arguments: `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`.
  - Cmake Linker flags: `-Wl,-z,max-page-size=16384`.
  - MMKV dùng `com.tencent:mmkv:2.0.2` (đã recompile 16 KB).
  - Lệnh xác minh: `llvm-objdump -p <so-file> | findstr LOAD` (phải chứa `align 2**14`), `zipalign -c -v -P 16 4 app-release.apk` (`Verification successful`).

### Lỗi 4: Dung lượng file AAB (> 500 MB) & Bộ nhớ Gradle R8
- **Quy tắc:** Bật `isMinifyEnabled = true`, `isShrinkResources = true`. Cấu hình `org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m` trong `gradle.properties` để tránh lỗi `JVM garbage collector is thrashing` khi chạy R8. Kích thước hiện tại: AAB ~16.61 MB, APK ~14.00 MB.

### Lỗi 5: Tiêu chuẩn mã hóa file tiếng Việt / XML (Tránh lỗi AAPT2 BOM)
- **Quy tắc:** Khi ghi file XML/Kotlin từ PowerShell, luôn dùng UTF-8 **không có BOM** (`[System.Text.UTF8Encoding]::new($false)`). UTF-8 BOM (`0xEF, 0xBB, 0xBF`) sẽ làm AAPT2 văng lỗi `Resource and asset merger: Cannot read field "elmName" because "root" is null`.

### Lỗi 6: Chụp ảnh màn hình thiết bị qua ADB trên PowerShell
- **Quy tắc:** Không dùng `adb exec-out screencap -p > file.png` vì PowerShell tự động mã hóa lại stdout sang UTF-16LE làm hỏng header PNG. Luôn dùng: `adb shell screencap -p /sdcard/tmp.png; adb pull /sdcard/tmp.png <local_path>`.

---

## 4. Lịch Sử Phiên Bản & Chi Tiết Các Thay Đổi (Release History)

| Phiên bản | versionCode | versionName | Thời gian build | Trạng thái | Điểm nhấn kỹ thuật |
|---|---|---|---|---|---|
| **v16** | **16** | **1.6** | 2026-11-05 20:41 | **RELEASE READY** | • **Bật Payment Wall chuẩn xác sau 3 video miễn phí (`MonetizedDomainRegistry.kt`):** Bổ sung `canDownloadMonetizedVideo()` kiểm tra ngay khi bấm nút **DOWNLOAD NOW** (`btnConfirmDownload`) và **Preview** (`btnPlayPreview`), chặn tải/mở từ video thứ 4 trở đi và bật ngay `PaywallActivity`.<br>• **Nhận diện đầy đủ Mirror & CDN của 1,822 Website (`monetized_domains.txt`):** Đối chiếu thêm gốc tên miền (`domainStemSet`), TLD nhạy cảm (`.xxx`, `.sex`, `.porn`, `.adult`, `.desi`) và các CDN phát luồng thực tế (`cdnsolutions.media`, `sacdnssedge.com`, `xhstream.com`, `xhcdn.com`, `bcvcdn.com`, `phncdn.com`, v.v.); loại trừ whitelist các nền tảng phổ thông.<br>• **Xác thực VIP thật từ Google Play (`BillingManager.kt`):** Bổ sung `verifyActivePurchases()` đồng bộ trạng thái gói đăng ký thực tế từ Google Play Store, loại bỏ cơ chế giả lập VIP khi test.<br>• **Khắc phục triệt để mất tiếng Video tải về (`DownloadEngine.kt`, `SnifferEngine.kt`):** Tự động thu hoạch luồng audio DASH (`onAudioStreamHarvested`) trên Facebook/Instagram/YouTube và ghép âm thanh bằng `MediaMuxer` (`muxVideoAndAudio`) khi video tải về chỉ có hình.<br>• **Chống tải đúp & Tối ưu tốc độ Check dung lượng (1.8s):** Khử trùng lặp theo `videoId` trong `parseMetaEfgInfo`, giới hạn tối đa 2 biến thể HLS và giảm timeout probe xuống 1,800ms; hiển thị chữ màu đỏ (`R.color.red_danger`) khi dòng video đang ở trạng thái `Checking...`.<br>• **Sửa điều hướng Tab/Login/Reels & Google reCAPTCHA trên WebView (`BrowserTabController.kt`):** Cho phép click mượt vào modal Đăng nhập, chuyển tab Reels/Watch/Shorts trên Facebook, YouTube, TikTok, Instagram, X, Dailymotion; mở khóa đầy đủ lưới ảnh 4x4 của Google reCAPTCHA (`recaptcha/api2`, `gstatic.com/recaptcha`).<br>• Đồng bộ toàn bộ 13 ngôn ngữ hiển thị nhãn `v16`. |
| **v15** | 15 | 1.5 | 2026-11-04 14:48 | Đã phát hành | • Cải tiến bộ lọc luồng video mạng xã hội và cập nhật giao diện danh sách Video Detected.<br>• Đồng bộ 13 ngôn ngữ hiển thị nhãn `v15`. |
| **v14** | 14 | 1.4 | 2026-11-03 18:17 | Đã phát hành | • **Module Browser & Video Downloader tích hợp hoàn chỉnh:** Nút Bubble nổi kéo thả, tự động bắt link multi-video kèm dung lượng file (MP4/HLS/MP3).<br>• **Chặn quảng cáo toàn diện & chống crash WebView:** Chặn ad-network/popup/redirect, xử lý `onRenderProcessGone`.<br>• **Đo lường Firebase Analytics Browser (17 events).** |
| **v13** | 13 | 1.3 | 2026-10-31 12:29 | Đã phát hành | • Bắt Firebase Events: `click_card_send`, `click_card_receive`, `in_app_purchased`.<br>• AdMob: Collapsible Banner, Native Ad thư mục rỗng, App Open Ad, Interstitial menu 3 chấm. |
| **v12** | 12 | 1.2 | 2026-10-30 18:30 | Đã phát hành | Gỡ bỏ hoàn toàn `READ_MEDIA_IMAGES` và `READ_MEDIA_VIDEO` đáp ứng chính sách Google Play. |
| **v11** | 11 | 0.1.11 | 2026-10-30 09:11 | Đã phát hành | Key AdMob Production thật 100%, WebShare Foreground Service chạy nền, fix chuyển ngôn ngữ. |
| **v10** | 10 | 0.1.10 | 2026-10-27 16:35 | Đã hoàn thành | 16 KB page-aligned (ELF `2**14` + zipalign OK), Target SDK 36, Billing v9. |
| **v9 - v7**| 9 - 7 | 0.1.9 | 2026-10-27 | Lưu kho | Nâng cấp AGP 8.6.0, CMake flags, gỡ quyền nguy hiểm `QUERY_ALL_PACKAGES`. |

---

## 5. Dữ Liệu Thực Tế Từ Firebase GA4 & Hiệu Suất Quảng Cáo AdMob (Ghi Nhận Ngày 02-03/11/2026)

### A. Chỉ số tổng hợp (30 ngày gần nhất)
- **Tổng người dùng (First Open):** 120 users
- **Lượt mở ứng dụng:** 541 lần (TB 4.5 lần/user)
- **Doanh thu AdMob ghi nhận:** $0.4681 USD
- **Lượt hiển thị quảng cáo (Impressions):** 1,294 lượt
- **Lượt click quảng cáo:** 35 clicks (**CTR TB: 2.70%**)
- **eCPM trung bình:** $0.36 USD

### B. Hiệu suất theo định dạng quảng cáo
- **Banner (Collapsible):** Doanh thu $0.1405 | 660 imp | CTR 1.5% | eCPM $0.21 (Đóng góp doanh thu lớn nhất).
- **Native Ad:** Doanh thu $0.1249 | 398 imp | CTR 1.5% | eCPM $0.31 (Hiệu quả tăng mạnh nhờ vị trí thư mục rỗng).
- **App Open Ad:** Doanh thu $0.1235 | 178 imp | **CTR 7.3%** | **eCPM $0.69** (Tỷ lệ click và giá trị rất cao khi mở từ nền).
- **Interstitial (Full Screen):** Doanh thu $0.0772 | 54 imp | **CTR 11.1%** | **eCPM $1.43** (Định dạng có giá trị cao nhất).
- **Rewarded Video:** Doanh thu $0.0020 | 4 imp | eCPM $0.51.

### C. Phân bổ người dùng theo quốc gia
- **Ấn Độ (India):** 43 users (35.8%) | 7,752 events | Doanh thu $0.3318 (Chiếm 70% doanh thu app, chiến dịch Google Ads mang lại 775 conversions).
- **Việt Nam:** 16 users (13.3%) | 3,988 events | Doanh thu $0.1031.
- **Hoa Kỳ (United States):** 8 users (6.7%) | 673 events | Doanh thu $0.0325 (**eCPM $1.20 - $2.50**, gấp 5-8 lần châu Á).

---

## 6. Thiết Bị Kiểm Thử Thực Tế (Hardware Profiles)
- **Thiết bị chính:** Samsung Galaxy A07 (`SM-A075F`)
  - IP kết nối ADB Wireless: `192.168.1.8:5555` (trước đó: `192.168.1.10:35279`, `192.168.1.10:37185`)
  - Đã cài đặt & xác thực trực tiếp bản **v16 (`versionCode=16`, `versionName=1.6`)**:
    - Đăng nhập & chuyển tab Facebook (`Log in`, `Reels`), YouTube, Dailymotion mượt mà.
    - Hiển thị đầy đủ lưới ảnh xác thực 4x4 của Google reCAPTCHA (`recaptcha/api2/demo`).
    - Tự động chặn và bật màn hình **Share Now Premium VIP (`PaywallActivity`)** ngay khi tải hoặc mở quá 3 video miễn phí từ các trang trong danh sách 1,822 website (`xhstream.com` / `xhamster`).
- **Thiết bị phụ:** Samsung Galaxy A50s (`SM-A507FN`, Android 11)
  - IP ADB: `192.168.1.13:37513`
  - Đã cài đặt đồng bộ bản **v16 (`versionCode=16`, `versionName=1.6`)** thành công (`Success`).

---

## 7. Bàn Giao File Bản Phát Hành Hiện Tại (`v16 - 1.6`)
- **AAB Phát Hành (Google Play Console):** `Convert/Android/app/build/outputs/bundle/release/app-release.aab` (`17,412,917 bytes` ~ **16.61 MB**)
- **APK Release Signed:** `Convert/Android/app/build/outputs/apk/release/app-release.apk` (`14,684,935 bytes` ~ **14.00 MB**)
- **Xác thực 16 KB Memory Page Alignment (`zipalign -c -v -P 16 4`):**
  - `lib/arm64-v8a/libmmkv.so (OK)`
  - `lib/arm64-v8a/libshareit_native_core.so (OK)`
  - `Verification successful`

---

## 8. Chi Tiết Kỹ Thuật Trọng Tâm Bản `v16 (1.6)`
1. **Ghép Âm Thanh Tự Động Cho Luồng Video DASH (`DownloadEngine.kt` & `SnifferEngine.kt`):**
   - Nhận diện thông số `efg` trên URL CDN của Facebook/Instagram (`parseMetaEfgInfo`) để phân biệt luồng video-only (`dash_vp9`, `dash_h264`) và audio-only (`dash_ln_heaac_vbr3_audio`).
   - Khi tải xong file MP4, kiểm tra `hasAudioTrack(targetFile)`. Nếu thiếu track âm thanh, tự động tìm luồng audio tương ứng (`findCompanionAudioUrl` hoặc giải mã qua API) và trộn bằng `MediaMuxer` (`muxVideoAndAudio`) để đảm bảo 100% video tải về có đầy đủ âm thanh.
2. **Tối Ưu Tốc Độ Kiểm Tra Dung Lượng & Giao Diện Modal (`BrowserTabController.kt`):**
   - Trong lúc đang kiểm tra dung lượng (`!item.isSizeProbed && item.sizeBytes <= 0L`), toàn bộ tiêu đề, thông tin phụ và chữ `Checking...` hiển thị màu đỏ (`R.color.red_danger`); ngay khi kiểm tra xong tự động chuyển về màu chuẩn.
   - Giảm timeout probe xuống `1,800ms`, giới hạn tối đa 2 biến thể HLS và bỏ qua probe mạng cho các trang đã có metadata trực tiếp để modal phản hồi tức thì.
   - Chống tải đúp (`enqueueDownload` & `addOrUpdateDetectedVideoItem`) bằng cách chuẩn hóa URL bỏ `bytestart`/`byteend` và đối chiếu `videoId`.
3. **Tương Thích Điều Hướng Đa Nền Tảng & Google reCAPTCHA (`BrowserTabController.kt`):**
   - Loại bỏ CSS `pointer-events: none` quá rộng và cho phép các `intent://` / custom scheme vô hại của mạng xã hội fallback mượt về WebView mà không mở trang lỗi.
   - Loại trừ toàn bộ tài nguyên `google.com/recaptcha`, `gstatic.com/recaptcha`, `recaptcha.net` khỏi bộ lọc chặn quảng cáo để khung xác thực hình ảnh CAPTCHA 3x3 / 4x4 luôn tải đầy đủ 100%.
4. **Kiểm Soát Hạn Mức 3 Video Miễn Phí & Bật Payment Wall (`MonetizedDomainRegistry.kt`, `BillingManager.kt`):**
   - Kiểm tra hạn mức `MAX_FREE_MONETIZED_VIDEOS = 3` tại cả thời điểm bấm **DOWNLOAD NOW** (`canDownloadMonetizedVideo`), bấm **Preview**, bấm **OPEN / Play** và **Save to Gallery** (`canOpenAndConsumeMonetizedVideo`).
   - Không tự động xuất ngầm video từ các trang monetized ra thư mục Gallery công khai nếu người dùng chưa đăng ký VIP (`onDownloadSuccessSyncMedia`).
   - Xác thực trạng thái VIP trực tiếp từ Google Play (`BillingManager.verifyActivePurchases()`) để đảm bảo Payment Wall luôn hoạt động chính xác.
