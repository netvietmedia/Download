# Set UTF-8 Output
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host " SHARENOW - TIẾN TRÌNH KÉO DỮ LIỆU GA4 VÀ TỐI ƯU DOANH THU ĐA KÊNH" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "[1/3] Đang chạy kịch bản kéo dữ liệu từ GA4 / Firebase..." -ForegroundColor Yellow
python pull_ga4_events.py

Write-Host "`n[2/3] Đang phân tích eCPM, ROAS và tối ưu hóa chiến dịch quảng cáo..." -ForegroundColor Yellow
python revenue_optimizer.py

Write-Host "`n[3/3] Đang tạo Dashboard HTML trực quan tương tác..." -ForegroundColor Yellow
python generate_dashboard.py

Write-Host "`n======================================================================" -ForegroundColor Green
Write-Host " HOÀN TẤT! ĐANG MỞ DASHBOARD TẠI TRÌNH DUYỆT..." -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green

Start-Process "dashboard.html"

