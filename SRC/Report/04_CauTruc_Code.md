# Báo cáo phân tích ứng dụng SHAREit — Cấu trúc thư mục Code

## 1. Tổng quan

- Mã nguồn decompile nằm tại `out_src/sources/`, gồm **45.606 file Java**.
- Hai package gốc của chính SHAREit:
  - `com.lenovo.anyshare.*` — tầng lõi / chia sẻ / file manager (kế thừa từ thời Lenovo)
  - `com.ushareit.*` — tầng ứng dụng / business mới
- Các package khác (`com.google`, `androidx`, `com.bytedance`, …) là SDK bên thứ ba (xem báo cáo 03).
- **Mức độ obfuscate cao**: ~1/3 class là tên 1 ký tự (`a.java`, `b.java`…), đặc biệt trong `com.lenovo.anyshare` (inner class bị đổi tên `C1140a`…) và toàn bộ package `p002si.*` (obfuscator tạo ra).
- Packer `Tierahs` nhúng thêm package `spg.erahsyna.ovonel.moc.*` (đảo ngược = `com.…`), chứa dịch vụ bảo vệ.

## 2. Sơ đồ cây (đơn vị: số class đếm được trong DEX)

```
com.lenovo.anyshare.gps (package gốc)
├── com.lenovo.anyshare.*            (~2.000 class) — tầng lõi
├── com.ushareit.*                   (~6.000 class) — tầng business
├── com.adexchange.*                 ad-exchange Midas
├── com.anythink.*                   TopOn mediation
├── com.bytedance.sdk.*              Pangle + ByteDance
├── com.google.* / com.firebase.*    Google SDK
├── androidx.*                       AndroidX
├── p002si.*                         code obfuscated (đủ mọi module)
└── spg.erahsyna.ovonel.moc.*        Tierahs protector
```

## 3. Chức năng từng thư mục — `com.ushareit.*` (tầng business)

| Package | Số class | Chức năng / nhiệm vụ |
|---|---|---|
| `com.ushareit.filemanager` | 798 | File manager: duyệt file local, ảnh/video/nhạc, zip, media scan |
| `com.ushareit.muslim` | 522 | Bộ công cụ Muslim Daily: Quran, prayer time, tasbih, allah name, hướng Qibla |
| `com.ushareit.ads` | 505 | Toàn bộ quảng cáo: baseadapter, interstitial, rewarded, mraid, reserve, cpi, sharemob |
| `com.ushareit.cleanit` | 505 | Dọn rác: analyze, diskclean, photo compress, residual, sdk clean |
| `com.ushareit.downloader` | 446 | Download video từ web: web main, video, history |
| `com.ushareit.base` | 339 | Hạ tầng chung: dialog, core net, NetworkStatus, entity |
| `com.ushareit.widget` | 266 | Widget tùy biến |
| `com.ushareit.siplayer` | 190 | SI Player (video player nội bộ) |
| `com.ushareit.mcds` | 164 | MCDS (remote config / nội dung động) |
| `com.ushareit.musicplayer` | 162 | Music player + lock screen + notification |
| `com.ushareit.nft` | 133 | NFT discovery |
| `com.ushareit.cast` | 126 | Cast/DLNA: main, music, photo, video |
| `com.ushareit.component` | 121 | Component: ads, notification, resdownload |
| `com.ushareit.applock` | 114 | Khóa ứng dụng (fingerprint, security) |
| `com.ushareit.bst` | 112 | BST (power/speed/clean tools): speed, power, game |
| `com.ushareit.clone` | 108 | Clone app |
| `com.ushareit.content` | 105 | Content/webshare |
| `com.ushareit.entity` | 104 | Entity dữ liệu |
| `com.ushareit.launch` | 104 | Khởi động app |
| `com.ushareit.hybrid` | 84 | Hybrid WebView UI + service |
| `com.ushareit.nearby` | 79 | Chia sẻ nearby: discover, ble push |
| `com.ushareit.coin` | 60 | Coin (điểm thưởng): rmi, ui, activating |
| `com.ushareit.upgrade` | 58 | Cập nhật app |
| `com.ushareit.tools` | 57 | Tools core/services |
| `com.ushareit.guide` | 51 | Guide/onboarding |
| `com.ushareit.butler` | 47 | Butler (trợ lý) |
| `com.ushareit.subscription` | 47 | Đăng ký trả phí |
| `com.ushareit.minivideo` | 46 | Video ngắn: trending, push |
| `com.ushareit.accountsetting` | 45 | Cài đặt tài khoản |
| `com.ushareit.net` | 43 | Network: download, http, rmi framework, rmframework |
| `com.ushareit.medusa` | 41 | Medusa (thử nghiệm A/B / push thử) |
| `com.ushareit.photo` | 40 | Photo online |
| `com.ushareit.ccm` | 38 | CCM (cloud content?) |
| `com.ushareit.theme` | 37 | Theme |
| `com.ushareit.upload` | 32 | Upload |
| `com.ushareit.feedback` | 30 | Feedback |
| `com.ushareit.product` | 30 | Product |
| `com.ushareit.videoplayer` | 30 | Video player |
| `com.ushareit.sdkshare` | 28 | SDK share (game) |
| `com.ushareit.rmi` | 18 | RMI methods (API nghiệp vụ) |
| `com.ushareit.metis` | 15 | Metis DB |
| `com.ushareit.security` | 14 | Security |
| `com.ushareit.beyla` | 11 | Beyla (ad targeting) |
| `com.ushareit.ulog` | 11 | ULog analytics |
| `com.ushareit.push` / `oppush` | 17 | Push |
| `com.ushareit.router` | 4 | Router URI |
| `com.ushareit.app` | 8 | BeylaIdProvider, application |
| `com.ushareit.login` (+logincore) | — | Đăng nhập, MultiUserInfo, WeChat/Facebook |

## 4. Chức năng từng thư mục — `com.lenovo.anyshare.*` (tầng lõi)

| Package | Số class | Chức năng / nhiệm vụ |
|---|---|---|
| `lenovo.share` | 400 | Chia sẻ P2P: hotspot, wifi direct, send/receive, transfer |
| `lenovo.main` | 340 | Main UI: MainActivity, home, transfer zone |
| `lenovo.content` | 254 | Content: webshare, web, sharezone |
| `lenovo.widget` | 170 | Widget (TransR widget, dialog, view) |
| `lenovo.safebox` | 164 | Két khóa file (fingerprint verify) |
| `lenovo.sharezone` | 78 | Share zone |
| `lenovo.help` | 77 | Trợ giúp |
| `lenovo.pc` | 65 | Kết nối PC |
| `lenovo.flash` | 61 | Flash (hẹn giờ tắt máy?) |
| `lenovo.activity` | 53 | Activity chung (FileBrowser, ExternalShare, QRCodeScan…) |
| `lenovo.revision` | 48 | Revision |
| `lenovo.setting` | 39 | Cài đặt |
| `lenovo.bizentertainment` | 37 | Giải trí |
| `lenovo.download` | 33 | Download |
| `lenovo.notification` | 23 | Notification |
| `lenovo.qrcode` | 18 | Quét QR |
| `lenovo.base` | 15 | Base |
| `lenovo.service` | 15 | Service |
| `lenovo.explorer` | 8 | Explorer |
| `lenovo.uat` | 6 | UAT |
| `lenovo.stats` | 5 | Stats |

## 5. Cấu trúc assets (`assets/`)

- **HTML/JS WebView**: `ad-viewer/` (29), `webshare_jio/` (30+16), `help/` (33), `help_center/` (15), `mraid3.js`, `android_bridge.js`, `omsdk-v1.js`, `dtb-m.js` (Amazon DTB)
- **Animation/lottie**: hàng chục thư mục `data.json` (flash, robot, coin, hotspot, clean…)
- **Config nội bộ**: `builddatas.json`, `rule.json`, `home_resource.xml`, `type_resource.xml`, `localStrategy/*.json` (placement quảng cáo Midas, base64), `SpaceClean.db`
- **Nội dung Muslim**: `quran`, `adhan`, `prayer`, `athkar`, `allahname`, `tasbih`, `adhanclock`
- **Bản đồ mime**: `tt_mime_type.pro` (ByteDance)

## 6. Kiến trúc chạy

1. `aashareit.AnyShareApp` (Application) khởi tạo toàn bộ: MMKV, network, push, ad SDK, Tierahs protector.
2. Launch flow: `com.ushareit.launch` → `FlashActivity` → `MainActivity` (exported, có scheme filter).
3. Network: `MobileClientManager` (RMI) — mọi API qua `registerAPI(interface, impl)`; host do remote config.
4. Quảng cáo: `com.ushareit.ads` + Midas `com.adexchange` + mediation AnyThink/ironSource, render qua WebView MRAID.
5. Truyền file: `lenovo.share` (Wi-Fi Direct/hotspot) + `relay.ushareit.com/relayserver` fallback.
