package com.ushareit.core.webshare

import android.content.Context
import com.ushareit.core.p2p.P2PTransferManager

object WebShareDashboardHtml {

    fun getHtml(context: Context): String {
        val deviceName = P2PTransferManager.getDeviceDisplayName()
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Share Now WebShare - $deviceName</title>
    <style>
        :root {
            --bg-base: #0b1120;
            --bg-card: rgba(30, 41, 59, 0.7);
            --bg-card-hover: rgba(51, 65, 85, 0.8);
            --border: rgba(148, 163, 184, 0.15);
            --border-hover: rgba(56, 189, 248, 0.4);
            --primary: #2563eb;
            --primary-hover: #1d4ed8;
            --accent: #06b6d4;
            --success: #10b981;
            --text-main: #f8fafc;
            --text-sub: #94a3b8;
            --text-muted: #64748b;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background: radial-gradient(circle at 15% 15%, #1e1b4b 0%, #0b1120 50%, #020617 100%);
            color: var(--text-main);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            overflow-x: hidden;
        }

        /* HEADER */
        header {
            background: rgba(15, 23, 42, 0.85);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border-bottom: 1px solid var(--border);
            padding: 16px 24px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            position: sticky;
            top: 0;
            z-index: 50;
        }
        .brand {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .brand-icon {
            width: 40px;
            height: 40px;
            background: linear-gradient(135deg, #0284c7, #2563eb);
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 20px;
            box-shadow: 0 0 20px rgba(37, 99, 235, 0.4);
        }
        .brand-name { font-size: 20px; font-weight: 700; color: #fff; letter-spacing: -0.5px; }
        .brand-badge {
            font-size: 11px;
            background: rgba(6, 182, 212, 0.15);
            color: #38bdf8;
            border: 1px solid rgba(56, 189, 248, 0.3);
            padding: 2px 8px;
            border-radius: 999px;
            font-weight: 600;
        }

        .device-info {
            display: flex;
            align-items: center;
            gap: 16px;
            font-size: 13px;
            color: var(--text-sub);
        }
        .status-dot {
            width: 8px;
            height: 8px;
            background: var(--success);
            border-radius: 50%;
            display: inline-block;
            box-shadow: 0 0 10px var(--success);
        }
        .info-pill {
            background: rgba(30, 41, 59, 0.6);
            border: 1px solid var(--border);
            padding: 6px 12px;
            border-radius: 8px;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        /* MAIN CONTAINER */
        .container {
            max-width: 1280px;
            margin: 0 auto;
            width: 100%;
            padding: 24px;
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 20px;
        }

        /* TABS */
        .tabs-bar {
            display: flex;
            gap: 8px;
            background: rgba(15, 23, 42, 0.6);
            backdrop-filter: blur(12px);
            padding: 6px;
            border-radius: 14px;
            border: 1px solid var(--border);
            overflow-x: auto;
        }
        .tab-btn {
            background: transparent;
            border: none;
            color: var(--text-sub);
            padding: 10px 18px;
            font-size: 14px;
            font-weight: 600;
            border-radius: 10px;
            cursor: pointer;
            transition: all 0.2s ease;
            white-space: nowrap;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .tab-btn:hover {
            color: var(--text-main);
            background: rgba(255, 255, 255, 0.05);
        }
        .tab-btn.active {
            background: linear-gradient(135deg, #2563eb, #1d4ed8);
            color: #fff;
            box-shadow: 0 4px 14px rgba(37, 99, 235, 0.35);
        }

        /* ACTION BAR */
        .action-bar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
            flex-wrap: wrap;
        }
        .search-box {
            position: relative;
            flex: 1;
            min-width: 260px;
        }
        .search-input {
            width: 100%;
            background: rgba(30, 41, 59, 0.6);
            border: 1px solid var(--border);
            padding: 10px 16px 10px 40px;
            border-radius: 10px;
            color: #fff;
            font-size: 14px;
            outline: none;
            transition: border 0.2s;
        }
        .search-input:focus {
            border-color: var(--accent);
            box-shadow: 0 0 10px rgba(6, 182, 212, 0.2);
        }
        .search-icon {
            position: absolute;
            left: 14px;
            top: 50%;
            transform: translateY(-50%);
            color: var(--text-muted);
            pointer-events: none;
        }
        .btn-action {
            background: rgba(30, 41, 59, 0.8);
            border: 1px solid var(--border);
            color: var(--text-main);
            padding: 10px 18px;
            border-radius: 10px;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .btn-action:hover {
            background: var(--bg-card-hover);
            border-color: var(--border-hover);
        }
        .btn-action.primary {
            background: linear-gradient(135deg, #059669, #10b981);
            border-color: transparent;
            color: #fff;
        }
        .btn-action.primary:hover {
            box-shadow: 0 4px 14px rgba(16, 185, 129, 0.35);
        }

        /* DRAG & DROP UPLOAD ZONE */
        .upload-card {
            background: rgba(15, 23, 42, 0.6);
            border: 2px dashed rgba(56, 189, 248, 0.3);
            border-radius: 16px;
            padding: 40px 24px;
            text-align: center;
            transition: all 0.3s;
            cursor: pointer;
            position: relative;
        }
        .upload-card.dragover {
            background: rgba(37, 99, 235, 0.15);
            border-color: #38bdf8;
            transform: scale(1.005);
        }
        .upload-icon {
            font-size: 48px;
            margin-bottom: 12px;
            display: block;
            animation: bounce 2s infinite ease-in-out;
        }
        @keyframes bounce {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-6px); }
        }
        .upload-title { font-size: 17px; font-weight: 700; margin-bottom: 6px; }
        .upload-desc { font-size: 13px; color: var(--text-sub); }

        /* UPLOAD PROGRESS LIST */
        .upload-queue {
            display: flex;
            flex-direction: column;
            gap: 10px;
            margin-top: 14px;
        }
        .upload-item {
            background: rgba(30, 41, 59, 0.8);
            border: 1px solid var(--border);
            border-radius: 10px;
            padding: 12px 16px;
            display: flex;
            flex-direction: column;
            gap: 6px;
        }
        .upload-header {
            display: flex;
            justify-content: space-between;
            font-size: 13px;
        }
        .progress-track {
            height: 6px;
            background: rgba(255, 255, 255, 0.1);
            border-radius: 999px;
            overflow: hidden;
        }
        .progress-bar {
            height: 100%;
            background: linear-gradient(90deg, #06b6d4, #10b981);
            width: 0%;
            transition: width 0.2s ease;
        }

        /* FILES GRID / TABLE */
        .content-card {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 16px;
            overflow: hidden;
            backdrop-filter: blur(12px);
        }
        .file-table {
            width: 100%;
            border-collapse: collapse;
            text-align: left;
            font-size: 13px;
        }
        .file-table th {
            background: rgba(15, 23, 42, 0.5);
            padding: 14px 18px;
            color: var(--text-sub);
            font-weight: 600;
            border-bottom: 1px solid var(--border);
        }
        .file-table td {
            padding: 14px 18px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
            color: var(--text-main);
        }
        .file-table tr:hover td {
            background: rgba(255, 255, 255, 0.03);
        }
        .file-name-cell {
            display: flex;
            align-items: center;
            gap: 12px;
            font-weight: 500;
        }
        .file-thumb {
            width: 38px;
            height: 38px;
            border-radius: 8px;
            background: rgba(255, 255, 255, 0.05);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            object-fit: cover;
            flex-shrink: 0;
        }
        .file-details {
            display: flex;
            flex-direction: column;
            gap: 3px;
            min-width: 0;
        }
        .file-title {
            color: var(--text-main);
            font-weight: 600;
            line-height: 1.3;
            word-break: break-word;
        }
        .file-pkg-badge {
            font-size: 11px;
            color: #38bdf8;
            background: rgba(56, 189, 248, 0.12);
            border: 1px solid rgba(56, 189, 248, 0.25);
            padding: 1px 4px 1px 7px;
            border-radius: 6px;
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
            display: inline-flex;
            align-items: center;
            gap: 2px;
            width: fit-content;
            max-width: 100%;
        }
        .pkg-name-text {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .btn-copy-pkg {
            background: transparent;
            border: none;
            color: #38bdf8;
            cursor: pointer;
            padding: 2px 4px;
            border-radius: 4px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            transition: all 0.15s ease;
            opacity: 0.85;
            line-height: 1;
        }
        .btn-copy-pkg:hover {
            background: rgba(56, 189, 248, 0.25);
            opacity: 1;
            transform: scale(1.15);
        }
        .btn-copy-pkg.copied {
            color: #10b981;
        }
        .btn-uninstall {
            background: rgba(239, 68, 68, 0.15);
            color: #f87171;
            border: 1px solid rgba(239, 68, 68, 0.3);
            padding: 6px 12px;
            border-radius: 8px;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
            display: inline-flex;
            align-items: center;
            gap: 4px;
            white-space: nowrap;
        }
        .btn-uninstall:hover {
            background: #dc2626;
            color: #fff;
            box-shadow: 0 2px 10px rgba(239, 68, 68, 0.4);
            border-color: transparent;
            transform: translateY(-1px);
        }
        .btn-uninstall:active {
            transform: translateY(0);
        }
        .notice-sub {
            margin-top: 5px;
            font-size: 12px;
            color: #fef08a;
            opacity: 0.85;
        }
        .btn-download {
            background: rgba(37, 99, 235, 0.2);
            color: #60a5fa;
            border: 1px solid rgba(96, 165, 250, 0.3);
            padding: 6px 14px;
            border-radius: 8px;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
            text-decoration: none;
            display: inline-flex;
            align-items: center;
            gap: 4px;
        }
        .btn-download:hover {
            background: #2563eb;
            color: #fff;
            box-shadow: 0 2px 10px rgba(37, 99, 235, 0.4);
        }

        /* EMPTY STATE */
        .empty-state {
            padding: 60px 20px;
            text-align: center;
            color: var(--text-muted);
        }
        .empty-icon { font-size: 48px; margin-bottom: 12px; display: block; opacity: 0.6; }

        /* LIGHTBOX PREVIEW */
        .lightbox {
            display: none;
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0, 0, 0, 0.9);
            z-index: 100;
            justify-content: center;
            align-items: center;
            padding: 20px;
        }
        .lightbox.active { display: flex; }
        .lightbox-img {
            max-width: 90vw;
            max-height: 85vh;
            border-radius: 12px;
            box-shadow: 0 0 30px rgba(0,0,0,0.8);
        }
        .lightbox-close {
            position: absolute;
            top: 24px; right: 24px;
            font-size: 32px;
            color: #fff;
            cursor: pointer;
            background: rgba(255,255,255,0.1);
            width: 44px; height: 44px;
            border-radius: 50%;
            display: flex; align-items: center; justify-content: center;
        }

        /* TOAST NOTIFICATION */
        .toast {
            position: fixed;
            bottom: 24px;
            right: 24px;
            background: rgba(16, 185, 129, 0.95);
            color: #fff;
            padding: 12px 20px;
            border-radius: 10px;
            font-weight: 600;
            font-size: 14px;
            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
            transform: translateY(100px);
            opacity: 0;
            transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
            z-index: 999;
        }
        .toast.show {
            transform: translateY(0);
            opacity: 1;
        }

        /* CHROME INSECURE DOWNLOAD BANNER */
        .chrome-notice {
            background: linear-gradient(135deg, rgba(245, 158, 11, 0.12), rgba(234, 179, 8, 0.08));
            border: 1px solid rgba(245, 158, 11, 0.35);
            border-radius: 12px;
            padding: 14px 18px;
            display: flex;
            align-items: flex-start;
            gap: 14px;
            font-size: 13px;
            line-height: 1.5;
            color: #fef08a;
            margin-bottom: 4px;
            position: relative;
            transition: all 0.3s ease;
        }
        .chrome-notice.pulse-highlight {
            border-color: #f59e0b;
            box-shadow: 0 0 24px rgba(245, 158, 11, 0.45);
            transform: scale(1.008);
        }
        .notice-icon { font-size: 22px; flex-shrink: 0; line-height: 1; margin-top: 1px; }
        .notice-content { flex: 1; }
        .notice-title { font-weight: 700; color: #fff; margin-bottom: 4px; font-size: 14px; }
        .notice-desc { color: #fde68a; font-size: 13px; }
        .notice-desc strong { color: #fff; font-weight: 700; }
        .notice-btn-close {
            background: transparent;
            border: none;
            color: var(--text-sub);
            font-size: 20px;
            cursor: pointer;
            padding: 0 6px;
            line-height: 1;
            transition: color 0.2s;
        }
        .notice-btn-close:hover { color: #fff; }

        /* UNINSTALL CONFIRMATION & LIVE TRACKING MODAL */
        .modal-overlay {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(11, 15, 25, 0.78);
            backdrop-filter: blur(8px);
            -webkit-backdrop-filter: blur(8px);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 1000;
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.25s ease;
            padding: 20px;
        }
        .modal-overlay.active {
            opacity: 1;
            pointer-events: auto;
        }
        .modal-card {
            background: var(--bg-card);
            border: 1px solid var(--border-color);
            border-radius: 20px;
            width: 100%;
            max-width: 480px;
            box-shadow: 0 20px 50px rgba(0, 0, 0, 0.6);
            overflow: hidden;
            transform: scale(0.94);
            transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .modal-overlay.active .modal-card {
            transform: scale(1);
        }
        .modal-header {
            padding: 20px 24px;
            background: linear-gradient(135deg, rgba(239, 68, 68, 0.12), rgba(244, 63, 94, 0.05));
            border-bottom: 1px solid var(--border-color);
            display: flex;
            align-items: center;
            gap: 16px;
        }
        .modal-header-icon {
            width: 48px;
            height: 48px;
            border-radius: 14px;
            background: rgba(239, 68, 68, 0.18);
            border: 1px solid rgba(239, 68, 68, 0.3);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
            flex-shrink: 0;
        }
        .modal-title {
            font-size: 17px;
            font-weight: 700;
            color: #fff;
        }
        .modal-subtitle {
            font-size: 13px;
            color: var(--text-sub);
            margin-top: 2px;
        }
        .modal-body {
            padding: 24px;
            display: flex;
            flex-direction: column;
            gap: 18px;
        }
        .modal-app-info {
            display: flex;
            align-items: center;
            gap: 14px;
            background: rgba(255, 255, 255, 0.04);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 12px 16px;
        }
        .modal-app-icon {
            width: 44px;
            height: 44px;
            border-radius: 10px;
            object-fit: cover;
            background: rgba(255, 255, 255, 0.06);
            flex-shrink: 0;
        }
        .modal-app-name {
            font-size: 15px;
            font-weight: 600;
            color: #fff;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .modal-app-pkg {
            font-family: monospace;
            font-size: 12px;
            color: var(--primary);
            word-break: break-all;
            margin-top: 2px;
        }
        .modal-instruction-box {
            background: rgba(245, 158, 11, 0.08);
            border: 1px dashed rgba(245, 158, 11, 0.35);
            border-radius: 12px;
            padding: 14px 16px;
            display: flex;
            flex-direction: column;
            gap: 10px;
            font-size: 13px;
            line-height: 1.5;
            color: #fde68a;
        }
        .inst-step {
            display: flex;
            align-items: flex-start;
            gap: 10px;
        }
        .step-badge {
            width: 20px;
            height: 20px;
            border-radius: 50%;
            background: #f59e0b;
            color: #111;
            font-size: 11px;
            font-weight: 700;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
            margin-top: 1px;
        }
        .modal-status-card {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 14px 16px;
            border-radius: 12px;
            background: rgba(59, 130, 246, 0.1);
            border: 1px solid rgba(59, 130, 246, 0.25);
            color: #93c5fd;
            font-size: 13px;
            font-weight: 500;
            transition: all 0.3s ease;
        }
        .modal-status-card.success {
            background: rgba(16, 185, 129, 0.12);
            border-color: rgba(16, 185, 129, 0.35);
            color: #6ee7b7;
        }
        .modal-status-card.error {
            background: rgba(239, 68, 68, 0.12);
            border-color: rgba(239, 68, 68, 0.35);
            color: #fca5a5;
        }
        .pulse-spinner {
            width: 18px;
            height: 18px;
            border: 2px solid rgba(59, 130, 246, 0.3);
            border-top-color: #3b82f6;
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
            flex-shrink: 0;
        }
        @keyframes spin {
            to { transform: rotate(360deg); }
        }
        .modal-footer {
            padding: 16px 24px;
            background: rgba(0, 0, 0, 0.15);
            border-top: 1px solid var(--border-color);
            display: flex;
            justify-content: flex-end;
            gap: 10px;
        }
    </style>
</head>
<body>

    <header>
        <div class="brand">
            <div class="brand-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="16 3 21 8 16 13"></polyline>
                    <line x1="21" y1="8" x2="9" y2="8"></line>
                    <path d="M9 8a5 5 0 0 0-5 5v0"></path>
                    <polyline points="8 21 3 16 8 11"></polyline>
                    <line x1="3" y1="16" x2="15" y2="16"></line>
                    <path d="M15 16a5 5 0 0 0 5-5v0"></path>
                </svg>
            </div>
            <div>
                <div style="display:flex; align-items:center; gap:8px;">
                    <span class="brand-name">Share Now</span>
                    <span class="brand-badge">WebShare v2.0</span>
                </div>
            </div>
        </div>

        <div class="device-info">
            <div class="info-pill">
                <span class="status-dot"></span>
                <span>Direct Connection</span>
            </div>
            <div class="info-pill" id="pill-device">
                <span>📱 $deviceName</span>
            </div>
            <div class="info-pill" id="pill-battery">
                <span>🔋 Battery: Loading...</span>
            </div>
            <div class="info-pill" id="pill-storage">
                <span>💾 Free Storage: Loading...</span>
            </div>
        </div>
    </header>

    <div class="container">

        <!-- CHROME INSECURE DOWNLOAD GUIDE BANNER -->
        <div class="chrome-notice" id="chrome-notice">
            <span class="notice-icon">💡</span>
            <div class="notice-content">
                <div class="notice-title">Google Chrome Download Tip (PC):</div>
                <div class="notice-desc">
                    If Chrome displays <strong>"Insecure download blocked"</strong> due to local Wi-Fi (HTTP):<br>
                    👉 Click the <strong>Downloads icon (⬇️)</strong> in Chrome's top-right toolbar ➔ select <strong>"Keep"</strong> (or <strong>"Keep anyway"</strong>) to finish downloading!
                    <div class="notice-sub">
                        <em>(Mẹo VI: Nếu Chrome báo "Tệp không an toàn đã bị chặn", bấm biểu tượng Tải xuống ⬇️ góc trên phải Chrome ➔ chọn "Giữ tệp" / "Vẫn tải xuống" là xong!)</em>
                    </div>
                </div>
            </div>
            <button class="notice-btn-close" onclick="document.getElementById('chrome-notice').style.display='none'" title="Close">&times;</button>
        </div>

        <!-- TABS -->
        <div class="tabs-bar">
            <button class="tab-btn active" onclick="switchCategory('PHOTO', this)">📷 Photos <span id="badge-PHOTO" style="font-size:11px; opacity:0.85;"></span></button>
            <button class="tab-btn" onclick="switchCategory('VIDEO', this)">🎬 Videos <span id="badge-VIDEO" style="font-size:11px; opacity:0.85;"></span></button>
            <button class="tab-btn" onclick="switchCategory('MUSIC', this)">🎵 Music <span id="badge-MUSIC" style="font-size:11px; opacity:0.85;"></span></button>
            <button class="tab-btn" onclick="switchCategory('APP', this)">📱 Apps (APK) <span id="badge-APP" style="font-size:11px; opacity:0.85;"></span></button>
            <button class="tab-btn" onclick="switchCategory('DOCUMENT', this)">📄 Documents <span id="badge-DOCUMENT" style="font-size:11px; opacity:0.85;"></span></button>
            <button class="tab-btn" onclick="switchCategory('RECEIVED', this)">📥 Received Files <span id="badge-RECEIVED" style="font-size:11px; opacity:0.85;"></span></button>
            <button class="tab-btn" onclick="switchCategory('UPLOAD', this)">📤 Upload to Phone</button>
        </div>

        <!-- UPLOAD SECTION (SHOWS WHEN UPLOAD TAB IS SELECTED OR AS A HEADER) -->
        <div id="section-upload" style="display:none;">
            <div class="upload-card" id="drop-zone" onclick="document.getElementById('file-input').click()">
                <input type="file" id="file-input" multiple style="display:none;" onchange="handleFileSelect(this.files)">
                <span class="upload-icon">📤</span>
                <div class="upload-title">Drag &amp; drop files from computer here to send to phone</div>
                <div class="upload-desc">Supports all file types: 4K Videos, Photos, Music, PDF, ZIP... High-speed Wi-Fi transfer with no file size limit</div>
            </div>
            <div class="upload-queue" id="upload-queue"></div>
        </div>

        <!-- FILE LIST SECTION -->
        <div id="section-files">
            <div class="action-bar" style="margin-bottom: 16px;">
                <div class="search-box">
                    <span class="search-icon">🔍</span>
                    <input type="text" class="search-input" id="search-input" placeholder="Search files or package name..." oninput="filterFiles()">
                </div>
                <div style="display:flex; gap:10px; flex-wrap:wrap;">
                    <button class="btn-action" id="btn-pick-phone" onclick="requestPhonePicker(currentCategory)">📱 Select on Phone</button>
                    <button class="btn-action" onclick="loadFiles(currentCategory, true)">🔄 Refresh</button>
                    <button class="btn-action primary" onclick="downloadAllZip()">📦 Download All (ZIP)</button>
                </div>
            </div>

            <div class="content-card">
                <table class="file-table">
                    <thead>
                        <tr>
                            <th style="width: 40px;"><input type="checkbox" id="check-all" onchange="toggleSelectAll(this)"></th>
                            <th id="th-filename">File Name</th>
                            <th style="width: 120px;">Size</th>
                            <th style="width: 160px;">Date Modified</th>
                            <th style="width: 120px; text-align: right;">Action</th>
                        </tr>
                    </thead>
                    <tbody id="files-tbody">
                        <tr><td colspan="5" class="empty-state"><span class="empty-icon">⏳</span>Loading files from phone...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>

    </div>

    <!-- LIGHTBOX -->
    <div class="lightbox" id="lightbox" onclick="closeLightbox()">
        <span class="lightbox-close">&times;</span>
        <img class="lightbox-img" id="lightbox-img" src="" alt="Preview">
    </div>

    <!-- TOAST -->
    <div class="toast" id="toast">Notification</div>

    <script>
        let currentCategory = 'PHOTO';
        let allFiles = [];
        let pickerPollTimer = null;

        // Khởi động trang
        window.onload = function() {
            fetchStatus(false);
            loadFiles('PHOTO', false);
            setupDragAndDrop();
        };

        function fetchStatus(forceRefresh) {
            const url = forceRefresh ? '/api/status?refresh=1' : '/api/status';
            fetch(url)
                .then(r => r.json())
                .then(data => {
                    if (data.deviceName) document.getElementById('pill-device').innerText = '📱 ' + data.deviceName;
                    if (data.battery) document.getElementById('pill-battery').innerText = '🔋 Battery: ' + data.battery + '%';
                    if (data.storageFree) document.getElementById('pill-storage').innerText = '💾 Free: ' + data.storageFree + ' / ' + data.storageTotal;
                    if (data.counts) {
                        ['PHOTO', 'VIDEO', 'MUSIC', 'APP', 'DOCUMENT', 'RECEIVED'].forEach(cat => {
                            const el = document.getElementById('badge-' + cat);
                            if (el && typeof data.counts[cat] === 'number') {
                                el.innerText = '(' + data.counts[cat] + ')';
                            }
                        });
                    }
                })
                .catch(err => console.error(err));
        }

        function switchCategory(cat, btn) {
            currentCategory = cat;
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            if (btn) btn.classList.add('active');

            const thFileName = document.getElementById('th-filename');
            if (thFileName) {
                thFileName.innerText = (cat === 'APP') ? 'File Name / Package Name' : 'File Name';
            }

            const btnPickPhone = document.getElementById('btn-pick-phone');
            if (btnPickPhone) {
                btnPickPhone.style.display = (cat === 'APP') ? 'none' : '';
            }

            const thUninstall = document.getElementById('th-uninstall');
            if (thUninstall) {
                thUninstall.style.display = (cat === 'APP') ? '' : 'none';
            }

            const uploadSection = document.getElementById('section-upload');
            const filesSection = document.getElementById('section-files');

            if (cat === 'UPLOAD') {
                uploadSection.style.display = 'block';
                filesSection.style.display = 'none';
            } else {
                uploadSection.style.display = 'none';
                filesSection.style.display = 'block';
                loadFiles(cat, false);
            }
        }

        function loadFiles(cat, forceRefresh) {
            const tbody = document.getElementById('files-tbody');
            const colspan = 5;
            tbody.innerHTML = '<tr><td colspan="' + colspan + '" class="empty-state"><span class="empty-icon">⏳</span>Scanning files on phone...</td></tr>';

            const url = '/api/files?category=' + cat + (forceRefresh ? '&refresh=1' : '');
            fetch(url)
                .then(r => r.json())
                .then(data => {
                    allFiles = data || [];
                    const badgeEl = document.getElementById('badge-' + cat);
                    if (badgeEl) badgeEl.innerText = '(' + allFiles.length + ')';
                    renderFiles(allFiles);
                    if (forceRefresh) fetchStatus(false);
                })
                .catch(err => {
                    tbody.innerHTML = '<tr><td colspan="' + colspan + '" class="empty-state"><span class="empty-icon">⚠️</span>Unable to load files: ' + err + '</td></tr>';
                });
        }

        function requestPhonePicker(cat) {
            showToast('📱 Opening file selector on phone... Please pick files on your phone screen!');
            fetch('/api/trigger-picker?category=' + encodeURIComponent(cat || currentCategory))
                .then(r => r.json())
                .then(() => {
                    if (pickerPollTimer) clearInterval(pickerPollTimer);
                    let attempts = 0;
                    const prevCount = allFiles.length;
                    pickerPollTimer = setInterval(() => {
                        attempts++;
                        fetch('/api/files?category=' + currentCategory + '&refresh=1')
                            .then(r => r.json())
                            .then(data => {
                                if (data && (data.length !== prevCount || attempts >= 10)) {
                                    allFiles = data;
                                    const badgeEl = document.getElementById('badge-' + currentCategory);
                                    if (badgeEl) badgeEl.innerText = '(' + allFiles.length + ')';
                                    renderFiles(allFiles);
                                    fetchStatus(false);
                                    if (data.length !== prevCount) {
                                        clearInterval(pickerPollTimer);
                                        pickerPollTimer = null;
                                        showToast('✅ Updated ' + data.length + ' files from phone!');
                                    }
                                }
                            })
                            .catch(() => {});
                        if (attempts >= 12) {
                            clearInterval(pickerPollTimer);
                            pickerPollTimer = null;
                        }
                    }, 2500);
                })
                .catch(err => showToast('Failed to trigger phone picker: ' + err));
        }

        function renderFiles(files) {
            const tbody = document.getElementById('files-tbody');
            const isApp = (currentCategory === 'APP');
            const colspan = 5;
            if (!files || files.length === 0) {
                const pickBtnHtml = (currentCategory !== 'APP')
                    ? '<div style="margin-top:14px;"><button class="btn-action primary" style="margin:0 auto;" onclick="requestPhonePicker(currentCategory)">📱 Select ' + currentCategory + ' Files on Phone</button></div>'
                    : '';
                tbody.innerHTML = '<tr><td colspan="' + colspan + '" class="empty-state"><span class="empty-icon">📂</span>No files found in this category' + pickBtnHtml + '</td></tr>';
                return;
            }

            let html = '';
            files.forEach((f, idx) => {
                const icon = getCategoryIcon(currentCategory, f.name);
                const isPhoto = (f.isPhoto || currentCategory === 'PHOTO' || f.name.match(/\.(jpg|jpeg|png|webp|gif|bmp)$/i));
                const previewTarget = escapeHtml(f.previewUrl || f.downloadUrl || '');
                const clickAttr = isPhoto ? ' onclick="openLightbox(\'' + previewTarget + '\')" style="cursor:zoom-in;"' : '';
                const safeName = escapeHtml(f.name);
                const jsSafeName = safeName.replace(/'/g, "\\'");
                const rawPkg = (f.packageName || '').trim();
                const safePkg = escapeHtml(rawPkg);
                const jsSafePkg = rawPkg.replace(/'/g, "\\'");

                let thumbUrl = '';
                if (isApp && rawPkg.length > 0) {
                    thumbUrl = '/api/thumbnail?category=APP&package=' + encodeURIComponent(rawPkg) + '&name=' + encodeURIComponent(f.name);
                } else if (isApp) {
                    thumbUrl = '/api/thumbnail?category=APP&name=' + encodeURIComponent(f.name) + '&id=' + f.id;
                } else {
                    const vParam = (f.dateModified || f.size || f.id);
                    thumbUrl = '/api/thumbnail?category=' + currentCategory + '&id=' + f.id + '&name=' + encodeURIComponent(f.name) + '&v=' + vParam;
                }
                const thumb = f.isImage ? '<img class="file-thumb" src="' + thumbUrl + '"' + clickAttr + ' loading="lazy" onerror="this.outerHTML=\'<div class=\\\'file-thumb\\\'>' + icon + '</div>\'">' : '<div class="file-thumb">' + icon + '</div>';

                // Package Name: Bỏ icon trước tên Packet, thêm Icon copy sau tên packet
                let pkgHtml = '';
                if (rawPkg.length > 0) {
                    pkgHtml = '<div class="file-pkg-badge" title="Package: ' + safePkg + '">' +
                        '<span class="pkg-name-text">' + safePkg + '</span>' +
                        '<button type="button" class="btn-copy-pkg" onclick="copyPackageName(event, \'' + jsSafePkg + '\', this)" title="Copy package name">' +
                            '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">' +
                                '<rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>' +
                                '<path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>' +
                            '</svg>' +
                        '</button>' +
                    '</div>';
                }

                html += '<tr>' +
                    '<td><input type="checkbox" class="file-check" value="' + f.id + '"></td>' +
                    '<td><div class="file-name-cell">' + thumb + 
                        '<div class="file-details">' +
                            '<span class="file-title" title="' + safeName + '">' + safeName + '</span>' +
                            pkgHtml +
                        '</div>' +
                    '</div></td>' +
                    '<td>' + f.sizeStr + '</td>' +
                    '<td style="color:var(--text-sub);">' + f.date + '</td>' +
                    '<td style="text-align: right;"><a class="btn-download" href="' + f.downloadUrl + '" download="' + safeName + '" onclick="onFileDownloadClick(event, \'' + jsSafeName + '\')">⬇️ Download</a></td>' +
                '</tr>';
            });
            tbody.innerHTML = html;
        }

        function filterFiles() {
            const query = (document.getElementById('search-input').value || '').toLowerCase().trim();
            if (!query) {
                renderFiles(allFiles);
                return;
            }
            const filtered = allFiles.filter(f => 
                (f.name && f.name.toLowerCase().includes(query)) ||
                (f.packageName && f.packageName.toLowerCase().includes(query))
            );
            renderFiles(filtered);
        }

        function getCategoryIcon(cat, name) {
            if (cat === 'PHOTO') return '🖼️';
            if (cat === 'VIDEO') return '🎬';
            if (cat === 'MUSIC') return '🎵';
            if (cat === 'APP') return '📱';
            if (name.endsWith('.pdf')) return '📕';
            if (name.endsWith('.docx') || name.endsWith('.doc')) return '📘';
            if (name.endsWith('.xlsx') || name.endsWith('.xls')) return '📗';
            if (name.endsWith('.zip') || name.endsWith('.rar')) return '🗜️';
            return '📄';
        }

        function downloadAllZip() {
            showToast('Preparing ZIP download for ' + currentCategory + '...');
            const a = document.createElement('a');
            a.href = '/api/download-all?category=' + currentCategory;
            a.download = 'ShareNow_' + currentCategory + '.zip';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            const notice = document.getElementById('chrome-notice');
            if (notice) {
                notice.classList.add('pulse-highlight');
                setTimeout(() => notice.classList.remove('pulse-highlight'), 3000);
            }
        }

        function onFileDownloadClick(e, fileName) {
            showToast('Starting download: ' + fileName);
            if (fileName.toLowerCase().endsWith('.apk') || fileName.toLowerCase().endsWith('.zip')) {
                const notice = document.getElementById('chrome-notice');
                if (notice) {
                    notice.classList.add('pulse-highlight');
                    setTimeout(() => notice.classList.remove('pulse-highlight'), 3000);
                }
            }
        }

        function toggleSelectAll(master) {
            document.querySelectorAll('.file-check').forEach(cb => cb.checked = master.checked);
        }

        // DRAG & DROP UPLOAD
        function setupDragAndDrop() {
            const dropZone = document.getElementById('drop-zone');
            ['dragenter', 'dragover'].forEach(eventName => {
                dropZone.addEventListener(eventName, e => {
                    e.preventDefault();
                    dropZone.classList.add('dragover');
                }, false);
            });

            ['dragleave', 'drop'].forEach(eventName => {
                dropZone.addEventListener(eventName, e => {
                    e.preventDefault();
                    dropZone.classList.remove('dragover');
                }, false);
            });

            dropZone.addEventListener('drop', e => {
                const files = e.dataTransfer.files;
                if (files && files.length > 0) {
                    handleFileSelect(files);
                }
            });
        }

        function handleFileSelect(files) {
            const queue = document.getElementById('upload-queue');
            for (let i = 0; i < files.length; i++) {
                uploadSingleFile(files[i], queue);
            }
        }

        function uploadSingleFile(file, queueContainer) {
            const item = document.createElement('div');
            item.className = 'upload-item';
            item.innerHTML = '<div class="upload-header"><span><b>' + escapeHtml(file.name) + '</b> (' + formatSize(file.size) + ')</span><span class="status-text">Preparing...</span></div><div class="progress-track"><div class="progress-bar"></div></div>';
            queueContainer.prepend(item);

            const progressBar = item.querySelector('.progress-bar');
            const statusText = item.querySelector('.status-text');

            const xhr = new XMLHttpRequest();
            xhr.open('POST', '/api/upload?filename=' + encodeURIComponent(file.name), true);

            xhr.upload.onprogress = function(e) {
                if (e.lengthComputable) {
                    const percent = Math.round((e.loaded / e.total) * 100);
                    progressBar.style.width = percent + '%';
                    statusText.innerText = 'Uploading ' + percent + '%';
                }
            };

            xhr.onload = function() {
                if (xhr.status === 200) {
                    progressBar.style.width = '100%';
                    progressBar.style.background = '#10b981';
                    statusText.innerText = '✅ Saved to phone';
                    showToast('Successfully received: ' + file.name);
                    fetchStatus();
                } else {
                    statusText.innerText = '❌ Upload failed (' + xhr.status + ')';
                }
            };

            xhr.onerror = function() {
                statusText.innerText = '❌ Connection failed';
            };

            xhr.send(file);
        }

        function copyPackageName(e, pkgName, btn) {
            e.stopPropagation();
            if (!pkgName) return;

            const copySuccess = () => {
                showToast('Copied package: ' + pkgName);
                if (btn) {
                    btn.classList.add('copied');
                    btn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>';
                    setTimeout(() => {
                        btn.classList.remove('copied');
                        btn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>';
                    }, 1800);
                }
            };

            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(pkgName).then(copySuccess).catch(() => fallbackCopy(pkgName, copySuccess));
            } else {
                fallbackCopy(pkgName, copySuccess);
            }
        }

        function fallbackCopy(text, onSuccess) {
            const ta = document.createElement('textarea');
            ta.value = text;
            ta.style.position = 'fixed';
            ta.style.top = '-9999px';
            ta.style.opacity = '0';
            document.body.appendChild(ta);
            ta.focus();
            ta.select();
            try {
                const successful = document.execCommand('copy');
                if (successful && onSuccess) onSuccess();
                else showToast('Copied package: ' + text);
            } catch (err) {
                showToast('Failed to copy: ' + err);
            }
            document.body.removeChild(ta);
        }



        function formatSize(bytes) {
            if (bytes <= 0) return '0 B';
            const mb = bytes / (1024 * 1024);
            if (mb >= 1024) return (mb / 1024).toFixed(2) + ' GB';
            if (mb >= 1) return mb.toFixed(1) + ' MB';
            return Math.round(bytes / 1024) + ' KB';
        }

        function openLightbox(src) {
            document.getElementById('lightbox-img').src = src;
            document.getElementById('lightbox').classList.add('active');
        }

        function closeLightbox() {
            document.getElementById('lightbox').classList.remove('active');
        }

        function showToast(msg) {
            const toast = document.getElementById('toast');
            toast.innerText = msg;
            toast.classList.add('show');
            setTimeout(() => toast.classList.remove('show'), 3500);
        }

        function escapeHtml(str) {
            return (str || '').replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
