# Báo cáo phân tích ứng dụng SHAREit — Danh sách API Endpoint

## 1. Tổng quan

- **375 host** khác nhau được tham chiếu trong DEX (bao gồm host của ad network, CDN, third-party SDK).
- **161 endpoint thuộc hệ sinh thái SHAREit** (`wshareit.com`, `dalyfeds.com`, `hellay.net`, `rqmob.com`, `shareitgames.com`, `ushareit.com`, …).
- Tầng network chính: **RMI framework** (`com.ushareit.net.rmframework`) — mọi API nghiệp vụ đi qua `MobileClientManager` với cơ chế ký tham số (`signUser`), host được cấu hình động qua remote config (`configHosts(http, https, pre, test, backup)`).
- Có cơ chế `forceHttps(boolean)` — chứng tỏ app **từng chạy HTTP thuần**, mới chuyển dần sang HTTPS.

## 2. Nhóm API theo chức năng

### 2.1 API nghiệp vụ chính (RMI — qua `api*.wshareit.com` / `api.dalyfeds.com`)

| Host | Endpoint | Chức năng / nhiệm vụ |
|---|---|---|
| `api2.wshareit.com` | `GET /config/group/deeplink` | Cấu hình nhóm deeplink (điều hướng sâu) |
| `api2.wshareit.com` | `GET /ping/` | Ping / health check |
| `dev-api.wshareit.com` | `POST /ladon/notify2/report` | Báo cáo sự kiện (Ladon = analytics nội bộ) |
| `dev-api.wshareit.com` | `POST /ladon/ongoing/update` | Cập nhật trạng thái ongoing |
| `dev-api.wshareit.com` | `POST /ladon/option/update` | Cập nhật tùy chọn |
| `ladon.dalyfeds.com` | `GET/POST /ladon/notify2/report` | Báo cáo analytics (prod) |
| `ladon.dalyfeds.com` | `GET/POST /ladon/ongoing/update` | Cập nhật ongoing |
| `ladon.dalyfeds.com` | `GET/POST /ladon/option/update` | Cập nhật tùy chọn |
| `feedback-api.dalyfeds.com` | `GET/POST` | API phản hồi người dùng |
| `activity-api.wshareit.com` | `GET/POST` | API sự kiện / activity (task, nhiệm vụ) |
| `s.dalyfeds.com` | `GET /?q=%s` | Search proxy |
| `s.wshareit.com` | `GET /watch?cache=open` | Xem nội dung |

### 2.2 API thanh toán / đăng ký (subscription)

| Host | Endpoint | Chức năng |
|---|---|---|
| `pay.wshareit.com` | `POST /v3/pay/order/record/save` | Lưu record đơn hàng |
| `pre-pay.wshareit.com` | `POST /v3/pay/order/record/save` | Môi trường pre-prod |
| `test-pay.wshareit.com` | `POST /v3/pay/order/record/save` | Môi trường test |
| `dev-pay.wshareit.com` | `POST /v3/pay/order/record/save` | Môi trường dev |
| `cdn.ushareit.com` | `GET /shareit/w/help/payment/index.html` | Trang trợ giúp thanh toán |
| `inw.ushareit.com` | `GET /test/payment/index.html` | Trang payment test |
| `subscription.adjust.com` | — | Theo dõi subscription qua Adjust |

### 2.3 API quảng cáo (ad network nội bộ — Midas)

Hệ ad-exchange nội bộ của SHAREit (Midas / rqmob / hellay / lacunads):

| Host | Endpoint | Chức năng |
|---|---|---|
| `api.rqmob.com` | `GET /shareit/get_ads?version=1` | Lấy quảng cáo (chính) |
| `api.rqmob.com` | `POST /shareit/bid?version=1` | Đấu giá (bidding) |
| `api.rqmob.com` | `GET /shareit/ads/` | Danh sách quảng cáo |
| `api.rqmob.com` | `GET /config?version=1` | Cấu hình SDK |
| `api.rqmob.com` | `GET /get_reservation_info` | Thông tin đặt trước |
| `api.hellay.net` | `GET /v2/get_reservation_info` | Reservation v2 |
| `api.hellay.net` | `POST /shareit/force_get_ad` | Ép lấy quảng cáo |
| `midas-api.hellay.net` | `GET /feedback/list` | Danh sách feedback quảng cáo |
| `adconf.rqmob.com` | `GET /shareit/get_ads?version=1` | Config quảng cáo |
| `adcs.rqmob.com` | `GET /ping/beyla` | Ping hệ thống Beyla (ad targeting) |
| `ap-adcs.rqmob.com` | `POST /collect` | Thu thập dữ liệu click/install |
| `ap-adcs.rqmob.com` | `GET /ping/cpi` | Ping CPI |
| `appclick.rqmob.com` | `POST /client/ci` | Click install attribution |
| `distribution.rqmob.com` | `POST /v1/prepare` | Chuẩn bị phân phối |
| `plutus.lacunads.com` | `POST /v1/get_ads` | Ad server Plutus |
| `plutus-track.lacunads.com` | `POST /event` | Tracking sự kiện |
| `2118c602-ads-access…elb.amazonaws.com` | `POST /shareit/force_get_ad` | Ad access (AWS ELB) |
| `pb.dalyfeds.com` | `POST /action/ci` | Click/install |
| `gaea.hellay.net` | `POST /p/a` | App-list report |
| `zj-api.dalyfeds.com` | — | API "component" (zj = 组件) |

### 2.4 API relay / chia sẻ P2P

| Host | Endpoint | Chức năng |
|---|---|---|
| `relay.ushareit.com` | `GET/POST /relayserver` | Relay server cho truyền file khi không dùng Wi-Fi Direct |
| `test.cmd.hermes.wshareit.com` | `GET/POST /relayserver` | Relay test |
| `pc.ushareit.com` | `GET /webshare` | Chia sẻ qua PC/web |

### 2.5 API game (shareitgames)

| Host | Endpoint | Chức năng |
|---|---|---|
| `api-v2.shareitgames.com` | — | API game v2 |
| `huosu.shareitgames.com` | `GET /index.html` | Cổng game H5 |
| `igame-cdn.wshareit.com` | `GET /games/{game}/_i/*.webp` | CDN ảnh game |

### 2.6 H5 / WebView (active.wshareit.com)

| Endpoint | Chức năng |
|---|---|
| `GET /account/index.html` | Trang tài khoản |
| `GET /2020/addgroup/index.html` | Tạo nhóm chia sẻ |
| `GET /2020/task2/index.html` | Nhiệm vụ (task) |
| `GET /mvp/task/index.html` | Nhiệm vụ MVP |
| `GET /mvp/farm/index.html` | Mini-game farm |
| `GET /client/muslimdaily/index.html` | Muslim daily |
| `GET /client/tools/index.html` | Tools |
| `GET /news-neitou/index.html` | Tin tức |
| `GET /mvp_sleep/index.html` | Màn hình sleep |

### 2.7 CDN nội dung

| Host | Chức năng |
|---|---|
| `cdn.ushareit.com` / `cdn.wshareit.com` | Ảnh, clean data, icon |
| `rs.wshareit.com` | Ảnh remote config (`/wnik/tmWc/…`) |
| `rs.hpyfeds.com` | Ảnh quảng cáo game |
| `igame-cdn.wshareit.com` | CDN game |

### 2.8 Endpoint nội bộ / test còn sót (rủi ro)

- `http://10.0.2.2:8888/logan-web/logan/upload.json` — Logan (log của Meituan) gửi log lên emulator loopback — chỉ nên có ở build dev, **còn sót trong build production**.
- Hàng loạt host `dev-`, `test-`, `pre-`, `alpha-` (`dev-api.wshareit.com`, `test-api.wshareit.com`, `alpha-api.wshareit.com`, `pre-zj-api.wshareit.com`, `test.midas-*.sg1.api`…) — dấu hiệu cấu hình môi trường nhiều tầng được nhúng nguyên trong APK.

## 3. Endpoint third-party quan trọng (attribution / tracking)

| Host | Vai trò |
|---|---|
| `app.adjust.com` (+ 8 TLD vùng) | Attribution/analytics Adjust |
| `app-measurement.com` | Firebase Analytics |
| `appmetrica.io` | Yandex AppMetrica |
| `api.beta/prod.cm.publishers.advertising.a2z.com` | Amazon APS logging |
| `c.amazon-adsystem.com` | Amazon ads |
| `log.byteoversea.com`, `log-mva.isnssdk.com`, `log.sgsnssdk.com` | ByteDance log |
| `sgfp.tongdun.net`, `bugly.tongdun.net` | Tongdun (risk control) |

## 4. Cách SHAREit bảo vệ API

1. **Ký tham số**: `signUser(map)` — tham số yêu cầu được ký (thuật toán trong `a.signUser`, `mAppParamsSigner`).
2. **Beyla ID**: mọi request cần `beyla_id` (định danh thiết bị thống nhất).
3. **White list hàm**: `mFunWhiteList` — chỉ hàm được đăng ký mới gọi được qua RMI.
4. **forceHttps**: cờ toàn cục chuyển HTTP → HTTPS.
5. **AccessBalanceIntercepter / APIIntercepter**: kiểm soát tần suất gọi API.
