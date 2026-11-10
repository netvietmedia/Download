# BÁO CÁO 18: HỆ THỐNG COIN ECONOMY, GAMIFICATION & QUY TRÌNH QUẢN TRỊ KINH TẾ ẢO

---

## 1. TỔNG QUAN HỆ THỐNG COIN TRONG SHAREIT

Trong ứng dụng **SHAREit**, hệ thống **Coin (Xu thưởng / Điểm tích lũy)** đóng vai trò là **Đòn bẩy tăng trưởng (Growth Engine)** và **Cơ chế giữ chân người dùng (Retention & Engagement Mechanism)**:

- **Nguồn sinh Coin (Faucet)**: Điểm danh hàng ngày (Check-in), Truyền file P2P dung lượng lớn, Xem video quảng cáo nhận thưởng (Rewarded Ads), Cài đặt và dọn rác CLEANit, Giới thiệu bạn bè (Referral).
- **Kho lưu trữ & Kiểm soát**: Ví người dùng (User Wallet) kết nối định danh Device UUID, sổ cái kép (Double-entry Ledger) ghi nhận lịch sử bất biến.
- **Nguồn tiêu thụ (Sink/Burn)**: Đổi gói VIP không quảng cáo (1 ngày / 7 ngày), Đổi thẻ cào điện thoại (Viettel, Vina, Mobi), E-Voucher mua sắm (Shopee, Lazada), Mã nạp game (Garena, Zing).

---

## 2. CÁC PHÂN HỆ QUẢN TRỊ TRÊN CMS DASHBOARD (PORT 6030)

### 2.1. Thẻ Chỉ Số Kinh Tế Vĩ Mô
- **Tổng Xu Đã Phát Hành (Total Issued)**: Tổng số coin được sinh ra từ tất cả nhiệm vụ và quản trị viên nạp.
- **Tổng Xu Đang Lưu Hành (Circulation)**: Số coin hiện nằm trong ví người dùng, có nguy cơ quy đổi.
- **Tổng Xu Đã Đổi Thưởng (Total Burned/Redeemed)**: Số coin đã tiêu hủy thành công qua các gói quà.
- **Yêu Cầu Chờ Duyệt (Pending Redemptions)**: Cảnh báo đơn đổi thưởng thẻ cào/voucher cần phê duyệt.

### 2.2. Quản Lý Nhiệm Vụ Kiếm Coin (Gamification Mission Center)
- Hỗ trợ CRUD: Tạo mới, Sửa phần thưởng xu, Đặt giới hạn số lần/ngày, Gắn Action Deep Link trong app.

### 2.3. Cửa Hàng Đổi Thưởng (Coin Rewards Shop Catalog)
- Hỗ trợ danh mục: VIP Pass, Thẻ Cào, E-Voucher, Thẻ Game.
- Kiểm soát số lượng kho (Stock limit) và giá quy đổi xu tương ứng.

### 2.4. Phê Duyệt Đổi Quà & Chống Gian Lận (Redemption Approvals & Anti-Fraud)
- Xem thông tin chi tiết người đổi (Device UUID, Target SĐT/Email, Loại quà, Số xu).
- Nút Phê duyệt: Hoàn tất trao thưởng.
- Nút Từ chối: Hủy đơn và Tự động hoàn xu (Auto-refund) về ví người dùng kèm ghi chú.

### 2.5. Điều Chỉnh Số Dư Thủ Công & Tra Cứu Sổ Cái (Manual Adjustment & Double-Entry Ledger)
- Admin có thể cộng/trừ xu cho bất kỳ User ID nào với lý do minh bạch.
- Hệ thống ghi nhận Audit Log và Transaction Record bất biến.