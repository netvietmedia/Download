# Báo cáo Phân tích API Endpoints, Data Models & Khả Năng Tái Sử Dụng

## 1. Tổng Quan Hệ Thống API Trong Ứng Dụng

Qua giải mã 13 file DEX và phân tích chuỗi mạng, hệ thống ứng dụng SHAREit tích hợp **375 host** khác nhau với hơn **161 endpoints nội bộ**. Hệ thống phân phối theo 2 tầng chính:
1. **Tầng RMI Framework (`com.ushareit.net.rmframework`)**: Sử dụng cơ chế RPC qua HTTP(S) POST, đóng gói tham số `params` và chữ ký `signUser`, điều phối bởi `MobileClientManager`.
2. **Tầng RESTful / Web API**: Các dịch vụ Ads, Paywall, Ladon Analytics, Dynamic MCDS và Cloud Relay.

---

## 2. Bảng Danh Mục API Endpoints & Đánh Giá Khả Năng Tái Sử Dụng

| STT | Phân hệ | Endpoint | Method | Chức năng / Nhiệm vụ | Sử dụng sẵn được không? | Ghi chú & Giải pháp Backend mới |
|---|---|---|---|---|---|---|
| **1** | Auth | `/api/v1/auth/device/register` | POST | Đăng ký thiết bị mới, cấp phát `device_uuid` và session token | **Cần Backend mới** | Thay thế `beyla_id` cũ bằng ECDSA Device Fingerprinting |
| **2** | Auth | `/api/v1/auth/signin` | POST | Đăng nhập tài khoản qua Google, Apple, Phone OTP | **Cần Backend mới** | Tích hợp OAuth 2.0 / Firebase Auth |
| **3** | Auth | `/api/v1/auth/token/refresh` | POST | Làm mới JWT Access Token | **Cần Backend mới** | JWT thời hạn 15m + Refresh Token 30d |
| **4** | Auth | `/api/v1/auth/user/profile` | GET/POST | Lấy và cập nhật hồ sơ người dùng | **Cần Backend mới** | Lưu trữ hồ sơ trên PostgreSQL/SQLite |
| **5** | Relay | `/api/v1/relay/session/create` | POST | Tạo phiên truyền file qua mây (sinh mã PIN 6 số & QR) | **Cần Backend mới** | Cổng 6030 Relay Manager |
| **6** | Relay | `/api/v1/relay/session/join` | POST | Thiết bị nhận tham gia phiên truyền file | **Cần Backend mới** | Ghép đôi thiết bị theo session ID |
| **7** | Relay | `/api/v1/relay/chunk/upload` | POST | Đẩy luồng nhị phân chunk file (1MB - 4MB/chunk) | **Cần Backend mới** | Xử lý đa luồng & dọn dẹp sau khi truyền |
| **8** | Relay | `/api/v1/relay/chunk/download` | GET | Kéo luồng chunk file kèm HTTP Range resume | **Cần Backend mới** | Hỗ trợ resume khi mất kết nối |
| **9** | Signaling | `WS /api/v1/signaling/webrtc` | WS | Trao đổi SDP Offer/Answer và ICE candidates P2P | **Cần Backend mới** | WebSocket server cổng 6030 |
| **10** | MCDS | `/api/v1/mcds/fetch` | POST | Lấy danh sách không gian banner, Lottie dynamic | **Cần Backend mới** | Thay thế `com.ushareit.mcds` server |
| **11** | Config | `/api/v1/config/app/init` | POST | Khởi tạo cấu hình ứng dụng, Kill-switch, Flags | **Cần Backend mới** | Server-signed JSON config |
| **12** | Upgrade | `/api/v1/upgrade/check` | POST | Kiểm tra phiên bản cập nhật APK / diff patch | **Cần Backend mới** | Quản lý update force/soft từ CMS |
| **13** | Ads | `/api/v1/ads/strategy/get` | POST | Trả về ma trận Waterfall Ad Mediation theo quốc gia | **Cần Backend mới** | Thay thế Midas / rqmob internal exchange |
| **14** | Ads | `/api/v1/ads/track/impression` | POST | Ghi nhận lượt hiển thị quảng cáo hợp lệ | **Cần Backend mới** | Chống fraud & click spam |
| **15** | Ads | `/api/v1/ads/track/click` | POST | Ghi nhận click quảng cáo & redirect | **Cần Backend mới** | Lưu vết Attribution |
| **16** | Pay | `/api/v1/pay/order/create` | POST | Tạo đơn hàng đăng ký gói VIP | **Cần Backend mới** | Xử lý giao dịch IAP |
| **17** | Pay | `/api/v1/pay/order/verify` | POST | Xác thực biên lai mua hàng Google Play / StoreKit | **Cần Backend mới** | Tích hợp Google Play Developer API v3 |
| **18** | Pay | `/api/v1/pay/subscription/status` | GET | Kiểm tra trạng thái VIP người dùng hiện tại | **Cần Backend mới** | Cache trạng thái VIP trên Redis/Memory |
| **19** | Pay | `/api/v1/pay/webhook/google` | POST | Webhook nhận thông báo Google Cloud Pub/Sub | **Cần Backend mới** | Xử lý tự động gia hạn, hủy gói |
| **20** | Coin | `/api/v1/coin/balance` | GET | Lấy số dư Coin và lịch sử biến động | **Cần Backend mới** | Sổ cái Coin bất biến (Idempotent Ledger) |
| **21** | Coin | `/api/v1/coin/task/list` | POST | Lấy danh mục nhiệm vụ nhận thưởng | **Cần Backend mới** | Quản trị từ CMS |
| **22** | Coin | `/api/v1/coin/task/claim` | POST | Nhận thưởng sau khi hoàn thành nhiệm vụ | **Cần Backend mới** | Kiểm tra điều kiện & chống farm coin |
| **23** | Coin | `/api/v1/coin/checkin` | POST | Điểm danh hàng ngày theo chuỗi liên tục | **Cần Backend mới** | Thưởng chuỗi 7 ngày |
| **24** | Coin | `/api/v1/coin/redeem` | POST | Đổi điểm lấy dung lượng Cloud, Voucher, VIP pass | **Cần Backend mới** | Trừ coin & cấp mã quà tặng |
| **25** | Telemetry | `/api/v1/telemetry/events/batch` | POST | Tiếp nhận gói sự kiện phân tích người dùng | **Cần Backend mới** | Thay thế `ladon.dalyfeds.com` |
| **26** | Telemetry | `/api/v1/telemetry/crash/report` | POST | Tiếp nhận crash stack trace & thiết bị sự cố | **Cần Backend mới** | Lưu trữ phân tích lỗi hệ thống |
| **27** | Push | `/api/v1/push/token/sync` | POST | Đồng bộ FCM / APNs device token | **Cần Backend mới** | Phân nhóm thiết bị theo múi giờ |
| **28** | Utility | `/api/v1/utility/muslim/prayer-times` | GET | Tính toán giờ cầu nguyện & hướng Qibla | **Có thể gọi sẵn / Offline** | Thuật toán offline tích hợp sẵn trong App |
| **29** | CDN | `/games/{id}/_i/*.webp` | GET | Tải assets hình ảnh mini game | **Có thể gọi sẵn** | CDN công khai hoặc lưu trữ local |
| **30** | WebView | `https://active.wshareit.com/*` | GET | Các trang H5: tài khoản, nhiệm vụ, quy tắc | **Có thể chuyển về CMS** | Tích hợp trực tiếp vào CMS Admin UI |

---

## 3. Đặc Tả Chi Tiết Data Models (Request & Response Payloads)

### 3.1. Phân hệ Auth & Device Management

#### Endpoint: `POST /api/v1/auth/device/register`
* **Request Header**: `Content-Type: application/json`, `X-App-Signature: <HMAC>`, `X-App-Timestamp: <timestamp>`
* **Request Body**:
```json
{
  "device_fingerprint": {
    "os_type": "android",
    "os_version": 34,
    "app_version": "6.53.38_UD",
    "app_version_code": 4565338,
    "device_model": "Pixel 7 Pro",
    "manufacturer": "Google",
    "language": "vi",
    "country_code": "VN",
    "gaid": "38400000-8cf0-11bd-b23e-10b96e40000d",
    "fcm_token": "fcm_token_sample_string..."
  }
}
```
* **Response Body (200 OK)**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "device_uuid": "dev_8f2910c2_4a9b_412e_a91c_89201f92e811",
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "ref_91a0b3c8...",
    "expires_in": 900,
    "is_new_device": true,
    "server_time": 1725510000
  }
}
```

---

### 3.2. Phân hệ Remote Config & MCDS Engine

#### Endpoint: `POST /api/v1/mcds/fetch`
* **Request Body**:
```json
{
  "device_uuid": "dev_8f2910c2_4a9b_412e_a91c_89201f92e811",
  "space_ids": ["home_top_banner", "trans_complete_interstitial", "tools_muslim_entry"],
  "context": {
    "country": "VN",
    "app_version_code": 4565338,
    "network_type": "WIFI",
    "is_vip": false
  }
}
```
* **Response Body (200 OK)**:
```json
{
  "code": 0,
  "data": {
    "spaces": [
      {
        "space_id": "home_top_banner",
        "prom_units": [
          {
            "id": "prom_101",
            "title": "Nâng cấp VIP - Truyền file tốc độ tối đa không quảng cáo",
            "content_type": "lottie",
            "media_url": "https://cdn.internal/lottie/vip_badge.json",
            "action_type": "deeplink",
            "action_value": "shareit://subscription/paywall",
            "priority": 10,
            "frequency_capping": {
              "max_impressions_per_day": 5,
              "interval_sec": 1800
            }
          }
        ]
      }
    ]
  }
}
```

---

### 3.3. Phân hệ Waterfall Ad Mediation Matrix

#### Endpoint: `POST /api/v1/ads/strategy/get`
* **Request Body**:
```json
{
  "device_uuid": "dev_8f2910c2_4a9b_412e_a91c_89201f92e811",
  "placement_ids": ["trans_complete_interstitial", "main_bottom_banner"],
  "country_code": "VN"
}
```
* **Response Body (200 OK)**:
```json
{
  "code": 0,
  "data": {
    "placements": {
      "trans_complete_interstitial": {
        "format": "interstitial",
        "waterfall": [
          {
            "network": "admob",
            "unit_id": "ca-app-pub-3940256099942544/1033173712",
            "ecpm_floor": 3.50,
            "timeout_ms": 3000
          },
          {
            "network": "applovin",
            "unit_id": "applovin_inter_unit_01",
            "ecpm_floor": 2.80,
            "timeout_ms": 2500
          },
          {
            "network": "mintegral",
            "unit_id": "mintegral_inter_unit_01",
            "ecpm_floor": 2.00,
            "timeout_ms": 2000
          }
        ],
        "auto_refresh_sec": 0
      }
    }
  }
}
```

---

### 3.4. Phân hệ Gamification & Sổ Cái Coin

#### Endpoint: `POST /api/v1/coin/checkin`
* **Request Body**:
```json
{
  "device_uuid": "dev_8f2910c2_4a9b_412e_a91c_89201f92e811",
  "request_nonce": "non_712891290312"
}
```
* **Response Body (200 OK)**:
```json
{
  "code": 0,
  "message": "Điểm danh thành công!",
  "data": {
    "day_streak": 3,
    "coins_awarded": 150,
    "total_balance": 1450,
    "transaction_id": "tx_coin_20260905_91823",
    "next_reward_day": 4,
    "next_coins": 200
  }
}
```

---

### 3.5. Phân hệ Telemetry Ladon Batch Ingestion

#### Endpoint: `POST /api/v1/telemetry/events/batch`
* **Request Body**:
```json
{
  "device_uuid": "dev_8f2910c2_4a9b_412e_a91c_89201f92e811",
  "batch_id": "batch_910238_001",
  "sent_at": 1725510030,
  "events": [
    {
      "event_name": "transfer_file_finish",
      "timestamp": 1725510015,
      "properties": {
        "file_type": "video/mp4",
        "file_size_bytes": 104857600,
        "transfer_mode": "wifi_direct",
        "duration_ms": 4200,
        "speed_mbps": 199.7,
        "status": "success"
      }
    }
  ]
}
```
* **Response Body (200 OK)**:
```json
{
  "code": 0,
  "accepted_events": 1,
  "server_received_at": 1725510031
}
```

