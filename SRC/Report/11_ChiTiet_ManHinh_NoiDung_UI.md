# Báo cáo Chi Tiết Danh Mục Màn Hình, Nội Dung & Cấu Trúc Giao Diện UI

## 1. Bảng Tổng Hợp Danh Mục Màn Hình Toàn Ứng Dụng

| # | Tên Màn Hình / Activity | Nội Dung Hiển Thị | Chức Năng & Nhiệm Vụ | Cấu Trúc UI & Thành Phần Views Chính |
|---|---|---|---|---|
| **1** | `SplashActivity` / `FlashActivity` | Logo thương hiệu SHAREit, Hoạt họa Lottie khởi động, Phiên bản ứng dụng | Khởi tạo MMKV, nạp cấu hình bảo mật, kiểm tra quyền và định hướng | `FrameLayout` chứa `ImageView` (Logo), `LottieAnimationView`, `TextView` version |
| **2** | `MainActivity` | Header, Hero Send/Receive Buttons, MCDS Banner Carousel, Quick Tools, Bottom Nav | Màn hình trung tâm điều phối toàn bộ luồng hoạt động của app | `CoordinatorLayout` + `AppBarLayout` + `ViewPager2` + `BottomNavigationView` |
| **3** | `SendFilePickerActivity` | Danh sách tệp phân loại theo 6 Tab: App, Video, Ảnh, Nhạc, Tài liệu, Tệp nén | Cho phép người dùng duyệt và chọn nhiều file cùng lúc để gửi đi | `TabLayout` + `ViewPager2` + `RecyclerView` đa cột + `FloatingActionBar` (Tổng dung lượng) |
| **4** | `RadarDiscoveryActivity` | Radar quét vòng tròn hiệu ứng sóng phát, Avatar các máy lân cận, Nút quét QR | Dò tìm thiết bị người nhận trong vùng phủ sóng Wi-Fi/Bluetooth | `CustomRadarView` (vẽ Canvas sóng radar) + `ConstraintLayout` chứa Avatar thiết bị |
| **5** | `ReceiveWaitingActivity` | Mã QR khổ lớn, Tên mạng Wi-Fi Hotspot SSID, Mật khẩu WPA2, Hướng dẫn kết nối | Thiết lập trạm phát Hotspot và hiển thị thông tin để máy gửi kết nối | `CardView` bọc `QRCodeImageView`, `TextView` SSID/Pass, `LottieAnimationView` sóng phát |
| **6** | `TransferProgressActivity` | Danh sách các file đang truyền, Thanh phần trăm (%), Tốc độ (MB/s), Nút tạm dừng/hủy | Quản lý luồng gửi/nhận dữ liệu đa luồng qua Socket TCP | `RecyclerView` với `TransferItemHolder` (Icon, Tên file, `ProgressBar`, Speed label) |
| **7** | `TransferResultActivity` | Hoạt họa hoàn thành, Tổng dung lượng đã truyền, Thời gian thực hiện, Danh sách file | Báo cáo kết quả phiên truyền, mở nhanh file vừa nhận, gợi ý tương tác | `NestedScrollView`, `LottieAnimationView` tích xanh, `RecyclerView` file đã truyền |
| **8** | `WebShareActivity` | Địa chỉ IP/Port cục bộ, Mã QR kết nối trình duyệt, Trạng thái máy tính kết nối | Chia sẻ file qua trình duyệt Web của máy tính không cần cài app | `CardView` hiển thị URL `http://192.168.x.x:port`, `QRCodeImageView`, Danh sách chia sẻ |
| **9** | `CleanitActivity` | Đồng hồ đo dung lượng rác (MB), Danh mục rác (Cache, APK thừa, Tệp tạm), Nút dọn | Quét và giải phóng bộ nhớ lưu trữ rác để tăng tốc thiết bị | `CircularProgressView` đo rác + `RecyclerView` nhóm rác + `Button` "Dọn dẹp ngay" |
| **10**| `SafeboxUnlockActivity` | Bàn phím số PIN 6 ô, Nút quét vân tay sinh trắc học, Nút quên mật khẩu | Xác thực người dùng trước khi cho phép vào két khóa bảo mật | `PinEntryEditText` (6 ô mã PIN), `KeypadView` (Bàn phím ảo), `BiometricPrompt` |
| **11**| `SafeboxVaultActivity` | Lưới ảnh/video đã mã hóa, Nút thêm tệp (+), Nút giải mã xuất ra ngoài | Quản lý kho tệp riêng tư đã mã hóa AES-256-GCM | `Toolbar` có nút khóa nhanh + `RecyclerView` lưới ảnh bảo vệ + `FAB` thêm tệp |
| **12**| `MuslimDailyActivity` | Bảng giờ cầu nguyện 5 khung, Đồng hồ đếm ngược đến giờ kế, Nút la bàn, Tasbih | Trung tâm tôn giáo Muslim: nhắc giờ cầu nguyện theo vị trí GPS | `CardView` giờ cầu nguyện + `LottieAnimationView` + Lưới 4 nút tiện ích Muslim |
| **13**| `QiblaCompassActivity` | Mặt đồng hồ la bàn 360 độ, Kim chỉ hướng Kaaba (Mecca), Góc phương vị (Độ) | Điều hướng hướng cầu nguyện chính xác dựa vào la bàn từ tính | `CustomCompassView` (Vẽ mặt la bàn xoay theo cảm biến góc `SensorEventListener`) |
| **14**| `QuranReaderActivity` | Danh sách 114 Chương (Surah), Giao diện đọc văn bản tiếng Ả Rập, Trình phát audio | Đọc và nghe kinh Quran với bản dịch đa ngôn ngữ | `RecyclerView` Surahs + `NestedScrollView` từng câu thơ Ayah + `AudioPlayerBottomBar` |
| **15**| `TasbihActivity` | Chuỗi hạt tràng tròn, Bộ đếm số điện tử, Nút bấm đếm, Nút đặt lại về 0 | Hỗ trợ tín đồ đếm số lần niệm kinh kèm rung phản hồi xúc giác | `TextView` số lượng lớn + `CustomBeadView` xoay hạt khi bấm + `Button` Reset/Target |
| **16**| `CoinStoreActivity` | Bảng điểm danh 7 ngày, Số dư Coin hiện có, Danh sách nhiệm vụ, Kho quà đổi thưởng | Trung tâm Gamification thu hút người dùng tương tác nhận thưởng | `HeaderCard` số dư coin + `HorizontalCalendarView` điểm danh + `RecyclerView` Tasks |
| **17**| `PaywallActivity` | Danh sách đặc quyền VIP, Bảng 4 gói cước (Tuần, Tháng, Năm, Trọn đời), Nút Mua | Chuyển đổi người dùng sang thuê bao VIP không quảng cáo | `BottomSheetDialog` hoặc `Activity` nền mờ, `RadioGroup` chọn gói cước, `GradientButton` |
| **18**| `WebDownloaderActivity` | Thanh nhập URL trang web, Trình duyệt WebView, Nút phát hiện video tải về | Tải video từ các trang mạng xã hội và web chia sẻ | `EditText` URL search + `WebView` có JS video detector + `FAB` nút tải xuống nổi bật |
| **19**| `SiMediaPlayerActivity`| Khung hình video toàn màn hình, Thanh tua thời gian, Nút chỉnh tốc độ (0.5x - 2x) | Trình phát video chuyên nghiệp hỗ trợ phụ đề và phát nền | `SurfaceView` / `TextureView` + `MediaControllerOverlay` + `AudioTrackSelector` |
| **20**| `SettingsActivity` | Danh sách tùy chọn cài đặt: Lưu trữ, Mạng 5GHz, Thông báo, Ngôn ngữ, Về app | Cho phép người dùng tùy biến hành vi của ứng dụng | `PreferenceFragmentCompat` hoặc `RecyclerView` dạng nhóm thiết lập |

---

## 2. Phân Tích Cấu Trúc Giao Diện Chi Tiết 3 Màn Hình Trọng Tâm

### 2.1. Màn hình Chính (`MainActivity`)
* **Layout Root**: `androidx.coordinatorlayout.widget.CoordinatorLayout`
* **Cấu trúc bên trong**:
  1. `AppBarLayout` có hiệu ứng cuộn mượt mà:
     - `Toolbar`: Chứa Logo, Icon Tìm kiếm, Icon QR Scanner và Icon Vương miện VIP vàng.
  2. `NestedScrollView` chứa nội dung trang chủ:
     - **Hero Transfer Card (`ConstraintLayout`)**: Nút tròn lớn màu xanh *"GỬI"* bên trái, nút tròn lớn màu cam *"NHẬN"* bên phải, nút phụ *"WebShare"* và *"Phone Clone"* bên dưới.
     - **MCDS Banner Container (`ViewPager2` + `CircleIndicator`)**: Tự động trượt các banner quảng bá sự kiện nhận được từ API `/api/v1/mcds/fetch`.
     - **Quick Tools Grid (`GridLayout` 4 cột)**: Lối tắt vào Dọn rác, Két khóa, Trình phát nhạc, Muslim Daily.
     - **Recent Transfers Card (`RecyclerView`)**: Danh sách 3 phiên truyền file gần nhất kèm nút xem tất cả.
  3. `BottomNavigationView`: Cố định dưới đáy màn hình với 4 Tab: *Trang chủ*, *Tệp*, *Muslim*, *Tôi*.

---

### 2.2. Màn hình Tiến Độ Truyền File (`TransferProgressActivity`)
* **Layout Root**: `androidx.constraintlayout.widget.ConstraintLayout`
* **Cấu trúc bên trong**:
  1. `HeaderInfoView`:
     - Tên và Avatar của thiết bị đối tác đang kết nối.
     - Nhãn phương thức kết nối: `Wi-Fi Direct (5 GHz)` hoặc `Local Hotspot`.
     - Nhãn tốc độ truyền tải thời gian thực: `42.5 MB/s` (Font số lớn đậm).
  2. `TransferOverallProgress`:
     - `ProgressBar` tổng thể phiên truyền: ví dụ `65% (780 MB / 1.2 GB)`.
     - Thời gian còn lại ước tính: `Còn 10 giây`.
  3. `RecyclerView` danh sách chi tiết từng tệp tin:
     - Icon loại tệp (Video, Ảnh, APK...).
     - Tên tệp và kích thước (ví dụ: `game_update.apk - 450 MB`).
     - Thanh trạng thái mini riêng của từng tệp: `Đang gửi (80%)`, `Chờ...`, `Đã xong`.
  4. `BottomActionBar`: Nút *"Tạm dừng"* và nút *"Hủy bỏ"*.

---

### 2.3. Màn hình Paywall Nâng Cấp VIP (`PaywallActivity`)
* **Layout Root**: `androidx.constraintlayout.widget.ConstraintLayout` (Chế độ Dark/Gold Theme sang trọng).
* **Cấu trúc bên trong**:
  1. Nút Đóng (X) góc trên bên trái & Nút *"Khôi phục giao dịch"* góc trên bên phải.
  2. Header Icon: Vương miện VIP ánh kim 3D kèm tiêu đề *"Trải Nghiệm SHAREit VIP Hoàn Hảo"*.
  3. Lưới 3 Lợi ích cốt lõi (`LinearLayout` dạng Bullet points kèm icon tích vàng):
     - ✨ *Không có bất kỳ quảng cáo nào (100% Ad-free)*.
     - 🚀 *Truyền file tốc độ tối đa lên tới 200 MB/s*.
     - 🔒 *Két an toàn Safebox đám mây dung lượng 200 GB*.
  4. Bộ chọn gói thuê bao (`RadioGroup` bọc 4 `CardView`):
     - Thẻ Gói Năm (Gắn huy hiệu nổi bật *"TIẾT KIỆM 50% - PHỔ BIẾN NHẤT"*).
     - Thẻ Gói Tháng, Gói Tuần và Gói Trọn Đời.
  5. Nút Kêu gọi Hành động chính (`MaterialButton` dải màu Gradient Vàng - Cam): *"DÙNG THỬ 3 NGÀY MIỄN PHÍ & ĐĂNG KÝ"*.
  6. Dòng cam kết bảo mật & liên kết: *"Hủy bất kỳ lúc nào trên Google Play. Điều khoản dịch vụ & Chính sách bảo mật"*.

