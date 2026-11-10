#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ShareNow GA4 / Firebase Data Puller
===================================
Tự động kết nối Google Analytics 4 (GA4) qua Google Analytics Data API,
kéo toàn bộ sự kiện Firebase, AdMob revenue, chiến dịch Google Ads, IAP
và báo cáo người dùng về máy để phân tích và tối ưu hóa doanh thu.
"""

import os
import sys
import json
import csv
from pathlib import Path
from datetime import datetime

# Đảm bảo in tiếng Việt UTF-8 chuẩn trên Windows PowerShell / CMD
try:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

from google.oauth2 import service_account
from google.analytics.data_v1beta import BetaAnalyticsDataClient
from google.analytics.data_v1beta.types import (
    DateRange,
    Dimension,
    Metric,
    RunReportRequest,
    OrderBy,
)

CURRENT_DIR = Path(__file__).parent.resolve()
CONFIG_FILE = CURRENT_DIR / "config.json"

def load_config():
    if not CONFIG_FILE.exists():
        raise FileNotFoundError(f"Không tìm thấy tệp cấu hình tại: {CONFIG_FILE}")
    with open(CONFIG_FILE, "r", encoding="utf-8") as f:
        return json.load(f)

def get_credentials(config):
    sa_path = CURRENT_DIR / config.get("service_account_file", "service_account.json")
    if not sa_path.exists():
        json_files = list(CURRENT_DIR.glob("*.json"))
        sa_candidates = [f for f in json_files if f.name != "config.json"]
        if sa_candidates:
            sa_path = sa_candidates[0]
            print(f"[*] Tự động phát hiện Service Account Key: {sa_path.name}")
        else:
            raise FileNotFoundError(
                f"Không tìm thấy Service Account JSON trong thư mục {CURRENT_DIR}!\n"
                f"Vui lòng tạo Service Account và tải key về thư mục này."
            )
    return service_account.Credentials.from_service_account_file(
        str(sa_path),
        scopes=["https://www.googleapis.com/auth/analytics.readonly"]
    )

def save_to_csv(rows, headers, file_path):
    file_path.parent.mkdir(parents=True, exist_ok=True)
    with open(file_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(headers)
        writer.writerows(rows)
    print(f"[✓] Đã xuất: {file_path} ({len(rows)} dòng)")

def fetch_report(client, property_id, dimensions, metrics, date_range, order_by_metric=None, limit=1000):
    dim_objs = [Dimension(name=d) for d in dimensions]
    metric_objs = [Metric(name=m) for m in metrics]
    date_ranges = [DateRange(start_date=date_range.get("start_date", "30daysAgo"), end_date=date_range.get("end_date", "today"))]
    
    request_kwargs = {
        "property": f"properties/{property_id}",
        "dimensions": dim_objs,
        "metrics": metric_objs,
        "date_ranges": date_ranges,
        "limit": limit
    }
    
    if order_by_metric:
        request_kwargs["order_bys"] = [
            OrderBy(metric=OrderBy.MetricOrderBy(metric_name=order_by_metric), desc=True)
        ]
        
    req = RunReportRequest(**request_kwargs)
    response = client.run_report(req)
    
    headers = dimensions + metrics
    rows = []
    for row in response.rows:
        dim_values = [val.value for val in row.dimension_values]
        metric_values = [val.value for val in row.metric_values]
        rows.append(dim_values + metric_values)
        
    return headers, rows

def main():
    print("=" * 70)
    print(" SHARE NOW - BỘ CÔNG CỤ TRÍCH XUẤT DỮ LIỆU GA4 / FIREBASE ANALYTICS")
    print("=" * 70)
    
    config = load_config()
    creds = get_credentials(config)
    output_dir = CURRENT_DIR / config.get("output_directory", "./output")
    output_dir.mkdir(parents=True, exist_ok=True)
    
    prop_id = config.get("property_id", "558520028").strip()
    print(f"[+] Sử dụng GA4 Property ID: {prop_id}")
    client = BetaAnalyticsDataClient(credentials=creds)
    date_range = config.get("date_ranges", {"start_date": "30daysAgo", "end_date": "today"})
    
    collected_summary = {
        "property_id": prop_id,
        "timestamp": datetime.now().isoformat(),
        "date_range": date_range,
        "reports": {}
    }

    try:
        # 1. Toàn bộ Sự Kiện (Events Summary)
        print("\n[1/6] Đang kéo báo cáo toàn bộ sự kiện (Events Summary)...")
        h1, r1 = fetch_report(
            client, prop_id,
            dimensions=["eventName"],
            metrics=["eventCount", "totalUsers", "eventValue"],
            date_range=date_range,
            order_by_metric="eventCount"
        )
        save_to_csv(r1, h1, output_dir / "events_summary.csv")
        collected_summary["reports"]["events_count"] = len(r1)
        
        # 2. Doanh thu AdMob theo Định dạng & Mạng đối tác
        print("\n[2/6] Đang kéo báo cáo doanh thu quảng cáo AdMob (Ad Revenue & Mediation Partners)...")
        try:
            h2, r2 = fetch_report(
                client, prop_id,
                dimensions=["adFormat", "adUnitName", "adSourceName"],
                metrics=["totalAdRevenue", "publisherAdImpressions", "publisherAdClicks"],
                date_range=date_range,
                order_by_metric="totalAdRevenue"
            )
            save_to_csv(r2, h2, output_dir / "admob_revenue.csv")
            collected_summary["reports"]["admob_rows"] = len(r2)
        except Exception as e:
            print(f"[!] Báo cáo AdMob: {e}")
            
        # 3. Nguồn lưu lượng & Chiến dịch (Traffic Source & Campaigns)
        print("\n[3/6] Đang kéo báo cáo nguồn người dùng & chiến dịch (Acquisition & Campaigns)...")
        try:
            h3, r3 = fetch_report(
                client, prop_id,
                dimensions=["sessionSourceMedium", "sessionCampaignName"],
                metrics=["sessions", "totalUsers", "conversions"],
                date_range=date_range,
                order_by_metric="sessions"
            )
            save_to_csv(r3, h3, output_dir / "traffic_campaigns.csv")
            collected_summary["reports"]["campaigns_count"] = len(r3)
        except Exception as e:
            print(f"[!] Báo cáo Nguồn & Chiến dịch: {e}")

        # 4. Sự kiện Thanh toán & Mua gói (In-App Purchases / VIP)
        print("\n[4/6] Đang kéo báo cáo mua VIP / Thanh toán (In-App Purchases & Monetization)...")
        try:
            # Lọc các sự kiện liên quan monetization
            h4, r4 = fetch_report(
                client, prop_id,
                dimensions=["eventName"],
                metrics=["eventCount", "totalUsers", "eventValue"],
                date_range=date_range,
                order_by_metric="eventCount"
            )
            iap_events = [row for row in r4 if any(k in row[0].lower() for k in ["purchase", "paywall", "in_app", "inapp", "subscribe"])]
            save_to_csv(iap_events, h4, output_dir / "in_app_purchases.csv")
            collected_summary["reports"]["iap_events_count"] = len(iap_events)
        except Exception as e:
            print(f"[!] Báo cáo IAP: {e}")

        # 5. Phễu người dùng theo ngày (Daily User Funnel)
        print("\n[5/6] Đang kéo báo cáo tiến trình theo ngày (Daily Events & Trends)...")
        h5, r5 = fetch_report(
            client, prop_id,
            dimensions=["date", "eventName"],
            metrics=["eventCount"],
            date_range=date_range,
            order_by_metric="eventCount"
        )
        save_to_csv(r5, h5, output_dir / "user_funnel_daily.csv")
        collected_summary["reports"]["daily_rows"] = len(r5)

        # 6. Địa lý & Thiết bị (Demographics: Country & Devices)
        print("\n[6/6] Đang kéo báo cáo phân bổ quốc gia (Country & Ad Revenue)...")
        try:
            h6, r6 = fetch_report(
                client, prop_id,
                dimensions=["country"],
                metrics=["totalUsers", "eventCount", "totalAdRevenue"],
                date_range=date_range,
                order_by_metric="totalUsers"
            )
            save_to_csv(r6, h6, output_dir / "user_demographics.csv")
            collected_summary["reports"]["countries_count"] = len(r6)
        except Exception as e:
            print(f"[!] Báo cáo Country: {e}")

        # Lưu file tổng hợp JSON
        with open(output_dir / "ga4_complete_export.json", "w", encoding="utf-8") as f:
            json.dump(collected_summary, f, indent=2, ensure_ascii=False)
            
        print("\n[✓] QUÁ TRÌNH KÉO DỮ LIỆU TỪ GA4 / FIREBASE HOÀN TẤT THÀNH CÔNG (6/6 BÁO CÁO)!")
        print(f"[✓] Toàn bộ file đã được lưu tại: {output_dir.resolve()}")
        
    except Exception as e:
        print(f"\n[!] Lỗi khi truy vấn GA4 Data API: {e}")

if __name__ == "__main__":
    main()
