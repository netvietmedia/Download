# Báo Cáo & Danh Mục Cấu Hình Khóa Quảng Cáo (Ad Placement Keys & Manifest)

> **Mục đích tài liệu:** Bản kê khai tập trung toàn bộ các Khóa Ứng Dụng (App ID), Vị Trí Quảng Cáo (Ad Unit IDs), Định dạng (Format), Giá sàn (eCPM Floor) và Mạng trung gian (Mediation Networks). Mọi khóa hiện đang dùng ID Test chuẩn thức của Google/AppLovin và được đóng gói thành các file cấu hình độc lập để bạn có thể **thay thế bằng Khóa Sản Xuất (Production Keys) của bạn sau này một cách nhanh chóng chỉ trong 1 bước**.

---

## 1. Bảng Kê Khai Toàn Bộ Vị Trí & Khóa Quảng Cáo

| STT | Tên Vị Trí (Placement ID) | Định Dạng (Format) | Màn Hình Xuất Hiện | Mạng Quảng Cáo Mặc Định | Khóa Test Hiện Tại (Test ID) | Vị Trí Thay Khóa Thật Sau Này |
|---|---|---|---|---|---|---|
| **1** | **`admob_app_id`** | App Credential | Toàn bộ ứng dụng | Google AdMob | `ca-app-pub-3940256099942544~3347511713` | `AndroidManifest.xml` & `ads_keys.json` |
| **2** | **`main_bottom_banner`** | Banner (320x50 / Adaptive) | Dưới đáy Trang Chủ (`MainActivity`) | AdMob / AppLovin | `ca-app-pub-3940256099942544/6300978111` | `ads_keys_config.json` |
| **3** | **`trans_complete_interstitial`** | Toàn màn hình (Interstitial) | Sau khi hoàn thành phiên gửi/nhận file P2P | AdMob / MAX / Mintegral | `ca-app-pub-3940256099942544/1033173712` | `ads_keys_config.json` |
| **4** | **`clean_finish_interstitial`** | Toàn màn hình (Interstitial) | Sau khi bấm "Dọn dẹp ngay" trong Cleanit | AdMob / ironSource | `ca-app-pub-3940256099942544/1033173712` | `ads_keys_config.json` |
| **5** | **`coin_rewarded_video`** | Video Thưởng (Rewarded) | Bấm "Xem Video nhận 200 Coin" | AdMob / Unity Ads | `ca-app-pub-3940256099942544/5224354917` | `ads_keys_config.json` |
| **6** | **`app_open_splash`** | Mở ứng dụng (App Open) | Khi mở lại app từ Background sau 4 tiếng | Google AdMob | `ca-app-pub-3940256099942544/9257395921` | `ads_keys_config.json` |
| **7** | **`files_native_advanced`** | Quảng cáo Tự Nhiên (Native) | Lồng giữa danh sách tệp tin & Video | AdMob / Pangle | `ca-app-pub-3940256099942544/2247696110` | `ads_keys_config.json` |
| **8** | **`applovin_sdk_key`** | SDK Credential | Mediation Layer | AppLovin MAX | `YOUR_APPLOVIN_SDK_KEY_PLACEHOLDER` | `AndroidManifest.xml` |
| **9** | **`mintegral_app_id`** | Network ID | Mediation Layer | Mintegral | `mintegral_app_id_placeholder` | `ads_keys.json` |
| **10**| **`unity_game_id`** | Network ID | Mini Game Ads | Unity Ads | `unity_game_id_placeholder` | `ads_keys.json` |

---

## 2. File Cấu Hình Khóa Độc Lập Cho Android (`assets/ads_keys_config.json`)

Đường dẫn tệp trong mã nguồn Android: [`Convert/Android/app/src/main/assets/ads_keys_config.json`](file:///f:/APP/Share%20File/Share%20it/Convert/Android/app/src/main/assets/ads_keys_config.json)

```json
{
  "version": "1.0.0",
  "updated_at": "2026-09-05",
  "description": "Cấu hình Khóa Quảng Cáo SHAREit - Thay thế các giá trị placeholder bằng ID thật khi phát hành",
  "credentials": {
    "admob_app_id": "ca-app-pub-3940256099942544~3347511713",
    "applovin_sdk_key": "YOUR_APPLOVIN_SDK_KEY_HERE",
    "mintegral_app_id": "YOUR_MINTEGRAL_APP_ID_HERE",
    "unity_game_id": "YOUR_UNITY_GAME_ID_HERE"
  },
  "placements": {
    "main_bottom_banner": {
      "format": "BANNER",
      "network": "admob",
      "unit_id": "ca-app-pub-3940256099942544/6300978111",
      "auto_refresh_sec": 30
    },
    "trans_complete_interstitial": {
      "format": "INTERSTITIAL",
      "network": "admob",
      "unit_id": "ca-app-pub-3940256099942544/1033173712",
      "waterfall_fallbacks": [
        { "network": "applovin", "unit_id": "applovin_inter_fallback_id", "ecpm_floor": 2.50 },
        { "network": "mintegral", "unit_id": "mintegral_inter_fallback_id", "ecpm_floor": 1.80 }
      ]
    },
    "clean_finish_interstitial": {
      "format": "INTERSTITIAL",
      "network": "admob",
      "unit_id": "ca-app-pub-3940256099942544/1033173712"
    },
    "coin_rewarded_video": {
      "format": "REWARDED",
      "network": "admob",
      "unit_id": "ca-app-pub-3940256099942544/5224354917",
      "reward_coins": 200
    },
    "app_open_splash": {
      "format": "APP_OPEN",
      "network": "admob",
      "unit_id": "ca-app-pub-3940256099942544/9257395921"
    },
    "files_native_advanced": {
      "format": "NATIVE",
      "network": "admob",
      "unit_id": "ca-app-pub-3940256099942544/2247696110"
    }
  }
}
```

---

## 3. Quản Lý & Cập Nhật Khóa Từ Xa Qua Backend CMS (Không Cần Build Lại App)

Ngoài việc lưu trữ trong file `assets/ads_keys_config.json`, bạn có thể **thay đổi toàn bộ Khóa Quảng cáo thời gian thực** ngay trên giao diện Web CMS tại:
👉 **[http://localhost:6030/admin](http://localhost:6030/admin)** -> Tab **"Ma Trận Quảng Cáo" (Ad Mediation)**.

Khi bạn cập nhật Unit ID hoặc giá sàn eCPM trên CMS, máy chủ sẽ tự động đồng bộ xuống toàn bộ thiết bị đang chạy thông qua API:
`POST http://localhost:6030/api/v1/ads/strategy/get` mà không bắt buộc người dùng phải cập nhật bản APK mới!

