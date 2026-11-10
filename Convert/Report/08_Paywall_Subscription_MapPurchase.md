# Báo cáo Phân Tích Kịch Bản Paywall, Gói Cước Subscription & Bản Đồ Mua Hàng (Map Purchase)

## 1. Phân Tích Kịch Bản Kích Hoạt Paywall (Paywall Triggers & User Flow)

Ứng dụng SHAREit triển khai chiến lược kiếm tiền kép (Hybrid Monetization): vừa chạy quảng cáo (Ads), vừa thúc đẩy chuyển đổi người dùng sang thuê bao VIP (Subscription). Paywall được thiết kế hiển thị tại các điểm chạm có giá trị cao (High-Intent Touchpoints):

```mermaid
flowchart TD
    T1[Điểm Chạm 1: Sau khi hoàn thành truyền file dung lượng lớn >1GB] --> PW{Màn hình Paywall VIP}
    T2[Điểm Chạm 2: Mở khóa két an toàn Safebox quá 10 file] --> PW
    T3[Điểm Chạm 3: Bấm nút 'Dọn rác chuyên sâu' Deep Clean] --> PW
    T4[Điểm Chạm 4: Bấm biểu tượng 'No Ads' / Vương miện trên Thanh Header] --> PW
    T5[Điểm Chạm 5: Đạt giới hạn tốc độ truyền P2P thông thường] --> PW

    PW -->|Người dùng chọn gói & Thanh toán| GP[Google Play Billing / Apple StoreKit]
    GP -->|Thành công: Cấp Purchase Token| BE[Backend Cổng 6030: /api/v1/pay/order/verify]
    BE -->|Xác thực hợp lệ| VIP[Mở khóa Quyền lợi VIP Toàn diện]
```

---

## 2. Ma Trận Các Gói Thuê Bao VIP & Đặc Quyền (Pricing & Entitlements)

| Mã Gói (Plan ID) | Tên Gói Cước | Thời hạn | Mức giá Đề xuất | Google Product ID | Apple Product ID | Đặc quyền mở khóa (Privileges) |
|---|---|---|---|---|---|---|
| `vip_weekly` | Gói VIP Tuần | 7 ngày (Có 3 ngày dùng thử) | $1.99 / tuần | `shareit.vip.weekly` | `com.shareit.sub.weekly` | • Không quảng cáo 100%<br>• Tốc độ truyền tối đa (Max Speed 200MB/s)<br>• Két Safebox dung lượng 50GB |
| `vip_monthly` | Gói VIP Tháng | 30 ngày | $4.99 / tháng | `shareit.vip.monthly` | `com.shareit.sub.monthly` | • Toàn bộ quyền gói tuần<br>• Tải video HD không giới hạn<br>• Quét dọn rác chuyên sâu tự động |
| `vip_yearly` | **Gói VIP Năm (Phổ biến nhất - Tiết kiệm 50%)** | 365 ngày | $24.99 / năm ($2.08/th) | `shareit.vip.yearly` | `com.shareit.sub.yearly` | • Toàn bộ quyền gói tháng<br>• Cloud Vault 200GB<br>• Huy hiệu VIP độc quyền trên hồ sơ<br>• Hỗ trợ kỹ thuật ưu tiên 24/7 |
| `vip_lifetime` | Gói VIP Trọn Đời (Mua 1 lần) | Vĩnh viễn (Non-consuming) | $49.99 (Thanh toán 1 lần) | `shareit.vip.lifetime` | `com.shareit.sub.lifetime` | • Mở khóa vĩnh viễn toàn bộ tính năng cao cấp cho mọi thiết bị đăng nhập cùng tài khoản |

---

## 3. Bản Đồ Quy Trình Mua Hàng Chi Tiết (Map Purchase Lifecycle)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant App as Android Client (Kotlin)
    participant GP as Google Play Billing v7
    participant Server as Backend Server (Cổng 6030)
    participant GoogleAPI as Google Play Developer API v3

    User->>App: Bấm chọn gói "VIP Năm ($24.99)"
    App->>GP: Khởi chạy BillingFlow (ProductDetails)
    GP-->>User: Hiển thị BottomSheet Thanh toán Google Play
    User->>GP: Xác nhận vân tay / Mật khẩu thanh toán
    GP-->>App: Trả về PurchasesUpdatedListener (PurchaseToken, OrderId)
    
    App->>Server: POST /api/v1/pay/order/verify (PurchaseToken, ProductId)
    Server->>GoogleAPI: GET /androidpublisher/v3/applications/.../purchases/subscriptions/
    GoogleAPI-->>Server: Trả về SubscriptionPurchase (paymentState: 1, expiryTimeMillis)
    
    Server->>Server: Cập nhật DB: set is_vip = true, vip_expire_at
    Server-->>App: Trả về 200 OK + JWT Token VIP mới
    App->>App: Lưu trạng thái VIP vào MMKV, Ẩn toàn bộ Quảng cáo
    App-->>User: Hiển thị Animation chúc mừng nâng cấp VIP thành công!
```

---

## 4. Xử Lý Các Trường Hợp Biên & Sự Kiện Tự Động (Edge Cases & Lifecycle)

1. **Khôi Phục Gói Đã Mua (Restore Purchases)**:
   - Khi người dùng cài lại máy hoặc đổi điện thoại, nút *"Khôi phục giao dịch"* trên Paywall sẽ gọi `billingClient.queryPurchasesAsync()` để lấy lại toàn bộ `PurchaseToken` đang hoạt động và gửi lên server kích hoạt lại quyền lợi VIP mà không mất thêm phí.
2. **Thời Gian Ân Hạn Khi Thẻ Lỗi (Grace Period - 16 ngày)**:
   - Khi đến kỳ gia hạn nhưng thẻ ngân hàng của người dùng hết tiền, Google Play đưa thuê bao vào trạng thái Grace Period. Server duy trì quyền VIP cho người dùng trong 16 ngày này và gửi Push Notification nhắc người dùng cập nhật phương thức thanh toán.
3. **Tạm Dừng Thuê Bao (Account Hold - 30 ngày)**:
   - Sau thời gian ân hạn vẫn không trừ tiền được, thuê bao chuyển sang Account Hold. Server tạm khóa quyền VIP nhưng giữ nguyên dữ liệu trong Safebox.
4. **Hủy Thuê Bao (Subscription Cancellation)**:
   - Người dùng hủy gia hạn trên Google Play: Quyền VIP vẫn được duy trì cho đến hết ngày kết thúc chu kỳ thanh toán hiện tại (`vip_expire_at`).
5. **Hoàn Tiền (Refund Webhook)**:
   - Khi Google Play chấp thuận yêu cầu hoàn tiền, sự kiện `SUBSCRIPTION_REVOKED` được gửi qua Google Cloud Pub/Sub Webhook (`POST /api/v1/pay/webhook/google`). Server lập tức cập nhật `is_vip = false` và thu hồi quyền lợi.

