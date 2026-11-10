# Báo cáo Danh Sách External Domains, Địa Chỉ IP, Cổng & Giao Thức

## 1. Tổng Quan Kết Nối Mạng Ngoài Ứng Dụng

Ứng dụng SHAREit thực hiện giao tiếp với **375 host** khác nhau phục vụ các mục đích: Truyền file P2P/Relay, Quảng cáo, Telemetry Analytics, Phân phối nội dung CDN và Dịch vụ nền tảng bên thứ ba.

---

## 2. Bảng Tra Cứu Toàn Bộ Domains, Địa Chỉ IP & Cổng Dịch Vụ

### 2.1. Nhóm Dịch Vụ Máy Chủ Cốt Lõi & Relay (Core Services & Relay)

| Tên Domain / Hostname | Dải Địa Chỉ IP Tham Chiếu | Cổng Mặc Định | Giao Thức | Vai Trò & Chức Năng |
|---|---|---|---|---|
| `api2.wshareit.com` | `13.250.x.x` (AWS Singapore) | 443 / 80 | HTTPS / HTTP | Cấu hình nhóm deeplink, ping health check hệ thống |
| `api.dalyfeds.com` | `52.74.x.x` (AWS APAC) | 443 | HTTPS | Máy chủ RMI Core API nghiệp vụ |
| `relay.ushareit.com` | `54.255.x.x` (AWS Cloud Relay) | 443, 8080, 9001 | HTTPS / TCP / WSS | Máy chủ chuyển tiếp dữ liệu (Relay Server) khi P2P thất bại |
| `test.cmd.hermes.wshareit.com` | `18.138.x.x` | 443, 8080 | HTTPS / TCP | Relay Server môi trường thử nghiệm |
| `pc.ushareit.com` | `13.213.x.x` (Cloudflare CDN) | 443 / 80 | HTTPS / HTTP | Cổng thông tin WebShare cho trình duyệt máy tính |
| `feedback-api.dalyfeds.com` | `54.169.x.x` | 443 | HTTPS | Tiếp nhận phản hồi và ảnh chụp màn hình lỗi |
| `activity-api.wshareit.com` | `13.229.x.x` | 443 | HTTPS | Quản lý sự kiện, nhiệm vụ và chiến dịch |
| **Backend Mới (Convert/Backend)** | **`127.0.0.1` / `0.0.0.0`** | **6030** | **HTTP / WSS** | **Máy chủ Backend & CMS quản trị tập trung** |

---

### 2.2. Nhóm Máy Chủ Quảng Cáo Midas & Ad Networks (Advertising & Mediation)

| Tên Domain / Hostname | Nhà Cung Cấp / Vùng IP | Cổng | Giao Thức | Vai Trò & Chức Năng |
|---|---|---|---|---|
| `api.rqmob.com` | Midas Ad Exchange (AWS Global) | 443 | HTTPS | Lấy danh sách quảng cáo, cơ chế Header Bidding |
| `api.hellay.net` | Midas Ads Sub-engine | 443 | HTTPS | Đấu thầu quảng cáo v2, ép lấy quảng cáo (`force_get_ad`) |
| `midas-api.hellay.net` | Midas Operations | 443 | HTTPS | Danh sách phản hồi và lọc quảng cáo xấu |
| `adconf.rqmob.com` | Midas Config | 443 | HTTPS | Tải cấu hình SDK mạng quảng cáo |
| `adcs.rqmob.com` | Beyla System | 443 | HTTPS | Ping hệ thống Beyla nhắm mục tiêu quảng cáo |
| `ap-adcs.rqmob.com` | Midas Tracking | 443 | HTTPS | Thu thập dữ liệu lượt xem, click và cài đặt (CPI) |
| `appclick.rqmob.com` | Attribution Server | 443 | HTTPS | Ghi nhận click attribution của đối tác |
| `plutus.lacunads.com` | Plutus Ads Engine | 443 | HTTPS | Phân phối chiến dịch quảng cáo tài trợ |
| `googleads.g.doubleclick.net` | Google AdMob Global Anycast | 443 | HTTPS | Tải quảng cáo Google AdMob & Ad Manager |
| `pagead2.googlesyndication.com` | Google Ads | 443 | HTTPS | Phân phối mã quảng cáo Google |
| `mediation.applovin.com` | AppLovin MAX Global | 443 | HTTPS | Đấu thầu MAX Mediation |
| `api.mbridge.com` | Mintegral Ad Network | 443 | HTTPS | Tải video quảng cáo Mintegral |
| `init.supersonicads.com` | ironSource Mediation | 443 | HTTPS | Khởi tạo mediation ironSource |
| `api.unityads.unity3d.com` | Unity Ads | 443 | HTTPS | Tải quảng cáo Unity |
| `api.pangle.io` | ByteDance Pangle Ads | 443 | HTTPS | Quảng cáo TikTok/Pangle |

---

### 2.3. Nhóm Telemetry, Analytics & Crash Logging

| Tên Domain / Hostname | Nhà Cung Cấp / IP | Cổng | Giao Thức | Vai Trò & Chức Năng |
|---|---|---|---|---|
| `ladon.dalyfeds.com` | Ladon Analytics (Nội bộ) | 443 / 80 | HTTPS / HTTP | Thu thập batch log hành vi và hiệu suất truyền tệp |
| `app-measurement.com` | Google Firebase Analytics | 443 | HTTPS | Ghi nhận sự kiện phân tích chuẩn Firebase |
| `app.adjust.com` | Adjust Attribution Engine | 443 | HTTPS | Đo lường nguồn cài đặt ứng dụng |
| `appmetrica.io` | Yandex AppMetrica | 443 | HTTPS | Phân tích thị trường quốc tế |
| `sgfp.tongdun.net` | Tongdun Risk Control | 443 | HTTPS | Đo lường rủi ro thiết bị và chống click farm |
| `log.byteoversea.com` | ByteDance Telemetry | 443 | HTTPS | Ghi nhật ký SDK ByteDance |
| `10.0.2.2` (Loopback Dev) | Máy chủ phát triển cục bộ | 8888 | HTTP | Meituan Logan upload log (Chỉ dùng trong dev) |

---

### 2.4. Nhóm Mạng Phân Phối Nội Dung (Content Delivery Network - CDN)

| Tên Domain / Hostname | Nhà Cung Cấp CDN | Cổng | Giao Thức | Vai Trò & Chức Năng |
|---|---|---|---|---|
| `cdn.ushareit.com` | Akamai / Cloudflare CDN | 443 | HTTPS | Phân phối Icon, hình ảnh ứng dụng, Lottie data |
| `cdn.wshareit.com` | Cloudflare CDN | 443 | HTTPS | Tài nguyên giao diện động MCDS |
| `rs.wshareit.com` | AWS CloudFront CDN | 443 | HTTPS | Ảnh cấu hình Remote Config |
| `igame-cdn.wshareit.com` | Cloudflare CDN | 443 | HTTPS | Tải tài nguyên WebP của mini game H5 |
| `huosu.shareitgames.com` | Gaming Gateway | 443 | HTTPS | Cổng trò chơi HTML5 |

