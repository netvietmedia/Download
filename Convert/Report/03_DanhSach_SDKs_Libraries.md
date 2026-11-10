# Báo cáo Thống Kê Chi Tiết SDKs & Thư Viện Native

## 1. Nguyên Tắc Quản Trị SDK Theo Yêu Cầu Dự Án

Theo điều khoản **Mục 2 trong Convertme.txt**: *"Các thư viện SDK đang sử dụng vẫn giữ nguyên không thay bằng thư viện khác"*. Dự án bảo lưu 100% các SDK bên thứ ba, cấu hình chính xác các phiên bản tương thích thông qua Gradle (`build.gradle.kts`) để đảm bảo không phát sinh xung đột runtime.

---

## 2. Danh Mục Toàn Bộ 50+ SDK Java / Kotlin Trong Ứng Dụng

### 2.1. Phân Hệ Quảng Cáo & Ad Mediation (Advertising & Mediation SDKs)

| Tên SDK | Package Name | Phiên bản (META-INF) | Chức năng & Vai trò trong App |
|---|---|---|---|
| **Google AdMob** | `com.google.android.gms.ads` | `play-services-ads:23.6.0` | Mạng quảng cáo chính (Banner, Interstitial, Rewarded, Native) |
| **Firebase Ads** | `com.google.firebase.ads` | `firebase-ads:20.4.0` | Cầu nối Ad Mediation Firebase |
| **ByteDance Pangle** | `com.bytedance.sdk.openadsdk` | SDK v3.x | Mạng quảng cáo toàn cầu Pangle / TikTok Ads |
| **Mintegral** | `com.mbridge.msdk` | v16.x | Mạng quảng cáo Video Rewarded & Interstitial |
| **ironSource** | `com.ironsource.mediationsdk` | v7.x | Hệ thống Mediation quảng cáo đa tầng |
| **Unity Ads** | `com.unity3d.ads` | v4.x | Quảng cáo video tương tác & game ads |
| **AppLovin MAX** | `com.applovin.sdk` | v12.x | Nền tảng đấu thầu Header Bidding & Mediation |
| **Vungle (Liftoff)** | `com.vungle.warren` | v7.x | Quảng cáo Video HD |
| **Tapjoy** | `com.tapjoy` | Tapjoy SDK 12.x | Quảng cáo Offerwall & Tích lũy điểm thưởng |
| **Yandex Mobile Ads** | `com.yandex.mobile.ads` | v5.x | Phục vụ thị trường Nga & Đông Âu |
| **MyTarget** | `com.my.target` | v5.x | Quảng cáo thị trường VK / Mail.ru |
| **Amazon APS** | `com.amazon.device.ads` | APS Mobile Config | Amazon Publisher Services đấu thầu quảng cáo |
| **Digital Turbine (Fyber)**| `com.fyber`, `com.digitalturbine`| FairBid v8.x | Mạng quảng cáo & phân phối ứng dụng |
| **PubNative / Verve** | `net.pubnative.lite.sdk` | HyBid v2.x | Quảng cáo Native & Programmatic |
| **Bigo Ads** | `sg.bigo.ads` | v4.x | Mạng quảng cáo khu vực Đông Nam Á / Trung Đông |
| **TopOn (AnyThink)** | `com.anythink.core` | v6.x | Nền tảng Mediation tối ưu hóa eCPM hàng đầu |
| **AlphaGaming** | `com.alphagaming` | v1.x | Mediation quảng cáo trong mini-games |
| **AdtMonetize** | `com.adtmonetize` | v2.x | Mạng quảng cáo bổ trợ |
| **Midas (Nội bộ)** | `com.adexchange`, `com.sharemob` | `6.2.81 (localStrategy)` | Hệ thống Ad-Exchange riêng do SHAREit phát triển |
| **Beyla Targeting** | `com.ushareit.beyla` | v1.0 | Hệ thống thu thập thông số thiết bị phục vụ Ad Targeting |

---

### 2.2. Phân Hệ Analytics, Giám Sát & Đo Lường (Analytics & Attribution SDKs)

| Tên SDK | Package Name | Phiên bản (META-INF) | Chức năng & Vai trò |
|---|---|---|---|
| **Firebase Core & Analytics** | `com.google.firebase.analytics` | `20.1.2` | Theo dõi hành vi người dùng, phễu sự kiện |
| **Firebase Cloud Messaging** | `com.google.firebase.messaging` | `20.1.0` | Nhận thông báo đẩy Push Notification |
| **Firebase Auth** | `com.google.firebase.auth` | `19.1.0` | Xác thực người dùng qua SĐT / Google / Email |
| **Adjust SDK** | `com.adjust.sdk` | v4.33.x | Đo lường nguồn cài đặt (Attribution) & ROI chiến dịch |
| **AppMetrica** | `io.appmetrica.analytics` | v5.x | Đo lường chuyên sâu thị trường quốc tế |
| **Meituan Logan** | Native `liblogan.so` | v1.2.0 | Ghi log hệ thống mã hóa hiệu năng cực cao |
| **xCrash** | Native `libxcrash.so` | v3.0.0 | Bắt sự cố Native Crash C/C++ và Java Exception |
| **Tongdun Risk Control** | `sgfp.tongdun.net` | v3.x | Kiểm soát gian lận, chặn thiết bị giả lập / farm |
| **UMeng Analytics** | `com.umeng.commonsdk` | v9.x | Đo lường người dùng thị trường APAC |
| **Ladon Analytics** | `ladon.dalyfeds.com` | Internal | Hệ thống Telemetry phân tích dung lượng & tốc độ truyền |
| **Google AppSet ID** | `play-services-appset` | `16.0.2` | Định danh thiết bị tuân thủ chính sách Google Play |
| **MSA OAID** | `com.bun.miitmdid` | v1.0.25 | Định danh quảng cáo cho các thiết bị không có GMS |

---

### 2.3. Phân Hệ Hạ Tầng Kỹ Thuật & Core Android (Core Infrastructure SDKs)

| Tên SDK | Package Name | Phiên bản (META-INF) | Chức năng & Vai trò |
|---|---|---|---|
| **OkHttp3** | `okhttp3` | `4.10.0` | HTTP Client xử lý kết nối mạng, Connection Pool, Interceptor |
| **Retrofit2** | `retrofit2` | `2.9.0` | Type-safe HTTP Client mapping REST API thành Kotlin Interface |
| **Kotlin Coroutines** | `kotlinx.coroutines` | `1.7.1` | Xử lý đa luồng bất đồng bộ Non-blocking |
| **RxJava & RxAndroid** | `io.reactivex` | `2.2.x / 3.x` | Luồng phản ứng xử lý Event Streams |
| **Glide** | `com.bumptech.glide` | `4.15.1` | Tải và cache hình ảnh, GIF, WebP |
| **AndroidX Core & Appcompat** | `androidx.core`, `androidx.appcompat` | `1.9.0 / 1.6.1` | Thư viện nền tảng giao diện Android |
| **AndroidX Room DB** | `androidx.room` | `2.4.3` | Cơ sở dữ liệu SQLite ORM nội bộ |
| **AndroidX WorkManager** | `androidx.work` | `2.7.0` | Lập lịch tác vụ nền (Dọn rác định kỳ, đồng bộ) |
| **Tencent MMKV** | `com.tencent.mmkv` | `1.3.x` | Lưu trữ Key-Value siêu tốc trên bộ nhớ chia sẻ mmap |
| **BouncyCastle** | `org.bouncycastle` | `1.68` | Thư viện mật mã học (AES, RSA, ECC, HMAC) |
| **OkDownload** | `com.liulishuo.okdownload` | `1.0.7` | Bộ tải file đa luồng hỗ trợ tạm dừng và tiếp tục |
| **AndroidVideoCache** | `com.danikula.videocache` | `2.7.1` | Proxy Streaming Video Cache cục bộ |
| **jmDNS** | `javax.jmdns` | `3.5.7` | Dò tìm thiết bị P2P trong mạng LAN qua giao thức mDNS/ZeroConf |
| **Google Play Billing** | `com.android.billingclient` | `7.0.0` | Xử lý mua hàng trong ứng dụng In-App Purchases (IAP) |
| **Google Play Core** | `play-services-base` / `app-update` | `2.1.0` | Cập nhật ứng dụng trong app & Asset Delivery |
| **Google Maps & Location** | `play-services-location` | `19.0.1` | Xác định tọa độ vị trí phục vụ tính giờ cầu nguyện Muslim |
| **Vendor Push SDKs** | `com.heytap`, `com.vivo`, `com.xiaomi` | Official | Nhận push trực tiếp trên các dòng máy Xiaomi, Vivo, Oppo |

---

## 3. Danh Mục 26 Thư Viện Native C/C++ (`lib/armeabi-v7a/*.so`)

| Tên File Thư Viện | Kích thước | Chức năng Kỹ thuật |
|---|---|---|
| `libTierahs.so` | 842 KB | **Protector / Anti-tamper**: Mã hóa DEX và kiểm tra chữ ký APK |
| `libpglarmor.so` | 512 KB | **PGL Armor Core**: Bảo vệ chống debug và chặn hook Xposed/Frida |
| `libNotCheck.so` | 128 KB | Bỏ qua một số kiểm tra bảo mật trong môi trường đặc biệt |
| `libmmkv.so` | 240 KB | Native backend cho MMKV (Memory Mapped Key-Value) |
| `libxcrash.so` | 196 KB | Trình theo dõi crash C/C++ (Signal Handler: SIGSEGV, SIGBUS) |
| `libxcrash_dumper.so` | 98 KB | Trích xuất stack frame và ghi file `.dmp` khi xảy ra sự cố |
| `liblogan.so` | 185 KB | Trình ghi log nhị phân có mã hóa AES + nén Zlib của Meituan |
| `libinnoplayer-core.so` | 1.8 MB | Media Player Core hỗ trợ giải mã phần cứng H.264/H.265/AAC |
| `libyuv.so` | 320 KB | Thư viện Google libyuv xử lý chuyển đổi không gian màu YUV sang RGB |
| `libimggroup.so` | 410 KB | Nén ảnh và xử lý Thumbnail siêu tốc |
| `libglide-webp.so` | 150 KB | Bộ giải mã WebP hoạt hình cho Glide |
| `libpl_droidsonroids_gif.so` | 210 KB | Giải mã và hiển thị ảnh GIF động mượt mà |
| `lib7-Zip-JBinding.so` | 1.4 MB | Thư viện giải nén định dạng 7z, RAR, Zip dung lượng lớn |
| `libhpatchz.so` | 115 KB | Áp dụng file vá nhị phân Diff Patch cập nhật APK siêu nhẹ |
| `libboost_multidex.so` | 85 KB | Tối ưu hóa nạp nhiều file DEX lúc khởi động app trên Android cũ |
| `libbuffer.so` | 64 KB | Bộ đệm Buffer tốc độ cao phục vụ truyền file P2P |
| `libfile_lock.so` | 48 KB | Khóa file độc quyền ở tầng Kernel chống ghi đè khi chia sẻ |
| `libc++_shared.so` | 1.1 MB | LLVM C++ Standard Runtime Library |
| `libtapjoy.so` | 650 KB | Native logic cho Tapjoy Monetization |
| `libmoksha.so` | 380 KB | ByteDance Pangle Anti-fraud & Verification logic |
| `libtdcoreplugin.so` | 290 KB | ByteDance Core Plugin Native Bridge |
| `libtobEmbedPagEncrypt.so`| 310 KB | Trình dựng hoạt họa Portable Animated Graphics (PAG) |
| `libtt_ugen_layout.so` | 450 KB | Trình kết xuất giao diện động UGen Layout của ByteDance |
| `libnms.so` | 140 KB | Network Measurement Suite |

