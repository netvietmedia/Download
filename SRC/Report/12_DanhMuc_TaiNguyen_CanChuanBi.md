# Báo cáo Thống Kê Danh Mục Tài Nguyên Cần Chuẩn Bị (Resource Checklist)

## 1. Hướng Dẫn Tổng Quan

Theo điều khoản **Mục 19 trong Convertme.txt**: *"Các tài nguyên cần cung cấp: key firebase, admob, .... Thống kê báo cáo dưới dạng bảng cho tôi chuẩn bị"*. Báo cáo này tổng hợp đầy đủ và chi tiết toàn bộ các thông số, khóa API, tài khoản dịch vụ và tệp cấu hình bạn cần chuẩn bị để vận hành và xuất bản ứng dụng.

---

## 2. Bảng Kê Khai Tài Nguyên Cần Chuẩn Bị Chi Tiết

### 2.1. Phân Hệ Google Firebase (Dịch Vụ Nền Tảng & Thông Báo Đẩy)

| # | Tên Tài Nguyên / Thông Số | Mục Đích Sử Dụng | Cách Lấy & Nơi Cấu Hình | Mẫu Giá Trị / Định Dạng | Trạng Thái |
|---|---|---|---|---|---|
| **1** | **File `google-services.json`** | Cấu hình Firebase SDK cho Android | Tải từ Google Firebase Console -> Project Settings -> Your Apps -> Android | File JSON đặt tại: `Convert/Android/app/google-services.json` | ⏳ *Cần chuẩn bị* |
| **2** | **Firebase Project ID** | Định danh dự án Firebase trên máy chủ | Firebase Console -> Project Settings | `shareit-clone-prod-2026` | ⏳ *Cần chuẩn bị* |
| **3** | **FCM Server Key / Service Account JSON** | Gửi thông báo đẩy Push Notification từ Backend | Firebase Console -> Cloud Messaging / IAM Service Accounts | `firebase-adminsdk-xxxxx.json` đặt tại Backend | ⏳ *Cần chuẩn bị* |
| **4** | **Firebase Web API Key** | Xác thực Firebase Auth | Firebase Console -> Project Settings -> Web API Key | `AIzaSyB...` | ⏳ *Cần chuẩn bị* |

---

### 2.2. Phân Hệ Quảng Cáo Google AdMob & Mediation Networks

| # | Tên Tài Nguyên / Đơn Vị Quảng Cáo | Định Dạng / Vị Trí Hiển Thị | Nơi Cấu Hình Trong Dự Án | Mẫu ID (Test ID của Google) | Trạng Thái |
|---|---|---|---|---|---|
| **1** | **AdMob App ID** | Định danh toàn bộ ứng dụng trên AdMob | `AndroidManifest.xml` (`APPLICATION_ID`) | `ca-app-pub-3940256099942544~3347511713` | ⏳ *Cần chuẩn bị* |
| **2** | **Banner Ad Unit ID** | Hiển thị dưới đáy màn hình chính (`MainActivity`) | `ad_placements` table / Backend CMS | `ca-app-pub-3940256099942544/6300978111` | ⏳ *Cần chuẩn bị* |
| **3** | **Interstitial Ad Unit ID** | Hiển thị sau khi hoàn thành truyền file P2P | `ad_placements` table / Backend CMS | `ca-app-pub-3940256099942544/1033173712` | ⏳ *Cần chuẩn bị* |
| **4** | **Rewarded Video Ad Unit ID**| Người dùng xem để nhận thêm 200 Coin | `ad_placements` table / Backend CMS | `ca-app-pub-3940256099942544/5224354917` | ⏳ *Cần chuẩn bị* |
| **5** | **App Open Ad Unit ID** | Hiển thị khi người dùng mở ứng dụng từ màn hình nền | `ad_placements` table / Backend CMS | `ca-app-pub-3940256099942544/9257395921` | ⏳ *Cần chuẩn bị* |
| **6** | **Native Advanced Ad Unit ID**| Hiển thị lồng trong danh sách tệp và dọn rác | `ad_placements` table / Backend CMS | `ca-app-pub-3940256099942544/2247696110` | ⏳ *Cần chuẩn bị* |
| **7** | **AppLovin MAX SDK Key** | Khóa tích hợp mạng trung gian AppLovin | `AndroidManifest.xml` & Backend CMS | Chuỗi ký tự SDK Key 86 ký tự | ⏳ *Cần chuẩn bị* |
| **8** | **Mintegral App ID & Key** | Mạng quảng cáo Mintegral | Backend CMS (`ad_placements`) | `app_id` + `app_key` | ⏳ *Cần chuẩn bị* |

---

### 2.3. Phân Hệ Thanh Toán Google Play Console (In-App Purchases & Keystore)

| # | Tên Tài Nguyên | Mục Đích Sử Dụng | Nơi Cấu Hình | Mẫu Giá Trị / Hướng Dẫn | Trạng Thái |
|---|---|---|---|---|---|
| **1** | **Google Play Service Account JSON** | Máy chủ Backend đối soát hóa đơn mua hàng VIP | Backend `.env` (`GOOGLE_SERVICE_ACCOUNT_PATH`) | Tạo tại Google Cloud Console với quyền `Android Publisher` | ⏳ *Cần chuẩn bị* |
| **2** | **Product ID: Gói Tuần** | Gói thuê bao $1.99/tuần | Google Play Console -> Subscriptions | `shareit.vip.weekly` | ⏳ *Cần chuẩn bị* |
| **3** | **Product ID: Gói Tháng** | Gói thuê bao $4.99/tháng | Google Play Console -> Subscriptions | `shareit.vip.monthly` | ⏳ *Cần chuẩn bị* |
| **4** | **Product ID: Gói Năm** | Gói thuê bao $24.99/năm (Phổ biến nhất) | Google Play Console -> Subscriptions | `shareit.vip.yearly` | ⏳ *Cần chuẩn bị* |
| **5** | **Product ID: Gói Trọn Đời** | Gói mua đứt một lần $49.99 (In-App Product) | Google Play Console -> In-app products | `shareit.vip.lifetime` | ⏳ *Cần chuẩn bị* |
| **6** | **Release Keystore File (`.jks` / `.keystore`)** | Ký số file APK / AAB để xuất bản lên Google Play | `Convert/Android/app/keystore/` | Tạo bằng `keytool` hoặc Android Studio | ⏳ *Cần chuẩn bị* |
| **7** | **Keystore Password & Alias** | Mật khẩu và bí danh của chứng chỉ ký số | `Convert/Android/gradle.properties` | `KEYSTORE_PASSWORD`, `KEY_ALIAS` | ⏳ *Cần chuẩn bị* |

---

### 2.4. Phân Hệ Hạ Tầng Máy Chủ & Domain (Server & Hosting)

| # | Tên Tài Nguyên | Thông Số Yêu Cầu | Mục Đích Sử Dụng | Ghi Chú Cấu Hình | Trạng Thái |
|---|---|---|---|---|---|
| **1** | **Tên Miền Chính (Domain)** | `api.yourdomain.com` / `cms.yourdomain.com` | Trỏ về máy chủ Backend cổng 6030 | Cấu hình bản ghi DNS A/CNAME | ⏳ *Cần chuẩn bị* |
| **2** | **Chứng Chỉ SSL / TLS** | Let's Encrypt hoặc Cloudflare Full SSL | Mã hóa toàn bộ kết nối HTTPS / WSS | Bảo vệ chống tấn công MITM | ⏳ *Cần chuẩn bị* |
| **3** | **Cổng Dịch Vụ (Port)** | **6030** | Chạy Backend API, WebSocket và CMS Admin UI | Đã thiết lập sẵn trong mã nguồn Backend | ✅ **Sẵn sàng** |
| **4** | **Google Maps API Key** | Đã bật Places & Geocoding API | Phục vụ tính toán tọa độ giờ cầu nguyện Muslim | Cần cấu hình giới hạn SHA-1 Package | ⏳ *Cần chuẩn bị* |
| **5** | **Adjust App Token (Tùy chọn)** | Attribution Tracking Token | Đo lường nguồn cài đặt từ các chiến dịch quảng cáo | 12 ký tự hex | ⏳ *Cần chuẩn bị* |

