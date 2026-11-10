# Báo cáo phân tích ứng dụng SHAREit — Danh sách SDK được gắn vào

## 1. Tổng quan

- **50 gói SDK Java/third-party** phát hiện trong DEX.
- **26 thư viện native** (`lib/*.so` — chỉ build **armeabi-v7a**).
- **67 file metadata version** trong META-INF cho biết phiên bản chính xác từng thư viện.
- 20+ ad network — SHAREit dùng **nhiều ad network nhất trong các app cùng loại**, qua nhiều lớp mediation (AnyThink/TopOn, ironSource, riêng Midas).

## 2. SDK quảng cáo (advertising) — lớn nhất

| SDK | Package | Phiên bản | Vai trò |
|---|---|---|---|
| Google AdMob | `com.google.android.gms.ads` | play-services-ads 23.6.0 | Ad network chính |
| Firebase Ads | `firebase-ads` | 20.4.0 | Ad mediation của Firebase |
| ByteDance Pangle | `com.bytedance.sdk.openadsdk` | (SDK 3.x, qua URL pangle.io) | Ad network |
| Mintegral | `com.mbridge.msdk` | — | Ad network |
| ironSource | `com.ironsource` | — | Mediation + ad |
| Unity Ads | `com.unity3d` | — | Ad network |
| Vungle | `com.vungle` | — | Ad network |
| Tapjoy | `com.tapjoy` (native `libtapjoy.so`) | — | Ad network (reward) |
| AppLovin | `com.applovin` | — | Ad network |
| Yandex Mobile Ads | `com.yandex.mobile.ads` | — | Ad network (Nga) |
| MyTarget | `com.my.target` | — | Ad network (Mail.ru) |
| Amazon APS | `com.amazon.device.ads` | aps_mobile_client_config | Ad network |
| Fyber / Digital Turbine | `com.fyber`, `com.digitalturbine` | — | Ad network |
| PubNative / Verve | `net.pubnative.lite.sdk` | — | Mediation |
| MoPub | `com.mopub` | — | Mediation (legacy) |
| Bigo Ads | `sg.bigo.ads` | — | Ad network (Bigo/YY) |
| AnyThink (TopOn) | `com.anythink` | — | Mediation hàng đầu |
| AlphaGaming | `com.alphagaming` | — | Mediation |
| AdtMonetize | `com.adtmonetize` | — | Ad network |
| **Midas (nội bộ)** | `com.adexchange`, `com.sharead`, `com.sharemob`, `com.st.entertainment` | 6.2.81 (localStrategy) | **Ad-exchange riêng của SHAREit** |
| Beyla | `com.ushareit.beyla`, `com.ushareit.app.BeylaIdProvider` | — | Targeting/thu thập app list |

## 3. SDK phân tích / theo dõi (analytics)

| SDK | Package | Phiên bản |
|---|---|---|
| Firebase Analytics | `com.google.firebase` | 20.1.2 (core 20.0.0, measurement 20.1.2) |
| Firebase Messaging (FCM) | `com.google.firebase.messaging` | 20.1.0 |
| Firebase Auth | `com.google.firebase.auth` | 19.1.0 |
| AppMetrica (Yandex) | `io.appmetrica` | — |
| Adjust | `com.adjust` (qua URL) | — |
| Logan (Meituan) | native `liblogan.so` | — |
| xCrash | native `libxcrash.so`, `libxcrash_dumper.so` | — |
| Bugly/Tongdun | `bugly.tongdun.net`, `sgfp.tongdun.net` | — |
| UMeng | `com.umeng` | — |
| Ladon (nội bộ) | `ladon.dalyfeds.com` | — |
| ULog (nội bộ) | `com.ushareit.ulog` | — |
| MSA miitmdid | `com.bun.miitmdid` | OAID (định danh quảng cáo TQ) |
| AppSet (Google) | `play-services-appset` | 16.0.2 |

## 4. SDK hạ tầng / cơ bản

| SDK | Package | Phiên bản |
|---|---|---|
| OkHttp | `okhttp3` | — |
| Retrofit | `retrofit2` | — |
| RxJava | `io.reactivex` | — |
| Kotlin Coroutines | `kotlinx.coroutines` | 1.7.1 |
| Glide | `com.bumptech.glide` (+ `libglide-webp.so`) | — |
| AndroidX (full set) | `androidx.*` | core 1.9.0, appcompat 1.6.1, recyclerview 1.2.1, room 2.4.3, work 2.7.0, webkit 1.5.0, media 1.6.0 |
| Material Design | `com.google.android.material` | 1.6.1 |
| MMKV | `com.tencent.mmkv` (native `libmmkv.so`) | — |
| BouncyCastle | `org.bouncycastle` | — |
| OkDownload | `com.liulishuo.okdownload` | — |
| AndroidVideoCache | `com.danikula` | — |
| jmdns (mDNS/DLNA) | `jmdns.version` | 3.5.7 |
| Google Play Core | billing 7.0.0, review 2.0.1, app-update 2.1.0, asset-delivery 2.2.2, feature-delivery 2.1.0 | — |
| Google Maps/Places | play-services-maps 17.0.0, places 2.6.0, location 19.0.1 | — |
| Huawei HMS | `com.huawei.hms` | — |
| Push (OPPO/vivo/Xiaomi/Meizu/Honor/ColorOS) | `com.heytap`, `com.vivo`, `com.xiaomi`, `com.meizu`, `com.hihonor`, `com.coloros` | — |
| Baidu / Weibo / WeChat SDK | `com.baidu`, `com.sina.weibo`, `com.tencent.mm.opensdk` | — |
| Mozilla (Rhino/Gecko) | `org.mozilla` | — |
| Tramini (TalkingData) | `com.tramini` | — |

## 5. Thư viện native (`lib/`, armeabi-v7a)

| Library | Chức năng |
|---|---|
| `lib7-Zip-JBinding.so` | Giải nén 7z |
| `libboost_multidex.so` | Khởi động multidex nhanh |
| `libbuffer.so`, `libfile_lock.so` | Tiện ích buffer/khóa file |
| `libc++_shared.so` | C++ runtime |
| `libglide-webp.so` | WebP cho Glide |
| `libhpatchz.so` | Patch nhị phân (upgrade) |
| `libimggroup.so` | Xử lý ảnh |
| `libinnoplayer-core.so` | Player (InnoPlayer) |
| `liblogan.so` | Logan log (Meituan) |
| `libmmkv.so` | MMKV key-value |
| `libmoksha.so` | ByteDance (Moksha) |
| `libnms.so` | — |
| `libpglarmor.so` | **Packer/bảo vệ** (PGL armor) |
| `libpl_droidsonroids_gif.so` | GIF |
| `libtapjoy.so` | Tapjoy ads |
| `libtdcoreplugin.so` | ByteDance |
| `libtobEmbedPagEncrypt.so` | ByteDance PAG (animation) |
| `libtt_ugen_layout.so` | ByteDance UGen |
| `libxcrash.so`, `libxcrash_dumper.so` | xCrash |
| `libyuv.so` | YUV xử lý video |
| `libNotCheck.so`, `libTierahs.so` | **Protector/anti-tamper** |

## 6. Ghi chú

- Bộ META-INF `.properties` còn tiết lộ thêm: `play-p2p-client 2.0.1` (Google Nearby/Play P2P — dùng cho `com.android.vending.p2p.APP_INSTALL_API`), `user-messaging-platform 3.0.0` (GDPR consent), `transport-runtime 3.1.8` (Firebase transport).
- Hệ ad nội bộ **Midas** có cấu hình placement mã hóa Base64 trong `assets/localStrategy/*.json` (sdk_ver 6.2.81), liệt kê từng ad unit theo `nw_firm_name` (Admob, Bigo…).
