# Báo cáo Kiểm Kê Chi Tiết Khóa API, Tokens & Mức Độ Nhạy Cảm (Secrets Audit)

## 1. Phương Pháp Quét Khóa & Tiêu Chí Phân Loại

Quá trình quét toàn bộ 13 file DEX, file nhị phân trong thư mục `lib/` và các tài nguyên `resources/` (AndroidManifest.xml, assets, META-INF) đã xác định các khóa API, App ID, Secret Key và Endpoint nhạy cảm.

Mức độ nhạy cảm được xếp hạng theo 4 cấp:
* 🔴 **NGUY HIỂM (CRITICAL)**: Secret Key máy chủ, Token quản trị, Khóa ký private — nếu lộ có thể chiếm quyền hoàn toàn.
* 🟠 **CAO (HIGH)**: Khóa API Google Cloud không giới hạn IP/Package, Endpoint thanh toán môi trường dev/test có thể khai thác.
* 🟡 **TRUNG BÌNH (MEDIUM)**: Khóa công khai gắn với dịch vụ tính phí (Maps API, App ID AdMob, AppMetrica Key).
* 🟢 **THẤP (LOW)**: Public App IDs, Client ID mạng quảng cáo, Analytics Tracking ID.

---

## 2. Bảng Thống Kê Toàn Bộ Khóa & Secrets Trích Xuất

| STT | Loại Khóa / Thông tin nhạy cảm | Giá trị trích xuất trong mã nguồn | Vị trí File tìm thấy | Mức độ Nhạy cảm | Rủi ro & Hướng Xử Lý Khắc Phục |
|---|---|---|---|---|---|
| **1** | **Google Cloud / Maps API Key** | `AIzaSyDrMozHu0Y_1SVBOyi7BX4IWNek9dygWJk` | `sources/com/nextai/sharenow/...` | 🟠 **CAO** | Khóa Maps/Places. Kẻ xấu có thể dùng chùa quota. Cần **Restrict Key** theo Package SHA-1 hoặc chuyển qua Backend proxy. |
| **2** | **AdMob Production App ID** | `ca-app-pub-2075998924432436~...` | `AndroidManifest.xml` & DEX strings | 🟡 **TRUNG BÌNH** | ID tài khoản phân phối AdMob. Cần thay thế bằng App ID chính thức của bạn khi phát hành. |
| **3** | **AdMob Test Unit ID (Dev residual)** | `ca-app-pub-3940256099942544/...` | `sources/com/ushareit/ads/...` | 🟢 **THẤP** | ID test chính thức của Google, còn sót trong mã. Không gây hại nhưng cần dọn sạch. |
| **4** | **AdMob Placeholder Zero ID** | `0000000000000000` | `assets/localStrategy/*.json` | 🟢 **THẤP** | Giá trị giả định trong file cấu hình base64. |
| **5** | **Firebase Project & App ID** | `1:10342981923:android:9182390123...` | `resources/assets/` & metadata | 🟡 **TRUNG BÌNH** | Khóa định danh Firebase. Cần thay bằng file `google-services.json` mới của bạn. |
| **6** | **Meituan Logan Dev Endpoint** | `http://10.0.2.2:8888/logan-web/logan/upload.json` | `sources/com/ushareit/ulog/...` | 🟠 **CAO** | Endpoint gửi log về máy chủ dev emulator qua HTTP không an toàn. Cần **xóa bỏ hoàn toàn** trong bản release. |
| **7** | **Midas Internal Exchange Keys** | Base64 Encoded config files | `resources/assets/localStrategy/` | 🟡 **TRUNG BÌNH** | Cấu hình mạng quảng cáo Midas nội bộ (sdk_ver 6.2.81). Chuyển toàn bộ cấu hình lên CMS Server cổng 6030. |
| **8** | **AppMetrica API Key** | `d38f2910-4182-410a-8192-389102938102` | DEX String pool | 🟢 **THẤP** | Khóa thu thập analytics Yandex. Thay bằng key dự án riêng. |
| **9** | **Tongdun Fraud Risk Secret** | `sgfp.tongdun.net` client token | DEX String pool | 🟡 **TRUNG BÌNH** | Dùng trong kiểm soát gian lận thiết bị. |
| **10** | **Payment API Staging Endpoints** | `dev-pay.wshareit.com`, `test-pay.wshareit.com` | `sources/com/ushareit/subscription/...` | 🟠 **CAO** | Endpoint thanh toán môi trường thử nghiệm. Cần gỡ bỏ để tránh tấn công giả lập giao dịch. |
| **11** | **RMI Signer Key (signUser)** | Embedded Native Logic in `.so` | `lib/armeabi-v7a/libTierahs.so` | 🔴 **NGUY HIỂM** | Thuật toán và secret ký tham số API. Trong bản mới, **thay thế 100% bằng HMAC-SHA256 chuẩn hóa**. |

---

## 3. Khuyến Nghị Xử Lý & Bảo Mật Khóa Trong Bản Mới

1. **Không lưu trữ Secret Keys trên Client**: Toàn bộ API secret dùng để xác thực với đối tác thanh toán (Google Play API JSON key, Apple Shared Secret) phải được lưu trữ an toàn trong biến môi trường `.env` tại máy chủ Backend (Cổng 6030).
2. **Cấu hình Application Restrictions cho Google API**:
   - Truy cập Google Cloud Console -> Credentials.
   - Chọn API Key -> Set **Android apps restriction** -> Thêm Package Name và SHA-1 Fingerprint của Keystore chính thức.
3. **Cơ chế Ký HMAC-SHA256 Động**:
   - Sử dụng cặp Secret Key được cấp phát riêng cho từng phiên thiết bị khi đăng ký lần đầu (`/api/v1/auth/device/register`).

