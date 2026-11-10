# BÁO CÁO SỐ 17: HỆ THỐNG QUẢNG CÁO CHÉO "SHAREit FAMILY" & MA TRẬN CPI APP WALL TRÊN CMS BACKEND (CỔNG 6030)

---

## 1. TỔNG QUAN VỀ TÍNH NĂNG "SHAREit FAMILY" TRONG MÃ NGUỒN GỐC

Trong quá trình dịch ngược và phân tích mã nguồn tệp APK gốc `com.lenovo.anyshare.gps` (phiên bản `6.53.38_UD`), nhóm phân tích đã phát hiện chuỗi định danh tài nguyên `bj9` ("SHAREit Family") cùng Activity điều hướng `FamilyProductActivity` (`com.lenovo.anyshare.main.FamilyProductActivity`).

### 1.1 Bản Chất Kỹ Thuật & Mô Hình Kinh Doanh
- **Mục tiêu thương mại**: "SHAREit Family" thực chất là một **App Wall nội bộ** và **Mạng lưới quảng cáo chéo (Cross-Promotion Network)**.
- **Cơ chế hoạt động**:
  1. Thay vì chỉ hiển thị quảng cáo từ Google AdMob hay Mintegral, SHAREit tận dụng lượng truy cập khổng lồ của mình để tự quảng bá các ứng dụng khác trong cùng hệ sinh thái (hoặc nhận hợp đồng quảng cáo CPI từ các đối tác thứ ba).
  2. Mỗi lượt click và cài đặt thành công (Cost-Per-Install - CPI) sẽ mang lại doanh thu từ **$0.20 - $0.50 / Install** mà không phải chia sẻ 30-40% phí trung gian cho các mạng ad network.
  3. Giao diện hiển thị dưới dạng danh sách App Store thu nhỏ (Icon, Tên App, Danh mục, Nhãn nổi bật HOT/NEW/RECOMMENDED, Đánh giá sao, Số lượt tải và nút Tải xuống).

---

## 2. HIỆN TRẠNG TÍCH HỢP TRÊN HỆ THỐNG BACKEND & CMS ADMIN (CỔNG 6030)

Hệ thống Backend NodeJS/TypeScript và giao diện CMS Web Portal tại `http://localhost:6030/admin` **ĐÃ ĐƯỢC TÍCH HỢP TOÀN DIỆN 100%** module **SHAREit Family & Cross-Promotion**.

### 2.1 Các Tính Năng Đã Hoàn Thiện Trên CMS
1. **Bảng KPI Giám Sát Thời Gian Thực**:
   - Tổng số App trong ma trận.
   - Số chiến dịch CPI đang kích hoạt (`Active`).
   - Giá thầu CPI Payout trung bình (`$ USD / Install`).
   - Lượt cài đặt ước tính trên toàn hệ thống.
2. **Trình Quản Lý App Wall Trực Quan (Live Preview)**:
   - Hiển thị trực quan từng App Card với ảnh đại diện (Icon), huy hiệu (`HOT`, `NEW`, `RECOMMENDED`, `POPULAR`), đánh giá sao và số lượt tải `50M+`.
   - Nút bật/tắt nhanh (`Tạm Dừng` / `Kích Hoạt`) chiến dịch theo thời gian thực mà không cần deploy lại code.
   - Nút `Xóa` ứng dụng khỏi danh sách phân phối.
3. **Form Thêm Mới Ứng Dụng / Chiến Dịch CPI**:
   - Cho phép nhập: Tên ứng dụng, Package Name (Android Bundle ID), Icon URL, Download/Play Store URL, Danh mục, Badge, Điểm sao, Lượt tải, Mô tả ngắn, Giá thầu CPI Payout ($), và Độ ưu tiên hiển thị (`Priority 1-10`).
4. **Cơ Chế Dynamic Config 100% (Zero APK Rebuild)**:
   - Khi Admin thêm, sửa hoặc tắt bất kỳ ứng dụng nào trên CMS, thiết bị người dùng (Android/iOS) gọi API `/api/v1/mcds/family-products` sẽ nhận ngay dữ liệu mới nhất tức thì.

---

## 3. DANH SÁCH ỨNG DỤNG GIA ĐÌNH HẠT GIỐNG ĐANG CHẠY TRÊN HỆ THỐNG

Hệ thống cơ sở dữ liệu `data_storage.json` đã được nạp sẵn 5 ứng dụng hạt giống chuẩn của hệ sinh thái SHAREit:

| ID | Tên Ứng Dụng | Package Name | Danh Mục | Badge | Đánh Giá | Lượt Tải | CPI Payout | Độ Ưu Tiên |
| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :---: | :---: |
| `fam_01` | **CLEANit - Dọn Rác Tăng Tốc** | `com.ushareit.cleanit` | Tiện ích hệ thống | `HOT` | 4.8 ★ | 50M+ | $0.35 | 10 |
| `fam_02` | **CLONEit - Sao Chép Đổi Máy** | `com.ushareit.cloneit` | Công cụ chuyển đổi | `RECOMMENDED` | 4.7 ★ | 20M+ | $0.40 | 9 |
| `fam_03` | **LOCKit - Khóa App Vân Tay** | `com.ushareit.lockit` | Bảo mật riêng tư | `POPULAR` | 4.6 ★ | 15M+ | $0.25 | 8 |
| `fam_04` | **LISTENit - Nghe Nhạc Offline** | `com.ushareit.listenit` | Âm nhạc & Âm thanh | `NEW` | 4.9 ★ | 30M+ | $0.30 | 7 |
| `fam_05` | **S-Player - Trình Chiếu Video 4K**| `com.ushareit.siplayer` | Xem phim & Video | `POPULAR` | 4.7 ★ | 25M+ | $0.20 | 6 |

---

## 4. DANH SÁCH API ENDPOINTS ĐÃ TRIỂN KHAI

### 4.1 Client Endpoints (Dành cho App Android/iOS)
- **Endpoint**: `GET /api/v1/mcds/family-products` (hoặc `POST /api/v1/mcds/family-products`)
- **Headers**: `Content-Type: application/json`
- **Phản Hồi Mẫu (HTTP 200 OK)**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 5,
    "products": [
      {
        "id": "fam_01",
        "app_name": "CLEANit - Dọn Rác Tăng Tốc",
        "package_name": "com.ushareit.cleanit",
        "icon_url": "https://cdn-icons-png.flaticon.com/512/2879/2879209.png",
        "download_url": "https://play.google.com/store/apps/details?id=com.ushareit.cleanit",
        "category": "Tiện ích hệ thống",
        "badge": "HOT",
        "rating": 4.8,
        "download_count_str": "50M+",
        "description": "Giải phóng đến 10GB dung lượng bộ nhớ đệm và tăng tốc RAM thiết bị.",
        "cpi_payout": 0.35,
        "priority": 10,
        "is_active": true
      }
    ],
    "server_time": 1788583350
  }
}
```

### 4.2 Admin CMS Endpoints (Yêu Cầu Header `x-admin-token`)
1. **Lấy danh sách quản trị**: `GET /api/v1/admin/family-products`
2. **Thêm ứng dụng mới**: `POST /api/v1/admin/family-products`
3. **Cập nhật / Bật tắt chiến dịch**: `PUT /api/v1/admin/family-products/:id`
4. **Xóa ứng dụng**: `DELETE /api/v1/admin/family-products/:id`

---

## 5. HƯỚNG DẪN THAY THẾ BẰNG ỨNG DỤNG RIÊNG CỦA DOANH NGHIỆP

Khi bạn muốn dùng hệ thống này để quảng bá hệ sinh thái ứng dụng của riêng mình:
1. Truy cập CMS tại `http://localhost:6030/admin` (Đăng nhập: `admin` / `admin@123456`).
2. Bấm vào tab **"SHAREit Family & Cross-Promo"** trên thanh menu bên trái.
3. Bấm nút **"Xóa"** hoặc **"Tạm Dừng"** các ứng dụng mẫu không cần thiết.
4. Điền thông tin ứng dụng của bạn (App Name, Package Name, Link tải trên Google Play / App Store / File APK trực tiếp, Icon URL, Giá trị CPI) vào form bên trái và bấm **"Thêm Vào SHAREit Family"**.
5. Toàn bộ thiết bị người dùng mở app sẽ ngay lập tức nhìn thấy danh sách ứng dụng mới của bạn.

