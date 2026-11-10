# NHẬT KÝ CÔNG VIỆC: TASK GHI LOG BỘ NHỚ (MEMORY LOGGER)

**Trạng thái hiện tại:** ⏸️ **ĐÃ TẠM DỪNG (PAUSED)**  
**Dự án:** Share Now (`com.nextai.sharenow`)  
**Ngày cập nhật:** 13/10/2026  

---

## 1. Tóm Tắt Trạng Thái Tạm Dừng

Theo yêu cầu của bạn, hệ thống đo lường và ghi log bộ nhớ định kỳ đã được **tạm thời tắt hoàn toàn** để giải phóng tài nguyên CPU/RAM và không chiếm dụng ổ cứng thiết bị khi thử nghiệm hoặc phát hành:

* **Coroutine Foreground (60s/lần):** ĐÃ TẮT.
* **Tác vụ ngầm WorkManager `MemoryLogWorker` (15 phút/lần):** ĐÃ HỦY VÀ NGỪNG LÊN LỊCH.
* **Ghi tệp cục bộ (`memory_monitor.log`):** ĐÃ TẠM DỪNG.
* **Đồng bộ Crashlytics memory keys:** ĐÃ TẠM DỪNG.

---

## 2. Các Thành Phần Đã Hoàn Thiện Trong Mã Nguồn

Mã nguồn của hệ thống vẫn được lưu trữ nguyên vẹn, sẵn sàng kích hoạt lại bất kỳ lúc nào:

| Tệp mã nguồn | Vai trò kỹ thuật |
| :--- | :--- |
| [`MemoryLogger.kt`](file:///F:/APP/Share%20File/Share%20it/Convert/Android/app/src/main/kotlin/com/ushareit/core/monitor/MemoryLogger.kt) | Chứa toàn bộ logic đo JVM Heap, Native Heap C++, System RAM, Storage, cơ chế xoay vòng tệp log 2MB và callback cứu nguy khi tụt RAM (`onTrimMemory`, `onLowMemory`). |
| [`MemoryLogWorker.kt`](file:///F:/APP/Share%20File/Share%20it/Convert/Android/app/src/main/kotlin/com/ushareit/core/monitor/MemoryLogWorker.kt) | Kế thừa `CoroutineWorker` chuẩn Android Jetpack WorkManager chạy nền. |
| [`ShareItApplication.kt`](file:///F:/APP/Share%20File/Share%20it/Convert/Android/app/src/main/kotlin/com/ushareit/app/ShareItApplication.kt) | Điểm kiểm soát vòng đời ứng dụng. |

---

## 3. Cách Bật Lại Khi Muốn Tiếp Tục Chạy ("Lúc khác chạy tiếp")

Khi bạn muốn kích hoạt lại hệ thống ghi log bộ nhớ, chỉ cần thực hiện **1 trong 2 cách** sau:

### Cách A: Kích hoạt bằng code (Khuyến nghị)
Tại bất kỳ đâu (ví dụ trong `ShareItApplication.kt` hoặc khi ấn một nút trong màn hình Cài đặt):
```kotlin
// Tiếp tục chạy lại task ghi log bộ nhớ
MemoryLogger.resumeLogging(context)
```

### Cách B: Đổi cờ cấu hình trong `MemoryLogger.kt`
Mở file [`MemoryLogger.kt`](file:///F:/APP/Share%20File/Share%20it/Convert/Android/app/src/main/kotlin/com/ushareit/core/monitor/MemoryLogger.kt):
```kotlin
// Đổi false thành true:
@Volatile
var isLoggingEnabled: Boolean = true
```

---

## 4. Nhật Ký Lịch Sử Bản Build Đã Đóng Gói
* **Bản Build v1.4 (versionCode 14 - 03/11/2026)**:
  - Tệp AAB: `Convert/Android/app/build/outputs/bundle/release/app-release.aab` (`16.56 MB`)
  - Tệp APK: `Convert/Android/app/build/outputs/apk/release/app-release.apk` (`13.99 MB`)
  - Target SDK: 36 (Android 16) | 16 KB Page Size: Verified (`zipalign -P 16` OK)
  - Tính năng chính: Browser & Video Downloader đa luồng, giới hạn 3 video miễn phí + Paywall trên 1,822 web monetized, chặn quảng cáo toàn diện, chống crash WebView, đồng bộ xóa file và đo lường 17 sự kiện Firebase Analytics cho Browser.
* **Bản Build v0.1.2 (versionCode 3)**:
  - Tệp AAB: `Convert/Info/sharenow-v0.1.2-release.aab`
  - Tệp APK: `Convert/Info/sharenow-v0.1.2-release.apk`
  - Target SDK: 36 (Android 16)
  - Play Billing Library: 9.0.0
  - Keystore alias: `sharenow` (Mật khẩu: `sharenow2026`)
