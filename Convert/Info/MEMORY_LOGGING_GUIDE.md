# Hướng Dẫn Kỹ Thuật: Tác Vụ Đo Lường & Ghi Log Bộ Nhớ (Memory Logger & Worker)

Hệ thống ghi log bộ nhớ được tích hợp sẵn trong ứng dụng **Share Now** (`com.nextai.sharenow`), hoạt động tự động cả khi ứng dụng đang mở (Foreground) và khi ứng dụng chạy nền (Background qua WorkManager).

---

## 1. Cấu Trúc Hệ Thống

| Thành phần | Đường dẫn tệp | Chức năng |
| :--- | :--- | :--- |
| **MemoryLogger** | [`com/ushareit/core/monitor/MemoryLogger.kt`](file:///F:/APP/Share%20File/Share%20it/Convert/Android/app/src/main/kotlin/com/ushareit/core/monitor/MemoryLogger.kt) | Thu thập thông số bộ nhớ, ghi logcat, ghi file cục bộ, bắt sự kiện Low Memory và đồng bộ Firebase Crashlytics |
| **MemoryLogWorker** | [`com/ushareit/core/monitor/MemoryLogWorker.kt`](file:///F:/APP/Share%20File/Share%20it/Convert/Android/app/src/main/kotlin/com/ushareit/core/monitor/MemoryLogWorker.kt) | Tác vụ ngầm định kỳ 15 phút/lần qua Android WorkManager tuân thủ chuẩn tiết kiệm pin Doze Mode |
| **ShareItApplication** | [`com/ushareit/app/ShareItApplication.kt`](file:///F:/APP/Share%20File/Share%20it/Convert/Android/app/src/main/kotlin/com/ushareit/app/ShareItApplication.kt) | Điểm khởi tạo duy nhất: `MemoryLogger.init(this)` khi khởi động app |

---

## 2. Các Thông Số Bộ Nhớ Được Ghi Nhận

Mỗi dòng nhật ký (Log entry) bao gồm:
1. **JVM Heap**: 
   - `jvmHeapUsedMb`: Dung lượng bộ nhớ Java/Kotlin đang sử dụng.
   - `jvmHeapMaxMb`: Giới hạn Heap tối đa của tiến trình app.
   - `jvmHeapPercent`: Tỉ lệ % Heap đã chiếm dụng.
2. **Native Heap (C++)**:
   - `nativeHeapAllocatedMb`: Bộ nhớ cấp phát cho tầng C++ (P2P socket transmission engine, buffer đồ họa, JNI).
   - `nativeHeapSizeMb`: Tổng kích thước Native Heap hiện thời.
3. **RAM Hệ Thống (System RAM)**:
   - `systemRamAvailMb`: Dung lượng RAM khả dụng trên toàn điện thoại.
   - `systemRamTotalMb`: Tổng RAM vật lý của thiết bị.
   - `systemRamLowMemory`: Cờ boolean hệ điều hành cảnh báo máy sắp cạn RAM (`true`/`false`).
4. **Bộ Nhớ Trong (Internal Storage)**:
   - `storageFreeMb`: Dung lượng trống còn lại trên ổ cứng trong máy.

---

## 3. Định Dạng Dòng Log Mẫu

```text
[2026-10-13 09:30:00.125] [Periodic_Active] JVM: 28.4/256.0 MB (11%) | Native: 14.2/20.0 MB | RAM Avail: 1850.5/3750.0 MB (Low: false) | Storage Free: 12450.0 MB
```

---

## 4. Cơ Chế Chống Sập Ứng Dụng (Low Memory Handling)

Hệ thống đăng ký `ComponentCallbacks2` để theo dõi các cấp độ thiếu RAM của Android:
* Khi nhận cảnh báo `TRIM_MEMORY_RUNNING_LOW` hoặc `onLowMemory()`:
  1. Tự động xả sạch bộ nhớ cache hình ảnh Glide: `Glide.get(context).clearMemory()`.
  2. Kích hoạt dọn rác khẩn cấp: `System.gc()`.
  3. Ghi cảnh báo `[TRIM_ALERT]` vào file log.
  4. Gửi tín hiệu và Custom Keys (`mem_jvm_used_mb`, `mem_ram_avail_mb`, `mem_is_low`) lên **Firebase Crashlytics**.

---

## 5. Hướng Dẫn Xem & Xuất Log Thực Tế

### Cách 1: Xem trực tiếp qua Android Logcat (khi cắm cáp USB hoặc Wi-Fi ADB)
```bash
adb logcat -s ShareNow_Memory
```
Hoặc lọc các cảnh báo tụt RAM khẩn cấp:
```bash
adb logcat -s ShareNow_Memory:W
```

### Cách 2: Trích xuất tệp log cục bộ từ thiết bị ra máy tính
Tệp log được lưu tại: `/data/data/com.nextai.sharenow/files/logs/memory_monitor.log` (tối đa 2MB, tự động xoay vòng thành `memory_monitor.log.old`).
```bash
# Xem trực tiếp nội dung tệp log
adb shell "run-as com.nextai.sharenow cat files/logs/memory_monitor.log"

# Hoặc tải file log về máy tính để phân tích
adb exec-out run-as com.nextai.sharenow cat files/logs/memory_monitor.log > memory_monitor.log
```

### Cách 3: Đọc qua mã nguồn Kotlin trong ứng dụng
```kotlin
// Đọc toàn bộ nội dung log đã ghi
val logContent: String = MemoryLogger.readLogs(context)

// Hoặc kích hoạt ghi ngay 1 dòng log tại thời điểm người dùng bắt đầu gửi/nhận file
MemoryLogger.logCurrentMemory(context, "Start_Transfer")
```
