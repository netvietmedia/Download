param(
    [string]$ServerUrl = "http://localhost:6030",
    [switch]$WaitForCompletion = $true
)

$ErrorActionPreference = "Stop"
$BackendDir = Split-Path -Parent $PSScriptRoot
$LogFile = "D:\SOCIAL MEDIA\Page\VideoLibrary\daily-scan.log"

function Write-ScanLog([string]$Message) {
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $line = "[$ts] $Message"
    Write-Host $line
    try {
        $logDir = Split-Path -Parent $LogFile
        if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
        Add-Content -Path $LogFile -Value $line -Encoding UTF8
    } catch {}
}

Write-ScanLog "=== BẮT ĐẦU QUÉT ĐỊNH KỲ KÊNH / PAGE YÊU THÍCH ==="

# 1. Đảm bảo Backend Server đang chạy trên cổng 6030
$serverAlive = $false
try {
    $health = Invoke-RestMethod -Uri "$ServerUrl/api/v1/reels/favorites" -Method Get -TimeoutSec 4
    if ($health.success) { $serverAlive = $true }
} catch {
    $serverAlive = $false
}

if (-not $serverAlive) {
    Write-ScanLog "Backend chưa chạy. Đang tự động khởi động Share Now Backend tại $BackendDir ..."
    Start-Process -FilePath "node" -ArgumentList "dist/server.js" -WorkingDirectory $BackendDir -WindowStyle Hidden
    for ($i = 0; $i -lt 15; $i++) {
        Start-Sleep -Seconds 1
        try {
            $health = Invoke-RestMethod -Uri "$ServerUrl/api/v1/reels/favorites" -Method Get -TimeoutSec 3
            if ($health.success) {
                $serverAlive = $true
                break
            }
        } catch {}
    }
}

if (-not $serverAlive) {
    Write-ScanLog "LỖI: Không thể kết nối tới Backend tại $ServerUrl."
    exit 1
}

# 2. Kích hoạt quét toàn bộ kênh yêu thích (Incremental: chỉ tải video mới)
$resp = Invoke-RestMethod -Uri "$ServerUrl/api/v1/reels/favorites/scan-all" -Method Post -ContentType "application/json" -Body "{}"
$jobs = @($resp.jobs)
Write-ScanLog "Đã kích hoạt $($jobs.Count) tiến trình quét kênh yêu thích."

if (-not $WaitForCompletion -or $jobs.Count -eq 0) {
    exit 0
}

# 3. Theo dõi cho đến khi các tiến trình quét hoàn tất
$pendingIds = $jobs | ForEach-Object { $_.jobId }
while ($pendingIds.Count -gt 0) {
    Start-Sleep -Seconds 5
    $stillRunning = @()
    foreach ($jid in $pendingIds) {
        try {
            $jResp = Invoke-RestMethod -Uri "$ServerUrl/api/v1/reels/jobs/$jid" -Method Get -TimeoutSec 5
            $job = $jResp.job
            if ($job.status -in @("QUEUED", "SCANNING", "DOWNLOADING")) {
                $stillRunning += $jid
            } else {
                Write-ScanLog "Hoàn tất [$($job.platform.ToUpper())] $($job.pageSlug): Trạng thái=$($job.status), Video mới tải=$($job.downloadedReels), Đã có sẵn=$($job.skippedExistingReels)"
            }
        } catch {
            $stillRunning += $jid
        }
    }
    $pendingIds = $stillRunning
}

Write-ScanLog "=== HOÀN TẤT QUÉT ĐỊNH KỲ ==="
