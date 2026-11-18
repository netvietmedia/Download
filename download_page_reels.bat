@echo off
chcp 65001 >nul
cd /d "%~dp0Convert\Backend"
if "%~1"=="" (
  echo Usage: download_page_reels.bat "https://www.facebook.com/samfamily.6826/" [--max N] [--quality 1080p^|hd^|sd]
  exit /b 1
)
call npm run download-page -- %*
