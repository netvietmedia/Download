# Báo cáo Blueprint Thiết Kế Hệ Thống Server / Backend / CMS Toàn Diện (Độ Chính Xác 100%)

## 1. Bối Cảnh & Kiến Trúc Tổng Thể

Dự án thiết kế và triển khai hệ thống Server / Backend / CMS thay thế toàn bộ hạ tầng phân tán của SHAREit, vận hành trên cổng **6030** và hỗ trợ khả năng mở rộng lên hàng chục triệu người dùng:

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                CLIENT APPLICATION LAYER                                     │
│                (Android Kotlin / iOS Swift / Desktop Client / WebShare Web)                 │
└──────────────────────────────────────────────┬──────────────────────────────────────────────┘
                                               │ (HTTPS / TLS 1.3 + WebSocket)
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                          EDGE GATEWAY & SECURITY LAYER (PORT 6030)                          │
│     • Rate Limiting: Token Bucket 60 req/min/IP • HMAC-SHA256 Request Signature Verifier   │
│     • JWT Auth Token Authenticator               • WAF & Anti-Fraud Bot Filtering           │
└──────────────────────────────────────────────┬──────────────────────────────────────────────┘
                                               │
 ┌─────────────────┬─────────────────┬─────────┴───────┬─────────────────┬──────────────────┐
 ▼                 ▼                 ▼                 ▼                 ▼                  ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Auth & Device│  │ P2P Signaling│  │ MCDS Remote  │  │ Ad Mediation │  │ Billing & IAP│  │ Gamification │
│ Management   │  │ & Chunk Relay│  │ Config Engine│  │ & Anti-Fraud │  │ Subscriptions│  │ & Coin Ledger│
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
```

---

## 2. Thiết Kế Cơ Sở Dữ Liệu Chi Tiết (Relational & OLAP Schema)

### 2.1. Cấu Trúc Bảng Dữ Liệu Quan Hệ (PostgreSQL / SQLite Core Schema)

```sql
-- 1. Bảng Quản lý Định danh Thiết bị
CREATE TABLE devices (
    id TEXT PRIMARY KEY,
    device_uuid TEXT UNIQUE NOT NULL,
    public_key TEXT,
    os_type TEXT NOT NULL, -- 'android', 'ios', 'windows', 'macos'
    os_version INTEGER NOT NULL,
    app_version TEXT NOT NULL,
    app_version_code INTEGER NOT NULL,
    device_model TEXT,
    manufacturer TEXT,
    language TEXT DEFAULT 'vi',
    country_code TEXT DEFAULT 'VN',
    gaid_idfv TEXT,
    fcm_token TEXT,
    is_banned INTEGER DEFAULT 0,
    first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Bảng Người dùng
CREATE TABLE users (
    id TEXT PRIMARY KEY,
    display_name TEXT,
    avatar_url TEXT,
    email TEXT UNIQUE,
    phone TEXT UNIQUE,
    is_vip INTEGER DEFAULT 0,
    vip_expire_at TIMESTAMP,
    coin_balance INTEGER DEFAULT 0,
    status TEXT DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Bảng Cấu hình MCDS Spaces & Promotional Banners
CREATE TABLE mcds_spaces (
    id TEXT PRIMARY KEY,
    space_id TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    is_active INTEGER DEFAULT 1,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE mcds_promotes (
    id TEXT PRIMARY KEY,
    space_id TEXT NOT NULL REFERENCES mcds_spaces(space_id),
    title TEXT,
    content_type TEXT NOT NULL, -- 'image', 'lottie', 'h5', 'native_ad'
    media_url TEXT NOT NULL,
    action_type TEXT NOT NULL, -- 'deeplink', 'external_url', 'open_tab'
    action_value TEXT NOT NULL,
    targeting_rules TEXT, -- JSON String: {"countries": ["VN", "ID"], "min_version": 100}
    frequency_capping TEXT, -- JSON String: {"max_impressions_per_day": 5, "interval_sec": 3600}
    priority INTEGER DEFAULT 1,
    is_active INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Bảng Cấu hình Ma Trận Quảng Cáo (Ad Mediation Matrix)
CREATE TABLE ad_placements (
    id TEXT PRIMARY KEY,
    placement_id TEXT UNIQUE NOT NULL,
    placement_type TEXT NOT NULL, -- 'banner', 'interstitial', 'rewarded', 'native'
    mediation_waterfall TEXT NOT NULL, -- JSON Array: [{"network": "admob", "unit_id": "...", "ecpm_floor": 3.5}, ...]
    country_overrides TEXT,
    is_active INTEGER DEFAULT 1,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Bảng Quản lý Gói Cước & Hóa Đơn Thanh Toán IAP
CREATE TABLE subscription_plans (
    id TEXT PRIMARY KEY, -- 'vip_weekly', 'vip_monthly', 'vip_yearly', 'vip_lifetime'
    title TEXT NOT NULL,
    price REAL NOT NULL,
    currency TEXT DEFAULT 'USD',
    duration_days INTEGER NOT NULL,
    google_product_id TEXT,
    apple_product_id TEXT,
    features TEXT, -- JSON Array: ["No Ads", "Max Speed 200MB/s", "Unlimited Cloud Vault"]
    is_active INTEGER DEFAULT 1
);

CREATE TABLE payment_orders (
    id TEXT PRIMARY KEY,
    order_code TEXT UNIQUE NOT NULL,
    user_id TEXT,
    device_id TEXT,
    plan_id TEXT REFERENCES subscription_plans(id),
    gateway TEXT NOT NULL, -- 'google_play', 'apple_iap', 'stripe'
    transaction_id TEXT,
    purchase_token TEXT,
    amount REAL NOT NULL,
    currency TEXT DEFAULT 'USD',
    status TEXT DEFAULT 'pending', -- 'pending', 'paid', 'failed', 'refunded'
    raw_response TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Bảng Sổ Cái Điểm Thưởng Coin (Idempotent Ledger)
CREATE TABLE coin_ledger (
    id TEXT PRIMARY KEY,
    transaction_id TEXT UNIQUE NOT NULL,
    user_id TEXT NOT NULL,
    event_type TEXT NOT NULL, -- 'daily_checkin', 'share_file', 'invite_friend', 'redeem'
    amount INTEGER NOT NULL, -- Dương: cộng coin, Âm: trừ coin
    balance_after INTEGER NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. Bảng Quản trị Viên & Nhật Ký CMS (Admin RBAC & Audit Logs)
CREATE TABLE admin_users (
    id TEXT PRIMARY KEY,
    username TEXT UNIQUE NOT NULL,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL, -- 'super_admin', 'marketing', 'support', 'auditor'
    is_active INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_audit_logs (
    id TEXT PRIMARY KEY,
    admin_id TEXT,
    action TEXT NOT NULL,
    resource TEXT NOT NULL,
    resource_id TEXT,
    old_data TEXT,
    new_data TEXT,
    ip_address TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 3. Đặc Tả Giao Diện Quản Trị CMS (Admin Dashboard Portal)

Hệ thống CMS tích hợp sẵn giao diện Web tương tác trực tiếp qua cổng **6030** tại địa chỉ: `http://localhost:6030/admin`.

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                       ENTERPRISE CMS ADMIN DASHBOARD (PORT 6030)                            │
├──────────────┬──────────────────────────────────────────────────────────────────────────────┤
│ 📊 Tổng quan │ • Real-time Online Devices: 4,820        • Relay Chunks Streamed: 1.8 TB     │
│              │ • Ad Revenue (Today): $842.50            • Active Subscriptions: 1,290       │
├──────────────┼──────────────────────────────────────────────────────────────────────────────┤
│ 📱 Thiết bị  │ • Quản lý Thiết bị: Device UUID, GAID, Quốc gia, Loại máy, Ban/Unban         │
│ & Người dùng │ • Tìm kiếm, Tra cứu lịch sử phiên hoạt động, Cấp VIP thủ công                │
├──────────────┼──────────────────────────────────────────────────────────────────────────────┤
│ 🎨 MCDS Slot │ • Visual Slot Builder: Thêm/Sửa Space ID (Home Banner, Interstitial...)      │
│ & Dynamic    │ • Cấu hình Lottie Animation URL, DeepLink điều hướng, Bộ lọc theo Quốc gia  │
├──────────────┼──────────────────────────────────────────────────────────────────────────────┤
│ 💰 Quản lý   │ • Ma trận Waterfall Quảng Cáo: AdMob, AppLovin, Mintegral, Unity Ads         │
│ Quảng Cáo    │ • Thiết lập giá sàn eCPM Floor theo từng quốc gia & định dạng                │
├──────────────┼──────────────────────────────────────────────────────────────────────────────┤
│ 💎 Gói Cước  │ • Thiết lập các gói VIP (Tuần, Tháng, Năm, Vĩnh viễn)                        │
│ & IAP Orders │ • Bảng đối soát đơn hàng Google Play / StoreKit, Doanh thu & Tỷ lệ hủy gói   │
├──────────────┼──────────────────────────────────────────────────────────────────────────────┤
│ 🎁 Gamify    │ • Thiết lập phần thưởng Điểm danh hàng ngày & Nhiệm vụ chia sẻ               │
│ & Coin Sổ Cái│ • Xem sổ cái giao dịch Coin, Kiểm tra gian lận và khóa tài khoản farm        │
├──────────────┼──────────────────────────────────────────────────────────────────────────────┤
│ 📋 Nhật Ký   │ • Báo cáo Telemetry Ladon, Thống kê tốc độ truyền file P2P                   │
│ & Audit Log  │ • Nhật ký thao tác của Admin bảo đảm tính minh bạch và an toàn               │
└──────────────┴──────────────────────────────────────────────────────────────────────────────┘
```

