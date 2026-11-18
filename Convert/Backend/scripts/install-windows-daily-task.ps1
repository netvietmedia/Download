param(
    [string]$TaskName = "ShareNow_DailyFavoriteChannelsScan",
    [string]$DailyTime = "08:00"
)

$ErrorActionPreference = "Stop"
$ScanScript = Join-Path $PSScriptRoot "scan-favorites-now.ps1"

if (-not (Test-Path $ScanScript)) {
    throw "Không tìm thấy script: $ScanScript"
}

$Action = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$ScanScript`""

$TriggerDaily = New-ScheduledTaskTrigger -Daily -At $DailyTime
$Settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -RunOnlyIfNetworkAvailable

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $Action `
    -Trigger $TriggerDaily `
    -Settings $Settings `
    -Description "Tự động quét các Page Facebook, Instagram, YouTube yêu thích hàng ngày và tải ngay video mới." `
    -Force | Out-Null

Write-Host "Đã đăng ký thành công tác vụ tự động Windows Task Scheduler: '$TaskName' chạy hàng ngày lúc $DailyTime (và tự chạy bù nếu lúc đó máy đang tắt nhờ StartWhenAvailable)."
