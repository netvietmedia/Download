# HƯỚNG DẪN KẾT NỐI GA4, ADMOB, GOOGLE ADS & GOOGLE PLAY CONSOLE CHO SHARENOW

Tài liệu này hướng dẫn chi tiết cách kết nối hệ thống phân tích dữ liệu đa kênh nhằm tự động hóa việc kéo toàn bộ sự kiện từ Firebase/GA4, phân tích doanh thu AdMob, tối ưu hóa chiến dịch quảng cáo Google Ads và tối đa hóa doanh thu cho ứng dụng **ShareNow** (`com.nextai.sharenow`).

---

## 1. THÔNG TIN HỆ THỐNG HIỆN TẠI

- **Tên dự án Google Cloud / Firebase:** `share-now-c5a02`
- **Mã dự án (Project Number):** `649913398819`
- **Gói ứng dụng (Package Name):** `com.nextai.sharenow`
- **Tài khoản Service Account đã tạo sẵn:**
  - **Email:** `ga4-955@share-now-c5a02.iam.gserviceaccount.com`
  - **File Private Key:** `F:\APP\Share File\Share it\GA4\share-now-c5a02-ca18d9d86ddc.json`

---

## 2. BƯỚC 1: KÍCH HOẠT GOOGLE CLOUD APIS (CHỈ MẤT 30 GIÂY)

Để Service Account có quyền gọi API kéo dữ liệu báo cáo, bạn chỉ cần nhấp vào 2 liên kết sau và nhấn nút **"BẬT" (ENABLE)**:

1. **Google Analytics Data API:**  
   👉 [Nhấp vào đây để BẬT Google Analytics Data API](https://console.cloud.google.com/apis/library/analyticsdata.googleapis.com?project=share-now-c5a02)

2. **Google Analytics Admin API:**  
   👉 [Nhấp vào đây để BẬT Google Analytics Admin API](https://console.cloud.google.com/apis/library/analyticsadmin.googleapis.com?project=share-now-c5a02)

---

## 3. BƯỚC 2: CẤP QUYỀN SERVICE ACCOUNT TRÊN GOOGLE ANALYTICS (GA4)

1. Mở trang [Google Analytics Console](https://analytics.google.com/).
2. Nhìn góc dưới bên trái, bấm vào biểu tượng bánh răng **Quản trị (Admin)**.
3. Ở cột **Tài sản (Property)**, chọn đúng tài sản liên kết với Firebase của bạn (`share-now-c5a02` hoặc `ShareNow`).
4. Nhấp vào mục **Quản lý quyền truy cập vào tài sản (Property Access Management)**.
5. Bấm dấu cộng **`+`** ở góc trên bên phải -> chọn **Thêm người dùng (Add users)**.
6. Dán địa chỉ email Service Account:
   ```text
   ga4-955@share-now-c5a02.iam.gserviceaccount.com
   ```
7. Chọn vai trò: **Người xem (Viewer)** hoặc **Biên tập viên (Editor)** -> Bấm **Thêm (Add)**.
8. Cũng tại trang **Cài đặt tài sản (Property Settings)**, bạn copy dãy số **Mã tài sản (Property ID)** (gồm 9 hoặc 10 chữ số, ví dụ `432198765`).
9. Mở file `F:\APP\Share File\Share it\GA4\config.json`, điền mã này vào mục:
   ```json
   "property_id": "MÃ_TÀI_SẢN_CỦA_BẠN"
   ```

---

## 4. BƯỚC 3: LIÊN KẾT ADMOB VỚI FIREBASE / GA4 (ĐỂ KÉO DOANH THU AD REVENUE)

Việc liên kết này giúp AdMob tự động gửi số tiền kiếm được từ từng lượt xem quảng cáo (Impression-level ad revenue) sang Firebase & GA4.

1. Đăng nhập [Google AdMob Console](https://apps.admob.com/).
2. Ở thanh menu bên trái, chọn **Cài đặt (Settings)** -> **Dịch vụ được liên kết (Linked Services)**.
3. Trong tab **Ứng dụng (Apps)**, tìm ứng dụng **Share Now** (`com.nextai.sharenow`).
4. Bật công tắc liên kết với **Firebase** -> Chọn dự án Firebase `share-now-c5a02`.
5. Đảm bảo tích chọn: **Báo cáo chỉ số người dùng (User metrics)** và **Dữ liệu doanh thu từ quảng cáo (Impression-level ad revenue)**.
6. Nhấn **Xác nhận liên kết**.

---

## 5. BƯỚC 4: LIÊN KẾT GOOGLE ADS ĐỂ TỐI ƯU GIÁ THẦU THÔNG MINH (tROAS & tCPA)

Khi liên kết Google Ads với GA4, các chiến dịch chạy quảng cáo App Install (UAC) có thể tự động tối ưu theo doanh thu thật:

1. Đăng nhập [Google Ads Console](https://ads.google.com/).
2. Vào **Công cụ và cài đặt (Tools and Settings)** -> **Tài khoản được liên kết (Linked Accounts)**.
3. Tìm **Google Analytics (GA4) và Firebase** -> Bấm **Quản lý & Liên kết (Manage & Link)**.
4. Tìm tài sản `share-now-c5a02` -> Bấm **Liên kết (Link)** -> Bật **Nhập chỉ số đối tượng truy cập web/app**.
5. **Nhập các Sự Kiện Chuyển Đổi (Conversion Actions):**
   - Vào **Công cụ và cài đặt** -> **Lượt chuyển đổi (Conversions)** -> **Tạo lượt chuyển đổi mới**.
   - Chọn nguồn: **Nhập từ Google Analytics 4 (Thuộc tính ứng dụng)**.
   - Tích chọn các sự kiện quan trọng:
     - 🥇 `ad_revenue_tracked`: Dùng cho chiến lược **Target ROAS (tROAS)**.
     - 🥈 `in_app_purchased`: Dùng cho chiến lược **Tối đa hóa giá trị chuyển đổi**.
     - 🥉 `click_card_send` và `p2p_connected`: Dùng cho chiến lược **Target CPA (tCPA)** nhằm thu hút người dùng chất lượng cao, thường xuyên gửi nhận file.

---

## 6. BƯỚC 5: LIÊN KẾT GOOGLE PLAY CONSOLE VỚI FIREBASE

1. Mở [Google Play Console](https://play.google.com/console).
2. Vào **Cài đặt nhà phát triển (Developer settings)** -> **Tài khoản được liên kết (Linked accounts)**.
3. Chọn **Firebase** -> Bấm **Liên kết với Firebase** -> Chọn dự án `share-now-c5a02`.
4. Điều này giúp đồng bộ chính xác 100% doanh thu mua gói VIP (`weekly`, `monthlyplan`, `lifetimeplan`) và phát hiện tỷ lệ người dùng gỡ cài đặt (Uninstalls).

---

## 7. CÁCH SỬ DỤNG BỘ CÔNG CỤ TẠI `F:\APP\Share File\Share it\GA4`

Thư mục đã được tích hợp sẵn 4 công cụ tự động hóa cao:

1. **`pull_ga4_events.py`**:
   - Tự động kết nối Google Analytics Data API.
   - Kéo toàn bộ danh sách sự kiện, doanh thu AdMob theo vị trí, hiệu suất chiến dịch Google Ads và giao dịch In-App.
   - Xuất dữ liệu sạch ra các file CSV và JSON tại thư mục `output/`.

2. **`revenue_optimizer.py`**:
   - Thuật toán tự động tính toán eCPM, CTR, tỷ suất hoàn vốn ROAS và lợi nhuận ròng (Net Profit).
   - Xuất bản phân tích chiến lược giá thầu thông minh Smart Bidding và kế hoạch phân bổ ngân sách.

3. **`generate_dashboard.py`**:
   - Tạo file **`dashboard.html`** có giao diện trực quan, biểu đồ tương tác Chart.js (phân bổ doanh thu, eCPM từng format, phễu chuyển đổi người dùng, hiệu quả chiến dịch).
   - Bạn chỉ cần nhấp đúp vào `dashboard.html` là mở xem được ngay trên trình duyệt (Chrome, Edge) cực kỳ đẹp mắt.

4. **`export_bigquery_pipeline.py` & `bigquery_analytics_queries.sql`**:
   - Cung cấp các câu lệnh SQL viết sẵn để truy vấn sâu kho dữ liệu BigQuery miễn phí.

---

## 8. LỆNH CHẠY NHANH 1-CLICK

Chỉ cần chạy file batch hoặc câu lệnh sau từ terminal:
```powershell
cd "F:\APP\Share File\Share it\GA4"
.\run_ga4_pipeline.bat
```
Toàn bộ quá trình kéo dữ liệu, tính toán tối ưu và hiển thị Dashboard sẽ diễn ra tự động hoàn toàn!

