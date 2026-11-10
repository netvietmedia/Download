-- ==========================================================================
-- SHARENOW - BỘ TRUY VẤN BIGQUERY PHÂN TÍCH DOANH THU & TỐI ƯU GOOGLE ADS
-- Project ID: share-now-c5a02
-- ==========================================================================

-- 1. Doanh thu AdMob & eCPM theo từng định dạng (Ad Revenue & eCPM by Format)
-- Tính toán Doanh thu AdMob, Lượt hiển thị, Lượt nhấp và eCPM theo từng vị trí
SELECT
    (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'ad_format') AS ad_format,
    (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'ad_unit_name') AS ad_unit_id,
    COUNT(1) AS total_impressions,
    SUM(COALESCE((SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'value_micros'), 0)) / 1000000.0 AS total_revenue_usd,
    SAFE_DIVIDE(
        SUM(COALESCE((SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'value_micros'), 0)) / 1000000.0,
        COUNT(1)
    ) * 1000 AS ecpm_usd
FROM
    `share-now-c5a02.analytics_YOUR_PROPERTY_ID.events_*`
WHERE
    event_name = 'ad_revenue_tracked'
    AND _TABLE_SUFFIX BETWEEN FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY))
    AND FORMAT_DATE('%Y%m%d', CURRENT_DATE())
GROUP BY
    ad_format, ad_unit_id
ORDER BY
    total_revenue_usd DESC;

-- 2. Phân tích Tỷ suất Hoàn vốn (ROAS) Chiến dịch Google Ads
-- Đánh giá hiệu quả từng chiến dịch Google Ads dựa trên doanh thu tổng hợp (Ads + IAP)
WITH UserAcquisition AS (
    SELECT
        user_pseudo_id,
        traffic_source.name AS campaign_name,
        traffic_source.source AS source,
        traffic_source.medium AS medium,
        MIN(TIMESTAMP_MICROS(event_timestamp)) AS acquired_at
    FROM
        `share-now-c5a02.analytics_YOUR_PROPERTY_ID.events_*`
    GROUP BY
        user_pseudo_id, campaign_name, source, medium
),
UserRevenue AS (
    SELECT
        user_pseudo_id,
        SUM(CASE WHEN event_name = 'ad_revenue_tracked' THEN (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'value_micros') / 1000000.0 ELSE 0 END) AS ad_revenue,
        SUM(CASE WHEN event_name IN ('in_app_purchased', 'inapp_purchased') THEN event_value_in_usd ELSE 0 END) AS iap_revenue
    FROM
        `share-now-c5a02.analytics_YOUR_PROPERTY_ID.events_*`
    GROUP BY
        user_pseudo_id
)
SELECT
    ua.campaign_name,
    COUNT(DISTINCT ua.user_pseudo_id) AS acquired_users,
    SUM(ur.ad_revenue) AS total_admob_revenue,
    SUM(ur.iap_revenue) AS total_iap_revenue,
    SUM(ur.ad_revenue + ur.iap_revenue) AS total_combined_revenue,
    SAFE_DIVIDE(SUM(ur.ad_revenue + ur.iap_revenue), COUNT(DISTINCT ua.user_pseudo_id)) AS avg_ltv_per_user
FROM
    UserAcquisition ua
LEFT JOIN
    UserRevenue ur ON ua.user_pseudo_id = ur.user_pseudo_id
WHERE
    ua.campaign_name IS NOT NULL AND ua.campaign_name != '(direct)'
GROUP BY
    ua.campaign_name
ORDER BY
    total_combined_revenue DESC;

-- 3. Phễu chuyển đổi tính năng truyền tệp P2P (Core Transfer Funnel)
-- Phễu chuyển đổi: Mở app -> Bấm Send/Receive -> Kết nối P2P -> Truyền tệp thành công
SELECT
    COUNT(DISTINCT CASE WHEN event_name = 'first_open' THEN user_pseudo_id END) AS step1_first_open,
    COUNT(DISTINCT CASE WHEN event_name IN ('click_card_send', 'transfer_send_click') THEN user_pseudo_id END) AS step2_click_send,
    COUNT(DISTINCT CASE WHEN event_name IN ('click_card_receive', 'transfer_receive_click') THEN user_pseudo_id END) AS step2_click_receive,
    COUNT(DISTINCT CASE WHEN event_name = 'p2p_connected' THEN user_pseudo_id END) AS step3_p2p_connected,
    COUNT(DISTINCT CASE WHEN event_name = 'transfer_file_success' THEN user_pseudo_id END) AS step4_file_transferred,
    COUNT(DISTINCT CASE WHEN event_name = 'clean_junk_executed' THEN user_pseudo_id END) AS bonus_cleanit_users,
    COUNT(DISTINCT CASE WHEN event_name = 'in_app_purchased' THEN user_pseudo_id END) AS converted_vip_users
FROM
    `share-now-c5a02.analytics_YOUR_PROPERTY_ID.events_*`
WHERE
    _TABLE_SUFFIX BETWEEN FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY))
    AND FORMAT_DATE('%Y%m%d', CURRENT_DATE());

