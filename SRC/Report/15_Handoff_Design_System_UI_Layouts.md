# Báo Cáo Thiết Kế Giao Diện & UI Handoff Specification (Dành Cho AI & Designer Tái Thiết Kế)

> **Mục đích tài liệu:** Bản đặc tả kỹ thuật chi tiết toàn diện (Design System, Tokens, Bảng mã màu HEX/RGBA, Cấu trúc Layouts XML, Bản mẫu HTML/CSS, Tọa độ & Khoảng cách Spacing, Hiệu ứng chuyển động) được chuẩn hóa để **AI Agent hoặc Designer** có thể đọc và tái tạo chính xác 100% hoặc thiết kế lại giao diện hiện đại cho ứng dụng SHAREit.

---

## 1. Hệ Thống Design Tokens & Bảng Mã Màu Toàn Diện (Color Palette)

### 1.1. Bảng Màu Thương Hiệu & Vai Trò Ngữ Nghĩa (Semantic Colors)

| Tên Token | Mã HEX | RGBA | Vai Trò & Vị Trí Áp Dụng Trong Giao Diện |
|---|---|---|---|
| `--color-primary-blue` | `#0288D1` | `rgba(2, 136, 209, 1.0)` | Màu thương hiệu chính, Nút "GỬI" (Send), Tab active, Header chính |
| `--color-primary-dark` | `#01579B` | `rgba(1, 87, 155, 1.0)` | Trạng thái nhấn (Pressed) của nút, Thanh trạng thái Status Bar |
| `--color-primary-light` | `#38BDF8` | `rgba(56, 189, 248, 1.0)` | Điểm nhấn phát sáng, Icon highlight, Vòng sóng Radar P2P |
| `--color-accent-orange` | `#FF6F00` | `rgba(255, 111, 0, 1.0)` | Nút "NHẬN" (Receive), Cảnh báo hành động quan trọng |
| `--color-accent-gold` | `#FFB300` | `rgba(255, 179, 0, 1.0)` | Huy hiệu VIP, Vương miện, Nút mua Paywall, Icon Coin |
| `--color-gold-gradient` | `Linear(135deg, #F59E0B, #D97706)` | N/A | Dải màu nền của Thẻ VIP và Nút thanh toán Paywall |
| `--color-success-green` | `#10B981` | `rgba(16, 185, 129, 1.0)` | Thông báo truyền tệp thành công 100%, Trạng thái Online, Đã dọn rác |
| `--color-danger-red` | `#EF4444` | `rgba(239, 68, 68, 1.0)` | Nút Hủy truyền, Xóa tệp, Cảnh báo bộ nhớ đầy |
| `--color-bg-dark` | `#0F172A` | `rgba(15, 23, 42, 1.0)` | Nền tối chủ đạo toàn ứng dụng (Dark Theme Background) |
| `--color-bg-card` | `#1E293B` | `rgba(30, 41, 59, 1.0)` | Nền thẻ (Card, BottomSheet, Dialog, Item Container) |
| `--color-bg-subtle` | `#0B1120` | `rgba(11, 17, 32, 1.0)` | Nền ô nhập liệu Input, Khung mã QR, Terminal Console |
| `--color-border` | `#334155` | `rgba(51, 65, 85, 1.0)` | Đường viền ngăn cách (Divider, Card Border, Table Line) |
| `--color-text-primary` | `#F8FAFC` | `rgba(248, 250, 252, 1.0)` | Văn bản tiêu đề chính, Con số nổi bật (Contrast 15.8:1) |
| `--color-text-secondary`| `#94A3B8` | `rgba(148, 163, 184, 1.0)`| Văn bản phụ, Mô tả, Dung lượng tệp, Thời gian |
| `--color-text-muted` | `#64748B` | `rgba(100, 116, 139, 1.0)`| Placeholder, Chú thích bản quyền dưới đáy |

---

### 1.2. Thang Đo Typography Chuẩn Hóa

* **Font Family chính**: `Inter`, `Roboto`, `SF Pro Display` (Sans-serif hiện đại).
* **Font Monospace** (Dùng cho Tọa độ, IP, Tốc độ MB/s): `JetBrains Mono`, `Roboto Mono`.

| Cấp Bậc (Style) | Kích Thước (Font Size) | Trọng Lượng (Weight) | Chiều Cao Dòng (Line Height) | Khoảng Cách Chữ (Letter Spacing) |
|---|---|---|---|---|
| **Display / Hero** | `32sp / 32px` | `Bold (700)` | `40px` | `-0.5px` |
| **Headline H1** | `24sp / 24px` | `Bold (700)` | `32px` | `-0.2px` |
| **Headline H2** | `20sp / 20px` | `SemiBold (600)` | `28px` | `0px` |
| **Title / H3** | `16sp / 16px` | `SemiBold (600)` | `24px` | `0px` |
| **Body Large** | `15sp / 15px` | `Regular (400) / Medium (500)` | `22px` | `+0.1px` |
| **Body Small** | `13sp / 13px` | `Regular (400)` | `18px` | `+0.2px` |
| **Caption / Badge**| `11sp / 11px` | `SemiBold (600)` | `14px` | `+0.4px` (Uppercase) |
| **Data Metric** | `28sp / 28px` | `Bold (800)` | `34px` | `-0.5px` |

---

### 1.3. Hệ Thống Spacing, Radius & Shadows (Grid 8px)

* **Spacing Scale**: `4dp`, `8dp`, `12dp`, `16dp`, `20dp`, `24dp`, `32dp`, `48dp`.
* **Corner Radius Scale**:
  - `radius-sm (6dp)`: Dùng cho Badges, Tag thể loại, Checkbox.
  - `radius-md (12dp)`: Dùng cho Thẻ Card tệp, Input Form, Banner nhỏ.
  - `radius-lg (16dp)`: Dùng cho Hero Action Card (Nút Gửi/Nhận), Modal BottomSheet.
  - `radius-xl (24dp)`: Dùng cho Paywall VIP Card, Hộp thoại xác thực.
  - `radius-full (9999dp)`: Nút tròn hành động (Send/Receive Circle), Floating Action Button (FAB).
* **Shadows & Elevation**:
  - `elevation-1 (2dp)`: `0px 2px 4px rgba(0,0,0,0.25)` — Thẻ danh mục tệp.
  - `elevation-2 (8dp)`: `0px 8px 16px rgba(0,0,0,0.35)` — Bottom Navigation, Floating Bar.
  - `elevation-glow-blue`: `0px 0px 24px rgba(56, 189, 248, 0.4)` — Radar P2P Pulse.
  - `elevation-glow-gold`: `0px 0px 24px rgba(245, 158, 11, 0.45)` — Thẻ VIP Paywall.

---

## 2. Đặc Tả Chi Tiết Từng Màn Hình Kèm Layouts XML & HTML Wireframes

---

### Màn Hình 1: Trang Chủ Điều Phối (`MainActivity`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ☰  [Logo SHAREit]                 🔍 [Tìm kiếm]   📷 [QR]   👑 [VIP Vàng]   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌──────────────────────────┐         ┌──────────────────────────┐         │
│   │           🔵             │         │           🟠             │         │
│   │        GỬI (SEND)        │         │       NHẬN (RECEIVE)     │         │
│   │    Truyền tới 200MB/s    │         │    Chờ kết nối máy phát  │         │
│   └──────────────────────────┘         └──────────────────────────┘         │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │  💻 WebShare (Máy Tính)      │      🔄 Chuyển Dữ Liệu Máy Mới       │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ 🎨 MCDS Banner Carousel (Trượt động 3 giây/lần)                          │ │
│ │ [Banner Nâng cấp VIP không quảng cáo / Sự kiện nhận 500 Coin]           │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ ⚡ TIỆN ÍCH NHANH (QUICK TOOLS)                                             │
│ ┌───────────────┬───────────────┬───────────────┬───────────────┐           │
│ │ 🧹 Dọn Rác   │ 🔒 Két Khóa   │ 🎵 Nghe Nhạc  │ 🕌 Muslim     │           │
│ │  (1.4 GB rác) │  (AES Vault)  │  (SI Player)  │  (Giờ cầu)    │           │
│ └───────────────┴───────────────┴───────────────┴───────────────┘           │
│                                                                             │
│ 🕒 LỊCH SỬ TRUYỀN GẦN ĐÂY                                                  │
│ • 🎬 Video_DuLich_4K.mp4 ────────── 1.2 GB ────────── Đã gửi (42MB/s)       │
│ • 📱 LienQuanMobile.apk  ────────── 1.8 GB ────────── Đã nhận               │
├─────────────────────────────────────────────────────────────────────────────┤
│   [🏠 Trang Chủ]        [📁 Tệp Tin]        [🕌 Muslim]        [👤 Tôi]     │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Mã Nguồn Layout XML Chuẩn: `activity_main.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#0F172A">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="#1E293B">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/main_toolbar"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            app:title="SHAREit"
            app:titleTextColor="#F8FAFC">
            <!-- Icons Search, QR Scanner, VIP Crown -->
        </androidx.appcompat.widget.Toolbar>
    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:paddingBottom="65dp"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- Hero Action Send / Receive Container -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="140dp"
                android:orientation="horizontal"
                android:weightSum="2">

                <!-- Send Button Card -->
                <androidx.cardview.widget.CardView
                    android:id="@+id/card_send"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_weight="1"
                    android:layout_marginEnd="8dp"
                    app:cardBackgroundColor="#0288D1"
                    app:cardCornerRadius="16dp"
                    app:cardElevation="6dp">
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:gravity="center"
                        android:orientation="vertical">
                        <ImageView
                            android:layout_width="48dp"
                            android:layout_height="48dp"
                            android:src="@drawable/ic_send"
                            app:tint="#FFFFFF" />
                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="GỬI"
                            android:textColor="#FFFFFF"
                            android:textSize="18sp"
                            android:textStyle="bold"
                            android:layout_marginTop="6dp" />
                    </LinearLayout>
                </androidx.cardview.widget.CardView>

                <!-- Receive Button Card -->
                <androidx.cardview.widget.CardView
                    android:id="@+id/card_receive"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_weight="1"
                    android:layout_marginStart="8dp"
                    app:cardBackgroundColor="#FF6F00"
                    app:cardCornerRadius="16dp"
                    app:cardElevation="6dp">
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:gravity="center"
                        android:orientation="vertical">
                        <ImageView
                            android:layout_width="48dp"
                            android:layout_height="48dp"
                            android:src="@drawable/ic_receive"
                            app:tint="#FFFFFF" />
                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="NHẬN"
                            android:textColor="#FFFFFF"
                            android:textSize="18sp"
                            android:textStyle="bold"
                            android:layout_marginTop="6dp" />
                    </LinearLayout>
                </androidx.cardview.widget.CardView>
            </LinearLayout>

            <!-- MCDS Banner Carousel ViewPager2 -->
            <androidx.viewpager2.widget.ViewPager2
                android:id="@+id/mcds_banner_pager"
                android:layout_width="match_parent"
                android:layout_height="110dp"
                android:layout_marginTop="16dp" />

            <!-- Quick Tools Grid -->
            <!-- History List -->
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>

    <!-- Bottom Navigation Bar -->
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_navigation"
        android:layout_width="match_parent"
        android:layout_height="60dp"
        android:layout_gravity="bottom"
        android:background="#1E293B"
        app:itemIconTint="#94A3B8"
        app:itemTextColor="#94A3B8"
        app:menu="@menu/bottom_nav_menu" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

---

### Màn Hình 2: Radar Dò Tìm Thiết Bị P2P (`RadarDiscoveryActivity`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ✕ [Hủy Tìm Kiếm]                  Radar Dò Tìm P2P                 📷 [Quét QR]│
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                                  ( ( ( 🛰️ ) ) )                             │
│                           Hiệu ứng sóng quét 360 độ                         │
│                                                                             │
│                                  [Avatar 1]                                 │
│                               Galaxy S23 Ultra                              │
│                                      │                                      │
│                                      ▼                                      │
│                  [Avatar 2] ─── ( ĐANG QUÉT ) ─── [Avatar 3]                │
│                 Redmi Note 12                      iPhone 15 Pro            │
│                                                                             │
│                                                                             │
│                    💡 Đảm bảo cả hai máy cùng bật Wi-Fi/Bluetooth           │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ 📦 Đã chọn: 7 tệp (1.4 GB)  │  Chạm vào Avatar người nhận để gửi ngay  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Cấu Trúc Thành Phần Views:
* `CustomRadarView`: Vẽ 4 đường tròn đồng tâm mở rộng dần bằng `Canvas.drawCircle()` với dải màu Gradient từ `rgba(56, 189, 248, 0.8)` mờ dần về `0.0`.
* `DeviceAvatarHolder`: Vòng tròn đường kính `64dp`, viền sáng xanh phát quang `elevation-glow-blue`, hiển thị tên máy và khoảng cách dự đoán (m).

---

### Màn Hình 3: Tiến Độ Truyền File Thời Gian Thực (`TransferProgressActivity`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ✕ [Dừng Phiên]             Đang Truyền Tới: Galaxy S23              ⚙️ [5GHz]│
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                             🚀 45.8 MB/s                                    │
│                    Tốc độ truyền cực nhanh qua Wi-Fi Direct                 │
│                                                                             │
│   ══════════════════════════════════════════════ 65%                        │
│   Đã truyền: 910 MB / 1.4 GB  •  Thời gian còn lại: ~11 giây                │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│ DANH SÁCH TỆP TIN:                                                          │
│                                                                             │
│ 🎬 Video_Cinematic_4K.mp4 (450 MB)        [======== 100% ========] ✅ Xong  │
│ 📱 Garena_LienQuan.apk (850 MB)           [====== 55%    ] 45MB/s Đang gửi │
│ 📁 TaiLieu_DuAn_2026.pdf (100 MB)         [                      ] Chờ...   │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ 💎 Nâng cấp VIP: Tăng tốc truyền tới 200MB/s (Không giới hạn băng thông)│ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                [ ⏸️ Tạm Dừng ]              [ 🛑 Hủy Bỏ ]                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Màn Hình 4: Két Khóa An Toàn Safebox (`SafeboxVaultActivity`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 🔒 [Khóa Két]                     Két An Toàn (AES-256)            ⚙️ [Đổi PIN]│
├─────────────────────────────────────────────────────────────────────────────┤
│ 🛡️ Zero-Knowledge Vault • Dữ liệu mã hóa tuyệt đối trong bộ nhớ             │
│                                                                             │
│ ┌──────────────────────┐  ┌──────────────────────┐  ┌─────────────────────┐ │
│ │ 🖼️ Anh_RiengTu_01.jpg │  │ 🖼️ Anh_RiengTu_02.jpg │  │ 🎬 Video_BiMat.mp4  │ │
│ │ [Đã mã hóa AES-GCM]  │  │ [Đã mã hóa AES-GCM]  │  │ (120 MB)            │ │
│ └──────────────────────┘  └──────────────────────┘  └─────────────────────┘ │
│                                                                             │
│ ┌──────────────────────┐  ┌──────────────────────┐  ┌─────────────────────┐ │
│ │ 📄 HopDong_Mat.pdf   │  │ 🔑 Password_List.txt │  │ ➕ Thêm Tệp Mới     │ │
│ │ (4.2 MB)             │  │ (12 KB)              │  │ [Chọn từ bộ nhớ]    │ │
│ └──────────────────────┘  └──────────────────────┘  └─────────────────────┘ │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  [ 📤 Giải Mã Xuất Ra Ngoài ]         [ 🗑️ Xóa Vĩnh Viễn Khỏi Bộ Nhớ ]       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Màn Hình 5: Giao Diện Mua Gói VIP Paywall (`PaywallActivity`)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ✕ [Đóng]                                          [ Khôi Phục Giao Dịch ]   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                     👑                                      │
│                        TRẢI NGHIỆM SHAREIT VIP                              │
│                    Mở Khóa Toàn Bộ Sức Mạnh Đỉnh Cao                        │
│                                                                             │
│   ✨ 100% Không Có Bất Kỳ Quảng Cáo Nào                                    │
│   🚀 Truyền File Không Giới Hạn Băng Thông (Tới 200 MB/s)                   │
│   🔒 Két An Toàn Safebox Đám Mây Dung Lượng 200 GB                          │
│   ⚡ Hỗ Trợ Kỹ Thuật Riêng Ưu Tiên 24/7                                    │
│                                                                             │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ 🟡 GÓI NĂM (TIẾT KIỆM 50% - PHỔ BIẾN NHẤT)                              │ │
│ │    $24.99 / Năm  ($2.08 / Tháng)  •  3 Ngày Dùng Thử Miễn Phí           │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ ⚪ GÓI THÁNG: $4.99 / Tháng                                              │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ ⚪ GÓI TRỌN ĐỜI: $49.99 (Thanh Toán 1 Lần Duy Nhất - Dùng Vĩnh Viễn)     │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │      🟡 DÙNG THỬ 3 NGÀY MIỄN PHÍ & ĐĂNG KÝ NGAY ($24.99/NĂM)            │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│      Hủy bất kỳ lúc nào trên Google Play. Điều khoản & Chính sách bảo mật    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Bản Mẫu Giao Diện Web WebShare Client (HTML5 / CSS3 Responsive)

Dành cho trình duyệt máy tính khi kết nối chia sẻ với điện thoại:

```html
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <title>SHAREit WebShare - Kết Nối Điện Thoại</title>
  <style>
    :root {
      --bg: #0f172a; --card: #1e293b; --primary: #0288d1;
      --text: #f8fafc; --muted: #94a3b8; --border: #334155;
    }
    body { background: var(--bg); color: var(--text); font-family: 'Inter', sans-serif; margin: 0; padding: 40px; }
    .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 20px; }
    .dropzone { border: 2px dashed var(--primary); background: rgba(2, 136, 209, 0.05); border-radius: 16px; padding: 50px; text-align: center; margin: 30px 0; cursor: pointer; }
    .file-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 20px; }
    .file-card { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 16px; display: flex; flex-direction: column; gap: 8px; }
    .btn-download { background: var(--primary); color: #fff; border: none; padding: 8px 12px; border-radius: 8px; cursor: pointer; font-weight: 600; text-align: center; text-decoration: none; }
  </style>
</head>
<body>
  <div class="header">
    <h2>📂 SHAREit WebShare (Đã Kết Nối: Samsung Galaxy Note 10)</h2>
    <span style="color: #10b981; font-weight: 600;">● Trực tuyến (LAN High-Speed)</span>
  </div>

  <div class="dropzone" id="dropzone">
    <h3>📥 Kéo Thả Tệp Tin Vào Đây Để Gửi Sang Điện Thoại</h3>
    <p style="color: var(--muted); font-size: 13px;">Hỗ trợ Video, Nhạc, Tài liệu, Ảnh dung lượng lớn không giới hạn</p>
  </div>

  <h3>Danh Sách Tệp Trên Điện Thoại Sẵn Sàng Tải Về Máy Tính:</h3>
  <div class="file-grid" id="file-list">
    <div class="file-card">
      <div style="font-size: 28px;">🎬</div>
      <b style="font-size: 14px;">Video_Cinematic_4K.mp4</b>
      <span style="color: var(--muted); font-size: 12px;">450 MB • 05/09/2026</span>
      <a href="/download/file_1" class="btn-download">Tải Về Máy Tính</a>
    </div>
  </div>
</body>
</html>
```
