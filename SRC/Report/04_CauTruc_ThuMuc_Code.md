# Báo cáo Cấu Trúc Thư Mục Mã Nguồn (Android Kotlin & Backend/CMS)

## 1. Bản Đồ Cấu Trúc Mã Nguồn Gốc (Decompiled Java Architecture)

Mã nguồn gốc sau khi decompile chứa 45.606 file Java phân chia thành 2 phân vùng kiến trúc lớn:

```
com.lenovo.anyshare.gps (Ứng dụng gốc)
├── com.lenovo.anyshare.*             [~2.000 classes] — Tầng Lõi Truyền File P2P, Safebox, WebShare
├── com.ushareit.*                    [~6.000 classes] — Tầng Nghiệp Vụ, Tiện Ích, Muslim, Paywall
├── com.adexchange.*                  [~1.200 classes] — Hệ thống Quảng cáo Midas & Header Bidding
├── com.anythink.* / ironsource.*     [~2.500 classes] — Mediation Mạng Quảng Cáo Ngoài
├── com.google.* / com.firebase.*     [~4.000 classes] — Bộ SDK Google Play Services & Firebase
├── androidx.*                        [~8.000 classes] — Android Jetpack Support Framework
├── p002si.*                          [~15.000 classes]— Mã nguồn Obfuscated do Packer sinh ra
└── spg.erahsyna.ovonel.moc.*         [~500 classes]   — Lớp bảo vệ chống dịch ngược (Tierahs Protector)
```

---

## 2. Bản Đồ Cấu Trúc Dự Án Android Kotlin Đã Chuyển Đổi (`Convert/Android`)

```
Convert/Android/
├── build.gradle.kts                      # Cấu hình Gradle Root cho toàn dự án
├── settings.gradle.kts                   # Đăng ký các module và kho lưu trữ Maven
├── gradle.properties                     # Tối ưu hóa JVM Heap và cấu hình AndroidX
├── app/
│   ├── build.gradle.kts                  # Dependencies, SDK target, NDK CMake config
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml       # File kê khai Manifest an toàn, chuẩn bảo mật
│   │   │   ├── kotlin/
│   │   │   │   └── com/ushareit/
│   │   │   │       ├── app/              # Application class, Dependency Injection, App Init
│   │   │   │       │   ├── ShareItApplication.kt
│   │   │   │       │   └── AppInitializer.kt
│   │   │   │       ├── core/
│   │   │   │       │   ├── p2p/          # Core P2P Engine: Wi-Fi Direct, Hotspot LOHS, Socket/NIO
│   │   │   │       │   │   ├── P2PManager.kt
│   │   │   │       │   │   ├── WifiDirectTransferEngine.kt
│   │   │   │       │   │   ├── HotspotServerEngine.kt
│   │   │   │       │   │   ├── SocketTransferProtocol.kt
│   │   │   │       │   │   ├── FileChunkProcessor.kt
│   │   │   │       │   │   └── ChecksumVerifier.kt
│   │   │   │       │   ├── webshare/     # WebShare Mini HTTP Server (Chia sẻ qua trình duyệt)
│   │   │   │       │   │   ├── WebShareServer.kt
│   │   │   │       │   │   └── WebShareAssetHandler.kt
│   │   │   │       │   ├── filemanager/  # Bộ quản lý tệp: Quét tệp, phân loại, Storage analyzer
│   │   │   │       │   │   ├── FileManager.kt
│   │   │   │       │   │   ├── FileCategoryScanner.kt
│   │   │   │       │   │   └── StorageStatsProvider.kt
│   │   │   │       │   ├── cleanit/      # Bộ dọn rác: Junk file cleaner, Bộ nhớ đệm, File trùng
│   │   │   │       │   │   ├── JunkCleanEngine.kt
│   │   │   │       │   │   └── MemoryBooster.kt
│   │   │   │       │   ├── safebox/      # Két khóa an toàn AES-GCM Zero-Knowledge Vault
│   │   │   │       │   │   ├── SafeboxVaultManager.kt
│   │   │   │       │   │   └── CryptoEngine.kt
│   │   │   │       │   ├── player/       # SI Media Player (Video & Audio Player Core)
│   │   │   │       │   │   ├── MediaPlayerController.kt
│   │   │   │       │   │   └── AudioPlaybackService.kt
│   │   │   │       │   ├── downloader/   # Trình tải video từ Web & Mạng xã hội
│   │   │   │       │   │   └── WebVideoDownloader.kt
│   │   │   │       │   ├── muslim/       # Muslim Daily: Giờ cầu nguyện, La bàn Qibla, Tasbih
│   │   │   │       │   │   ├── PrayerTimesCalculator.kt
│   │   │   │       │   │   ├── QiblaCompassEngine.kt
│   │   │   │       │   │   └── TasbihCounter.kt
│   │   │   │       │   ├── mcds/         # Dynamic Remote Config Client & Visual Slot Parser
│   │   │   │       │   │   ├── MCDSManager.kt
│   │   │   │       │   │   └── ConfigRuleEngine.kt
│   │   │   │       │   ├── ads/          # Bộ điều phối Quảng cáo đa tầng (Ad Mediation Layer)
│   │   │   │       │   │   ├── AdMediationManager.kt
│   │   │   │       │   │   ├── AdMobAdapter.kt
│   │   │   │       │   │   └── AppLovinAdapter.kt
│   │   │   │       │   ├── subscription/ # Quản lý gói VIP & Google Play Billing Client v7
│   │   │   │       │   │   ├── BillingManager.kt
│   │   │   │       │   │   └── SubscriptionState.kt
│   │   │   │       │   ├── coin/         # Gamification: Điểm danh, Nhiệm vụ & Ví Coin
│   │   │   │       │   │   ├── CoinRewardManager.kt
│   │   │   │       │   │   └── TaskRepository.kt
│   │   │   │       │   └── net/          # Retrofit Client kết nối Backend (HMAC Interceptor)
│   │   │   │       │       ├── ApiService.kt
│   │   │   │       │       ├── NetworkClient.kt
│   │   │   │       │       └── SecurityInterceptor.kt
│   │   │   │       └── ui/               # Tầng giao diện người dùng: Activities, Fragments
│   │   │   │           ├── main/         # Màn hình chính, Bottom Bar, Drawer
│   │   │   │           │   ├── MainActivity.kt
│   │   │   │           │   └── MainViewModel.kt
│   │   │   │           ├── transfer/     # Giao diện Gửi / Nhận file, Dò tìm radar P2P
│   │   │   │           │   ├── SendActivity.kt
│   │   │   │           │   ├── ReceiveActivity.kt
│   │   │   │           │   └── TransferProgressActivity.kt
│   │   │   │           ├── files/        # Giao diện duyệt tệp theo danh mục
│   │   │   │           │   └── FileManagerFragment.kt
│   │   │   │           ├── clean/        # Giao diện quét và dọn dẹp rác
│   │   │   │           │   └── CleanitActivity.kt
│   │   │   │           ├── safebox/      # Giao diện mở khóa mã PIN két an toàn
│   │   │   │           │   └── SafeboxUnlockActivity.kt
│   │   │   │           ├── muslim/       # Giao diện Muslim Daily, Kinh Quran
│   │   │   │           │   └── MuslimDailyActivity.kt
│   │   │   │           ├── coin/         # Giao diện Đổi thưởng, Điểm danh nhận quà
│   │   │   │           │   └── CoinStoreActivity.kt
│   │   │   │           └── paywall/      # Giao diện Mua gói VIP (Subscription Dialog)
│   │   │   │               └── PaywallActivity.kt
│   │   │   ├── cpp/                      # Tầng Native C++ NDK hiệu năng cao
│   │   │   │   ├── CMakeLists.txt        # File cấu hình build C++
│   │   │   │   └── native-core.cpp       # Thuật toán CRC32, XXHash, Ring Buffer siêu tốc
│   │   │   ├── assets/                   # Assets gốc: Lottie animations, JS Bridge, WebShare UI
│   │   │   └── res/                      # Tài nguyên giao diện: layouts, drawables, strings, values
```

---

## 3. Bản Đồ Cấu Trúc Hệ Thống Backend & CMS (`Convert/Backend` - Cổng 6030)

```
Convert/Backend/
├── package.json                          # Khai báo Dependencies Node.js, Express, WS, JWT
├── tsconfig.json                         # Cấu hình biên dịch TypeScript chuẩn
├── .env.example                          # File mẫu cấu hình cổng 6030, Secret keys, Database
├── src/
│   ├── server.ts                         # File khởi động chính Express HTTP & WebSocket cổng 6030
│   ├── config/                           # Cấu hình biến môi trường và hằng số hệ thống
│   │   └── index.ts
│   ├── database/                         # Tầng lưu trữ cơ sở dữ liệu (SQLite / JSON persistence)
│   │   ├── db.ts
│   │   └── seed.ts                       # Khởi tạo dữ liệu mẫu (Admin, MCDS slots, Ad matrix, VIP)
│   ├── middlewares/                      # Middlewares xác thực JWT, HMAC Signature, Rate Limiter
│   │   ├── auth.middleware.ts
│   │   └── signature.middleware.ts
│   ├── controllers/                      # Bộ điều khiển xử lý logic cho từng phân hệ
│   │   ├── auth.controller.ts            # Xử lý thiết bị, token, người dùng
│   │   ├── relay.controller.ts           # Xử lý phiên truyền dữ liệu Relay & Streaming chunks
│   │   ├── mcds.controller.ts            # Xử lý Dynamic Remote Config & Space IDs
│   │   ├── ads.controller.ts             # Xử lý Waterfall Mediation & Tracking
│   │   ├── pay.controller.ts             # Xử lý Giao dịch IAP VIP & Webhook Google/Apple
│   │   ├── coin.controller.ts            # Xử lý Sổ cái Coin, Điểm danh, Nhiệm vụ
│   │   ├── telemetry.controller.ts       # Xử lý Ladon Analytics Ingestion & Crash Dump
│   │   └── admin.controller.ts           # API phục vụ CMS Dashboard
│   ├── routes/                           # Khai báo các tuyến đường URL Endpoints
│   │   ├── auth.routes.ts
│   │   ├── relay.routes.ts
│   │   ├── mcds.routes.ts
│   │   ├── ads.routes.ts
│   │   ├── pay.routes.ts
│   │   ├── coin.routes.ts
│   │   ├── telemetry.routes.ts
│   │   └── admin.routes.ts
│   └── services/                         # Tầng nghiệp vụ cốt lõi
│       ├── signaling.service.ts          # WebSocket Signaling WebRTC SDP/ICE
│       └── ledger.service.ts             # Ghi nhận giao dịch Coin bất biến
└── public/
    └── admin/                            # Giao diện Web CMS Dashboard (Truy cập: /admin)
        ├── index.html                    # Trang quản trị Single Page Application
        ├── style.css                     # Phong cách giao diện hiện đại (Dark/Light mode)
        └── app.js                        # Script điều khiển Dashboard, Biểu đồ Chart.js, API calls
```

