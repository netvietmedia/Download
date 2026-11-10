# HƯỚNG DẪN KHAI BÁO AN TOÀN DỮ LIỆU (DATA SAFETY DECLARATION) TRÊN GOOGLE PLAY CONSOLE

Google Play Console thông báo lỗi:
> **"Invalid data safety declaration: Apps that collect or share user data must declare all data types in their data safety declaration. You have not let us know about user data that your app transmits off users’ devices."**

**Nguyên nhân:** Google Play tự động quét tệp AAB và phát hiện ứng dụng có tích hợp các SDK (**Firebase Analytics, Firebase Crashlytics, Google AdMob, Google Play Billing, Play Services Location**). Các SDK này có truyền dữ liệu ra khỏi thiết bị của người dùng, nhưng trên Google Play Console bạn chưa điền hoặc chọn "Không thu thập dữ liệu".

Dưới đây là **hướng dẫn tích chọn chính xác từng bước (100% vượt duyệt Google Play)**:

---

## BƯỚC 1: Truy cập mục An toàn dữ liệu
1. Mở **Google Play Console** -> Chọn ứng dụng **Share Now**.
2. Ở thanh menu bên trái, cuộn xuống mục **Chính sách và chương trình (Policy and programs)** -> Chọn **Nội dung ứng dụng (App content)**.
3. Tìm mục **An toàn dữ liệu (Data safety)** -> Nhấn **Bắt đầu (Start)** hoặc **Quản lý (Manage)**.

---

## BƯỚC 2: Thu thập và bảo mật dữ liệu (Data collection and security)

Trả lời 3 câu hỏi khởi đầu:
1. **Ứng dụng của bạn có thu thập hoặc chia sẻ bất kỳ loại dữ liệu người dùng bắt buộc nào không?**
   👉 Chọn: **Có (Yes)**
2. **Tất cả dữ liệu người dùng mà ứng dụng của bạn thu thập có được mã hóa trong quá trình truyền tải không?**
   👉 Chọn: **Có (Yes)** *(Tất cả kết nối của Firebase/Google SDK đều mã hóa HTTPS/TLS)*
3. **Bạn có cung cấp cách để người dùng yêu cầu xóa dữ liệu của họ không?**
   👉 Chọn: **Có (Yes)**
   - Nhập liên kết yêu cầu xóa dữ liệu: `https://sites.google.com/view/nextai-share-now/` (hoặc gửi qua email `support@sharenow.app`)

---

## BƯỚC 3: Chọn các loại dữ liệu (Data types)

Tích chọn các mục sau đây (tương ứng với các SDK có trong app):

### 1. Vị trí (Location)
* Tích chọn: **Vị trí ước chừng (Approximate location)**
*(Do sử dụng Play Services Location & Wi-Fi scanning cho P2P)*

### 2. Thông tin cá nhân (Personal info)
* Tích chọn: **Mã nhận dạng người dùng (User IDs)**
*(Do Firebase Auth / Firebase Analytics gán ID phiên người dùng)*

### 3. Thông tin tài chính (Financial info)
* Tích chọn: **Lịch sử mua hàng (Purchase history)**
*(Do sử dụng Google Play Billing mua gói VIP/xu)*

### 4. Hoạt động trong ứng dụng (App activity)
* Tích chọn 2 mục:
  * ✅ **Tương tác trong ứng dụng (App interactions)** *(Firebase Analytics)*
  * ✅ **Ứng dụng đã cài đặt (Installed apps)** *(BẮT BUỘC ĐỂ HẾT LỖI: dùng để hiển thị danh sách ứng dụng trên máy giúp người dùng chọn gửi file APK)*

### 5. Thông tin và hiệu suất của ứng dụng (App info and performance)
* Tích chọn cả 3 mục:
  * ✅ **Nhật ký sự cố (Crash logs)** *(Firebase Crashlytics)*
  * ✅ **Chẩn đoán (Diagnostics)** *(Firebase Performance / Crashlytics)*
  * ✅ **Dữ liệu hiệu suất ứng dụng khác (Other app performance data)** *(Hệ thống MemoryLogger đo RAM/JVM)*

### 6. Thiết bị hoặc mã nhận dạng khác (Device or other IDs)
* Tích chọn: **Thiết bị hoặc mã nhận dạng khác (Device or other IDs)**
*(Do Google AdMob sử dụng Advertising ID và Firebase Cloud Messaging token)*

> **LƯU Ý:** Mục **Ảnh và video (Photos and videos)** và **Tệp và tài liệu (Files and documents)**: **KHÔNG CẦN CHỌN** vì tệp chỉ truyền trực tiếp giữa 2 máy qua mạng nội bộ P2P (Offline / Wi-Fi Direct), hoàn toàn **không tải lên máy chủ (Not collected off-device)** theo đúng định nghĩa của Google Play.

---

## BƯỚC 4: Khai báo chi tiết cho từng loại dữ liệu (Data usage and handling)

Google Play sẽ yêu cầu bạn xác nhận cách dùng cho từng mục đã tích ở Bước 3. Hãy chọn theo bảng sau:

| Loại dữ liệu | Thu thập hay Chia sẻ? | Được xử lý tạm thời (Ephemeral)? | Bắt buộc hay Tùy chọn? | Mục đích sử dụng (Chọn các ô) |
| :--- | :--- | :--- | :--- | :--- |
| **Vị trí ước chừng (Approximate location)** | ✅ Thu thập (Collected) | Có (Yes) | Bắt buộc (Required) | • Chức năng ứng dụng (App functionality)<br>• Phân tích (Analytics) |
| **Mã nhận dạng người dùng (User IDs)** | ✅ Thu thập (Collected) | Không (No) | Tùy chọn (Optional) | • Chức năng ứng dụng (App functionality)<br>• Quản lý tài khoản (Account management) |
| **Lịch sử mua hàng (Purchase history)** | ✅ Thu thập (Collected) | Không (No) | Tùy chọn (Optional) | • Chức năng ứng dụng (App functionality)<br>• Phòng chống gian lận (Fraud prevention) |
| **Tương tác trong ứng dụng (App interactions)** | ✅ Thu thập (Collected) | Không (No) | Bắt buộc (Required) | • Phân tích (Analytics) |
| **Ứng dụng đã cài đặt (Installed apps)** | ✅ Thu thập (Collected)<br>❌ Không chia sẻ (Not shared) | Có (Yes) | Bắt buộc (Required) | • Chức năng ứng dụng (App functionality)<br>*(Dùng để quét và hiển thị danh sách app trên máy giúp người dùng chọn gửi file APK)* |
| **Nhật ký sự cố (Crash logs)** | ✅ Thu thập (Collected) | Không (No) | Bắt buộc (Required) | • Phân tích (Analytics) |
| **Chẩn đoán & Hiệu suất khác (Diagnostics & Performance)** | ✅ Thu thập (Collected) | Không (No) | Bắt buộc (Required) | • Phân tích (Analytics) |
| **Mã nhận dạng thiết bị (Device or other IDs)** | ✅ Thu thập (Collected)<br>✅ Chia sẻ (Shared - cho AdMob) | Không (No) | Bắt buộc (Required) | • Quảng cáo hoặc tiếp thị (Advertising)<br>• Phân tích (Analytics)<br>• Phòng chống gian lận (Fraud prevention) |

---

## BƯỚC 5: Xem lại và Lưu (Preview and Submit)
1. Kiểm tra lại bảng tóm tắt: Google Play sẽ hiển thị bảng tóm tắt các dữ liệu bạn vừa khai báo.
2. Nhấn **Lưu (Save)**.
3. Quay lại trang tổng quan bản phát hành (Publishing overview) -> Nhấn **Gửi các thay đổi để xem xét (Send for review)**.
4. Bản phát hành **v0.1.2** (versionCode 3) sẽ được chuyển sang trạng thái **Đang xem xét (In review)** thành công!
