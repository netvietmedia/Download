# Báo cáo phân tích ứng dụng SHAREit — Phân tích bảo mật

> Mục đích: xác định rủi ro bảo mật của SHAREit 6.53.38_UD để **tránh lặp lại / chặn** trong sản phẩm của bạn. Mỗi mục: phát hiện → mức độ → cách phòng tránh.

## 1. Tóm tắt mức độ rủi ro

| # | Vấn đề | Mức độ |
|---|---|---|
| 1 | Cleartext HTTP toàn bộ (`usesCleartextTraffic=true` + base-config cho phép) | **CAO** |
| 2 | 202 permissions, nhiều permission nhạy cảm không cần thiết | **CAO** |
| 3 | 86 exported components (58 activity, 7 service, 12 receiver, 9 provider) | **CAO** |
| 4 | Truy cập dữ liệu thiết bị: IMEI/IMSI/phone/MAC 16 lần | **CAO** |
| 5 | Quét toàn bộ app đã cài (QUERY_ALL_PACKAGES + getInstalledPackages 21 lần) | **CAO** |
| 6 | Bỏ qua lỗi SSL trong nhiều WebView (ad SDK) | TRUNG BÌNH |
| 7 | addJavascriptInterface (19 chỗ) — nguy cơ RCE qua WebView | TRUNG BÌNH |
| 8 | Khóa API Google / AdMob nhúng trong DEX | TRUNG BÌNH |
| 9 | `WRITE_SETTINGS`, `SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE` | TRUNG BÌNH |
| 10 | Endpoint dev/test/loopback sót trong build production | TRUNG BÌNH |
| 11 | Packer + obfuscation nặng (chống phân tích, cũng gây khó gỡ lỗi) | THÔNG TIN |
| 12 | Mã hóa yếu: MD5 (2), AES/ECB (6), không dùng SQLCipher | THẤP→TB |

## 2. Phân tích chi tiết

### 2.1 Cleartext HTTP toàn bộ (CAO)

**Phát hiện:**
- Manifest: `android:usesCleartextTraffic="true"` (toàn app).
- `res/xml/m.xml` (network security config):
  ```xml
  <base-config cleartextTrafficPermitted="true">
      <trust-anchors><certificates src="system"/></trust-anchors>
  </base-config>
  ```
- 81 URL `http://` trong DEX, gồm cả endpoint chức năng: `http://api2.wshareit.com/ping/`, `http://ladon.dalyfeds.com/ladon/notify2/report`, `http://pay.wshareit.com/v3/pay/order/record/save`, `http://relay.ushareit.com/relayserver`.

**Rủi ro:** MITM đọc/sửa toàn bộ traffic: dữ liệu người dùng, tham số ký API (có thể replay), file truyền qua relay, quảng cáo chèn nội dung độc hại (ad injection → malware phổ biến ở các app "free" kiểu này).

**Phòng tránh:** HTTPS bắt buộc; `usesCleartextTraffic=false`; network security config chỉ cho phép cleartext cho domain cụ thể (localhost); chứng chỉ `pin` cho API chính; từ chối HTTP trong OkHttp/WebView (`NetworkSecurityPolicy`).

### 2.2 Permission quá mức (CAO)

**202 uses-permission** — điểm nổi bật:

| Permission | Dùng để |
|---|---|
| `READ_CONTACTS`, `WRITE_CONTACTS` | Đọc/ghi danh bạ |
| `READ_CALENDAR`, `WRITE_CALENDAR` | Lịch |
| `SEND_SMS`, `CALL_PHONE` | Gửi SMS/gọi (đáng ngờ nhất) |
| `RECORD_AUDIO`, `CAMERA` | Ghi âm/quay phim |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Vị trí |
| `READ_PHONE_STATE`, `READ_PRIVILEGED_PHONE_STATE`, `READ_BASIC_PHONE_STATE` | IMEI/trạng thái |
| `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS` | Quét app đã cài |
| `SYSTEM_ALERT_WINDOW` | Overlay (quảng cáo nổi) |
| `WRITE_SETTINGS`, `WRITE_MEDIA_STORAGE`, `MANAGE_EXTERNAL_STORAGE` | Ghi cấu hình/đa phương tiện |
| `INSTALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `REQUEST_DELETE_PACKAGES` | Cài/xóa app khác |
| `KILL_BACKGROUND_PROCESSES`, `CLEAR_APP_CACHE`, `GET_PACKAGE_SIZE` | Quản lý process/package |
| `PACKAGE_VERIFICATION_AGENT`, `UPDATE_DEVICE_STATS` | Quyền hệ thống |
| ~60 permission READ/WRITE_SETTINGS cho **từng loại launcher** (HTC, Samsung, Oppo, Xiaomi, ZTE…) | Badge/icon shortcut trên mọi launcher |
| `INTERACT_ACROSS_USERS_FULL` (khai báo `<permission>`) | Quyền hệ thống nguy hiểm |

**Phòng tránh:** nguyên tắc tối thiểu — chỉ xin quyền đúng tính năng, runtime request kèm giải thích, không khai báo quyền hệ thống nhạy cảm (`READ_PRIVILEGED_PHONE_STATE`, `INSTALL_PACKAGES` không bao giờ dùng cho app thường), gom permission launcher bằng `uses-permission` thật cần hoặc bỏ.

### 2.3 Exported components (CAO)

**86 component exported=true**, gồm:
- 58 activity: `MainActivity`, `Downloader`, `MusicPlayer`, `VideoPlayer`, `FloatingVideoPlayer`, `SafeBoxVerifyActivity`, `SafeboxHomeActivity`, `AppBrowser`, `FileBrowserA/B`, `QRCodeScanActivity`, `WebShareJIOStartActivity`, `HybridLocalActivity`…
- 7 service: `HybridService`, `PushMessageService`, `AppPushMessageService`, `TierahsMPService`…
- 12 receiver, 9 provider (trong đó 4 provider định danh: `BeylaIdProvider`, `ShareitIdProvider`, `MyUGProvider`, `XYUGProvider`).

**Rủi ro:** activity/service exported cho phép app khác gọi trực tiếp (deep link, intent injection). `HybridLocalActivity` + `AppBrowser` = WebView mở URL từ intent ngoài → có thể mở `file://` hoặc `javascript://` nếu không kiểm tra. Provider định danh có thể bị đọc `beyla_id`/user id.

**Phòng tránh:** exported=false cho mọi component trừ component có intent-filter thật sự cần; dùng `android:permission` trên exported component; kiểm tra scheme/host/action khi nhận intent; không mở URL intent vào WebView chưa lọc.

### 2.4 Thu thập định danh thiết bị (CAO)

DEX chứa 16 tham chiếu API định danh: `getDeviceId`/IMEI (10), `getSubscriberId`/IMSI (3), `getLine1Number` (1), `getMacAddress` (2), `ANDROID_ID` (8), `getInstalledPackages`/queryIntentActivities (21).

Kết hợp với `com.ushareit.beyla` + `gaea.hellay.net/p/a` (app-list report) → SHAREit xây dựng **hồ sơ thiết bị + danh sách app đã cài** cho quảng cáo (fingerprinting). IMEI/IMSI là nhạy cảm nhất và bị Google chặn từ Android 10.

**Phòng tránh:** không đọc IMEI/IMSI/MAC (không còn được phép, dùng Advertising ID/OAID); khai báo `AD_ID` đúng cách; minh bạch trong privacy policy; tránh thu thập app list nếu không thực sự cần.

### 2.5 Bỏ qua lỗi SSL trong WebView (TRUNG BÌNH)

15 handler `onReceivedSslError`; nhiều handler **bỏ trống** (không gọi `handler.cancel()`) hoặc gọi `super` — điển hình trong ad SDK: AnyThink (`b.java:30`, `a.java:47`), một số WebView ad khác. Hệ quả: quảng cáo MITM vẫn tải được → ad injection thay quảng cáo thật.

**Phòng tránh:** không bao giờ để `onReceivedSslError` rỗng; luôn `handler.cancel()` trừ khi có chính sách rõ ràng; hạn chế WebView tải nội dung remote không kiểm soát.

### 2.6 JavaScript bridge trong WebView (TRUNG BÌNH)

19 chỗ `addJavascriptInterface` (AftWebView của Midas, BaseWebController, Fyber, Pangle, adexchange…). Nếu bridge không lọc message hoặc WebView mở URL không kiểm soát → **RCE** (exploit CVE-2012-6636 class cũ, hoặc lạm dụng bridge gọi hàm native).

**Phòng tránh:** `addJavascriptInterface` chỉ cho domain tin cậy (Android 17+ có `@JavascriptInterface` filter); kiểm tra `getUrl()` trước khi inject; không cho WebView mở `file://`; disable JS nếu không cần.

### 2.7 Khóa API nhúng trong code (TRUNG BÌNH)

- Google API key: `AIzaSyDrMozHu0Y_1SVBOyi7BX4IWNek9dygWJk` (Maps/Places — key này xuất hiện cùng `maps.googleapis.com/maps/api/place/*`).
- AdMob app ID: `ca-app-pub-2075998924432436`; còn cả ID test `ca-app-pub-3940256099942544` và placeholder `0000000000000000` (test dev sót lại).

**Rủi ro:** key bị trích xuất dùng trái phép (quota, chi phí); placeholder test là dấu hiệu config chưa sạch.

**Phòng tránh:** khóa Google nhúng phải restrict theo package+signing; không để test ID/placeholder vào build release; secret tuyệt đối không vào client — chuyển qua backend proxy.

### 2.8 Endpoint dev/test sót (TRUNG BÌNH)

`10.0.2.2:8888/logan-web/…` (Logan log tới emulator loopback), `dev-api.wshareit.com`, `test.midas-*.sg1.api`, `distribution-test.rqmob.com?debug=1`… có mặt trong build production. Có thể dùng để nhận diện build/khai thác môi trường dev.

**Phòng tránh:** phân tách build flavor, cấu hình môi trường qua remote config có kiểm tra signature; không nhúng endpoint dev trong release.

### 2.9 Mã hóa yếu (THẤP–TRUNG BÌNH)

- `MD5` (2 chỗ) — chỉ dùng cho hash kiểm tra file, không phải password — chấp nhận được.
- `AES/ECB` (6 chỗ) — ECB không an toàn, thường là config nội bộ, nhưng nên đổi CBC/GCM.
- Không thấy SQLCipher — database nội bộ plaintext (safebox dùng cơ chế riêng).
- Cơ chế "signUser" ký tham số API tồn tại nhưng thuật toán nằm trong native/obfuscated — **không thể xác nhận độ mạnh**, nghi ngờ là MD5/HMAC đơn giản.

**Phòng tránh:** AES-GCM + key từ Keystore; HMAC-SHA256 với key server-side; database nhạy cảm mã hóa; tránh tự chế crypto.

### 2.10 Packer / Obfuscation (THÔNG TIN — đáng biết)

- `libpglarmor.so`, `libTierahs.so`, `libNotCheck.so` + package `spg.erahsyna.ovonel.moc.*` (Tierahs packer, gần như chắc chắn của bên thứ 3 Trung Quốc, từng thấy trong các app shareit clone).
- Chuỗi trong AndroidManifest bị cắt ngắn (phá static analysis).
- ~30% class tên 1 ký tự.

**Tác động:** chống phân tích, bảo vệ signUser/anti-cheat — nhưng **không phải là bảo mật**: người phân tích kiên nhẫn vẫn lấy được toàn bộ (chứng minh: báo cáo này). Chi phí: build phức tạp, false-positive trên Google Play Protect, khó debug.

**Khuyến nghị:** nếu mục tiêu là chống copy logic — packer giúp chậm kẻ phân tích ~vài ngày; nếu mục tiêu là bảo vệ người dùng — đầu tư vào server-side auth + HTTPS + remote config có chữ ký thay vì packer.

## 3. Khuyến nghị ưu tiên cho sản phẩm của bạn

1. **HTTPS bắt buộc + pin certificate** cho mọi API (chặn MITM/ad-injection — vấn đề số 1 của app miễn phí).
2. **Quyền tối thiểu**: bỏ SEND_SMS/CALL_PHONE/READ_CONTACTS/CALENDAR nếu không phải core; runtime permission + rationale.
3. **Export component có kiểm soát**: `android:exported=false` mặc định, intent-filter chỉ cho component cần, xác thực scheme.
4. **Không đọc IMEI/IMSI/MAC**: dùng Advertising ID/OAID tuân thủ.
5. **WebView an toàn**: không bridge cho domain lạ, không bỏ qua SSL error, không mở file://.
6. **Khóa nhúng phải restrict**, secret phải ở backend.
7. **Phân tách môi trường** dev/prod; không nhúng debug endpoint vào release.
8. **Remote config có chữ ký** (mọi config tải về phải verify chữ ký server) — chống cấu hình độc hại.
9. Nếu cần chống phân tích: obfuscation (R8/ProGuard) đủ cho phần lớn; packer chỉ khi có yêu cầu cụ thể — cân nhắc chi phí/Play Protect.
