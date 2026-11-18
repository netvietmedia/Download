@echo off
chcp 65001 >nul
title Share Now - AI Agent Reels Downloader Web Server (Port 6030)
cd /d "%~dp0Convert\Backend"
echo =============================================================
echo Starting Share Now Reels Auto-Downloader Web App ^& API...
echo Web UI : http://localhost:6030/reels
echo API    : POST http://localhost:6030/api/v1/reels/auto-download
echo =============================================================
if not exist "dist\server.js" (
  call npm run build
)
start "" "http://localhost:6030/reels"
node dist\server.js
