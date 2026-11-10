# Báo cáo Phân Cấp Menu, Tab & Cây Tính Năng Từ Cha Xuống Con

## 1. Cây Phả Hệ Phân Cấp Menu & Tab Tổng Thể (Hierarchical Tree)

```
[MÀN HÌNH CHÍNH - MAIN ACTIVITY]
├── 1. THANH TIÊU ĐỀ & TIỆN ÍCH TRÊN CÙNG (TOP APP BAR & HEADER)
│   ├── [1.1] Nút Menu Ngăn Kéo (Navigation Drawer Toggle)
│   ├── [1.2] Biểu Tượng Vương Miện VIP (Mở Paywall Subscription)
│   ├── [1.3] Biểu Tượng Quét Mã QR (Mở Camera Scanner)
│   └── [1.4] Biểu Tượng Tìm Kiếm Nhanh (Search Bar Global)
│
├── 2. KHU VỰC TRUYỀN DỮ LIỆU CHÍNH (HERO ACTION ZONE)
│   ├── [2.1] Nút "GỬI" (Send Button) -> Mở Bộ Chọn Tệp Đa Thể Loại
│   ├── [2.2] Nút "NHẬN" (Receive Button) -> Khởi Tạo Trạm Nhận & Mã QR
│   ├── [2.3] Nút "WEBSHARE" (Kết Nối Máy Tính Qua Trình Duyệt)
│   └── [2.4] Nút "CHUYỂN ĐỔI ĐIỆN THOẠI" (Phone Clone Data Transfer)
│
├── 3. THANH ĐIỀU HƯỚNG DƯỚI CÙNG (BOTTOM NAVIGATION BAR - 4 TABS)
│   │
│   ├── [TAB 1: TRANG CHỦ - HOME]
│   │   ├── Khối Truyền P2P (Send / Receive / WebShare)
│   │   ├── Khối MCDS Dynamic Banner Slots (Banner quảng bá, sự kiện)
│   │   ├── Khối Lối Tắt Nhanh (Quick Tools: Dọn rác, Két khóa, Nhạc, Video)
│   │   └── Khối Lịch Sử Truyền Gần Đây (Recent Transfer History)
│   │
│   ├── [TAB 2: QUẢN LÝ TỆP - FILES]
│   │   ├── Phân Loại Tệp Theo Danh Mục (Categories Grid)
│   │   │   ├── Ứng Dụng (Apps Manager: APK đã cài, File cài APK)
│   │   │   ├── Video (Phân loại theo Thư mục, Độ phân giải, Thời lượng)
│   │   │   ├── Ảnh (Ảnh máy ảnh, Ảnh chụp màn hình, WhatsApp)
│   │   │   ├── Âm Nhạc (Bài hát, Ca sĩ, Album, Danh sách phát)
│   │   │   ├── Tài Liệu (PDF, DOCX, XLSX, PPTX, TXT)
│   │   │   └── Tệp Nén (Zip, 7z, RAR)
│   │   ├── Bộ Phân Tích Bộ Nhớ (Storage Analyzer: Biểu đồ % đã dùng)
│   │   └── Menu Thao Tác Tệp Ngữ Cảnh (Context Menu: Chia sẻ, Đổi tên, Khóa két, Xóa)
│   │
│   ├── [TAB 3: MUSLIM DAILY - TIỆN ÍCH HỒI GIÁO]
│   │   ├── Bảng Giờ Cầu Nguyện Thời Gian Thực (5 Khung Giờ: Fajr -> Isha)
│   │   ├── La Bàn Qibla Điều Hướng Trực Quan Hướng Về Thánh Địa Mecca
│   │   ├── Trình Đọc Kinh Quran (114 Surahs, Bản dịch, Âm thanh tụng kinh)
│   │   ├── Tràng Hạt Tasbih Điện Tử Đếm Số Lần Niệm
│   │   └── Bộ Sưu Tập Lời Cầu Nguyện Athkar & 99 Tên của Thánh Allah
│   │
│   └── [TAB 4: TÔI / CÁ NHÂN - ME / PROFILE]
│       ├── Hồ Sơ Người Dùng (Avatar, Tên hiển thị, Huy hiệu VIP)
│       ├── Ví Điểm Thưởng Coin & Nhiệm Vụ (Daily Check-in, Reward Store)
│       ├── Két Khóa Riêng Tư Safebox (Quản lý ảnh/video đã mã hóa AES)
│       ├── Trình Quản Lý Tải Về (Download Center & Trình duyệt Web)
│       └── Cài Đặt Ứng Dụng (Settings Submenu)
│
└── 4. MENU NGĂN KÉO CÀI ĐẶT (SETTINGS & DRAWER SUB-MENUS)
    ├── Quản Lý Tài Khoản (Đăng nhập Google, Apple, SĐT)
    ├── Cài Đặt Truyền File (Đổi tên Hotspot, Thư mục lưu trữ, Tần số 5GHz)
    ├── Quản Lý Quyền Riêng Tư (Xóa bộ nhớ đệm, Yêu cầu xóa dữ liệu GDPR)
    ├── Kiểm Tra Cập Nhật Ứng Dụng (Version Update Check)
    ├── Phản Hồi & Đánh Giá Ứng Dụng (Feedback with Screenshot)
    └── Điều Khoản Dịch Vụ & Chính Sách Bảo Mật
```

---

## 2. Bảng Mô Tả Chi Tiết Chức Năng Menu Từ Cha Xuống Con

| Menu Cha | Menu Con / Tab | Thành phần UI / Class | Chức năng & Nhiệm vụ Chi tiết |
|---|---|---|---|
| **Bottom Bar** | **Home Tab** | `HomeFragment.kt` | Trang chủ điều phối hành động truyền file, banner MCDS, lối tắt tiện ích. |
| Home | Nút Gửi | `SendActivity.kt` | Mở giao diện chọn tệp đa năng và chuyển tiếp vào Radar quét P2P. |
| Home | Nút Nhận | `ReceiveActivity.kt` | Bật trạm phát Wi-Fi Hotspot nội bộ và sinh mã QR chờ kết nối. |
| Home | WebShare | `WebShareActivity.kt` | Khởi động mini HTTP server để chia sẻ file với trình duyệt máy tính. |
| **Bottom Bar** | **Files Tab** | `FileManagerFragment.kt` | Trình quản lý toàn diện mọi tệp tin trong bộ nhớ trong và thẻ nhớ SD. |
| Files | Quản lý Ứng dụng | `AppListFragment.kt` | Trích xuất file APK của app đã cài đặt để truyền sang máy khác. |
| Files | Quản lý Video | `VideoListFragment.kt` | Xem danh sách video, phát thử, sắp xếp theo kích thước giảm dần. |
| Files | Quản lý Ảnh | `PhotoGridFragment.kt` | Duyệt thư viện ảnh theo dòng thời gian ngày chụp. |
| Files | Quản lý Tài liệu | `DocListFragment.kt` | Lọc nhanh các file văn phòng PDF, Word, Excel, PowerPoint. |
| Files | Menu Ngữ Cảnh (Context) | `FileActionBottomSheet.kt` | Cung cấp: Chia sẻ P2P, Thêm vào Két Safebox, Đổi tên, Chi tiết, Xóa. |
| **Bottom Bar** | **Muslim Tab** | `MuslimDailyActivity.kt` | Trung tâm tôn giáo Muslim: giờ cầu nguyện, la bàn Qibla, kinh Quran. |
| Muslim | La Bàn Qibla | `QiblaCompassActivity.kt` | Sử dụng cảm biến từ kế và gia tốc kế chỉ hướng thánh địa Mecca. |
| Muslim | Kinh Quran | `QuranReaderActivity.kt` | Hiển thị văn bản tiếng Ả Rập và âm thanh tụng kinh từng câu thơ. |
| Muslim | Tràng Hạt Tasbih | `TasbihActivity.kt` | Giao diện đếm hạt rung phản hồi xúc giác (Haptic feedback). |
| **Bottom Bar** | **Me Tab** | `MeProfileFragment.kt` | Quản lý thông tin cá nhân, ví coin, trạng thái VIP và cài đặt. |
| Me | Két Safebox | `SafeboxUnlockActivity.kt` | Xác thực mã PIN / vân tay để truy cập vùng lưu trữ mã hóa AES-256. |
| Me | Ví Coin & Task | `CoinStoreActivity.kt` | Điểm danh 7 ngày liên tục, hoàn thành nhiệm vụ và đổi voucher. |
| Me | Nâng cấp VIP | `PaywallActivity.kt` | Xem bảng giá các gói VIP và thanh toán qua Google Play Billing. |
| Me | Cài đặt chung | `SettingsActivity.kt` | Tùy chỉnh ngôn ngữ, dải tần Wi-Fi 5GHz, thông báo và bảo mật. |

