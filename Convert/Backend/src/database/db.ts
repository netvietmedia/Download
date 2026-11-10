import fs from 'fs';
import path from 'path';

export interface Device {
  id: string;
  device_uuid: string;
  public_key?: string;
  os_type: 'android' | 'ios' | 'windows' | 'macos';
  os_version: number;
  app_version: string;
  app_version_code: number;
  device_model: string;
  manufacturer: string;
  language: string;
  country_code: string;
  gaid_idfv?: string;
  fcm_token?: string;
  is_banned: boolean;
  first_seen_at: string;
  last_active_at: string;
}

export interface User {
  id: string;
  display_name: string;
  avatar_url: string;
  email?: string;
  phone?: string;
  is_vip: boolean;
  vip_expire_at?: string;
  coin_balance: number;
  status: 'active' | 'suspended' | 'deleted';
  created_at: string;
  updated_at: string;
}

export interface MCDSSpace {
  id: string;
  space_id: string;
  name: string;
  description: string;
  is_active: boolean;
  priority: number;
}

export interface MCDSPromote {
  id: string;
  space_id: string;
  title: string;
  content_type: 'image' | 'lottie' | 'h5' | 'native_ad';
  media_url: string;
  action_type: 'deeplink' | 'external_url' | 'open_tab';
  action_value: string;
  targeting_rules?: {
    countries?: string[];
    min_version?: number;
  };
  frequency_capping?: {
    max_impressions_per_day: number;
    interval_sec: number;
  };
  priority: number;
  is_active: boolean;
}

export interface FamilyProduct {
  id: string;
  app_name: string;
  package_name: string;
  icon_url: string;
  download_url: string;
  category: string;
  badge: 'HOT' | 'NEW' | 'RECOMMENDED' | 'POPULAR';
  rating: number;
  download_count_str: string;
  description: string;
  cpi_payout: number;
  priority: number;
  is_active: boolean;
}

export interface AdGlobalKeys {
  admob_app_id: string;
  applovin_sdk_key: string;
  mintegral_app_id: string;
  mintegral_app_key: string;
  unity_game_id: string;
  pangle_app_id: string;
  ironsource_app_key: string;
  test_mode: boolean;
}

export interface AdPlacement {
  id: string;
  placement_id: string;
  placement_type: 'banner' | 'interstitial' | 'rewarded' | 'native' | 'app_open';
  mediation_waterfall: Array<{
    network: string;
    unit_id: string;
    ecpm_floor: number;
    timeout_ms: number;
    is_active?: boolean;
  }>;
  country_overrides?: Record<string, any>;
  is_active: boolean;
}

export interface SubscriptionPlan {
  id: string;
  title: string;
  price: number;
  currency: string;
  duration_days: number;
  google_product_id: string;
  apple_product_id: string;
  features: string[];
  is_active: boolean;
}

export interface PaymentOrder {
  id: string;
  order_code: string;
  user_id?: string;
  device_uuid?: string;
  plan_id: string;
  gateway: string;
  transaction_id: string;
  purchase_token: string;
  amount: number;
  currency: string;
  status: 'pending' | 'paid' | 'failed' | 'refunded';
  created_at: string;
}

export interface CoinTransaction {
  id: string;
  transaction_id: string;
  user_id: string;
  device_uuid: string;
  event_type: string;
  amount: number;
  balance_after: number;
  metadata?: any;
  created_at: string;
}

export interface CoinTask {
  id: string;
  title: string;
  description: string;
  reward_coins: number;
  category: 'daily' | 'transfer' | 'ads' | 'system' | 'social';
  icon_url: string;
  action_deeplink: string;
  daily_limit: number;
  is_active: boolean;
}

export interface CoinRewardItem {
  id: string;
  title: string;
  category: 'vip_pass' | 'telco_card' | 'voucher' | 'game_card' | 'cash';
  cost_coins: number;
  stock: number;
  icon_url: string;
  description: string;
  payout_value_str: string;
  is_active: boolean;
}

export interface CoinRedemption {
  id: string;
  request_code: string;
  user_id: string;
  device_uuid: string;
  reward_id: string;
  reward_title: string;
  coins_spent: number;
  payout_target: string;
  status: 'pending' | 'approved' | 'rejected' | 'completed';
  admin_note?: string;
  created_at: string;
  processed_at?: string;
}

export interface TelemetryEvent {
  id: string;
  event_name: string;
  device_uuid: string;
  timestamp: number;
  properties: Record<string, any>;
  created_at: string;
}

export interface AuditLog {
  id: string;
  admin_user: string;
  action: string;
  resource: string;
  details: string;
  ip_address: string;
  created_at: string;
}

export interface AdminUser {
  id: string;
  username: string;
  password?: string;
  display_name: string;
  email: string;
  role: 'super_admin' | 'admin' | 'moderator' | 'operator';
  avatar_url?: string;
  is_active: boolean;
  created_at: string;
  last_login_at?: string;
}

interface DBStructure {
  admin_users: AdminUser[];
  devices: Device[];
  users: User[];
  mcds_spaces: MCDSSpace[];
  mcds_promotes: MCDSPromote[];
  family_products: FamilyProduct[];
  ad_global_keys: AdGlobalKeys;
  ad_placements: AdPlacement[];
  subscription_plans: SubscriptionPlan[];
  payment_orders: PaymentOrder[];
  coin_ledger: CoinTransaction[];
  coin_tasks: CoinTask[];
  coin_rewards: CoinRewardItem[];
  coin_redemptions: CoinRedemption[];
  telemetry_events: TelemetryEvent[];
  audit_logs: AuditLog[];
}

class DatabaseManager {
  private dbPath: string;
  private data: DBStructure;

  constructor() {
    this.dbPath = path.join(process.cwd(), 'data_storage.json');
    this.data = this.loadData();
  }

  private loadData(): DBStructure {
    if (fs.existsSync(this.dbPath)) {
      try {
        const raw = fs.readFileSync(this.dbPath, 'utf8');
        const parsed = JSON.parse(raw);
        if (!parsed.admin_users || parsed.admin_users.length === 0) {
          parsed.admin_users = this.getInitialAdminUsers();
        }
        if (!parsed.family_products || parsed.family_products.length === 0) {
          parsed.family_products = this.getInitialFamilyProducts();
        }
        if (!parsed.ad_global_keys) {
          parsed.ad_global_keys = this.getInitialAdGlobalKeys();
        }
        if (!parsed.coin_tasks || parsed.coin_tasks.length === 0) {
          parsed.coin_tasks = this.getInitialSeed().coin_tasks;
        }
        if (!parsed.coin_rewards || parsed.coin_rewards.length === 0) {
          parsed.coin_rewards = this.getInitialCoinRewards();
        }
        if (!parsed.coin_redemptions || parsed.coin_redemptions.length === 0) {
          parsed.coin_redemptions = this.getInitialCoinRedemptions();
        }
        return parsed;
      } catch (e) {
        console.error('Error reading db file, re-initializing default data:', e);
      }
    }
    return this.getInitialSeed();
  }

  private getInitialAdminUsers(): AdminUser[] {
    return [
      {
        id: 'admin_01',
        username: 'admin',
        password: 'admin@123456',
        display_name: 'Super Administrator',
        email: 'superadmin@shareit.io',
        role: 'super_admin',
        avatar_url: 'https://cdn-icons-png.flaticon.com/512/3135/3135715.png',
        is_active: true,
        created_at: '2026-09-01T00:00:00.000Z',
        last_login_at: new Date().toISOString()
      },
      {
        id: 'admin_02',
        username: 'marketing_lead',
        password: 'mkt@123456',
        display_name: 'Trưởng Nhóm Marketing',
        email: 'marketing@shareit.io',
        role: 'admin',
        avatar_url: 'https://cdn-icons-png.flaticon.com/512/3135/3135768.png',
        is_active: true,
        created_at: '2026-09-02T00:00:00.000Z',
        last_login_at: '2026-09-04T10:30:00.000Z'
      },
      {
        id: 'admin_03',
        username: 'support_ops',
        password: 'ops@123456',
        display_name: 'Hỗ Trợ & Vận Hành',
        email: 'ops@shareit.io',
        role: 'operator',
        avatar_url: 'https://cdn-icons-png.flaticon.com/512/3135/3135823.png',
        is_active: true,
        created_at: '2026-09-03T00:00:00.000Z',
        last_login_at: '2026-09-05T08:15:00.000Z'
      }
    ];
  }

  private getInitialAdGlobalKeys(): AdGlobalKeys {
    return {
      admob_app_id: 'ca-app-pub-3940256099942544~3347511713',
      applovin_sdk_key: 'YOUR_APPLOVIN_SDK_KEY_HERE',
      mintegral_app_id: 'YOUR_MINTEGRAL_APP_ID_HERE',
      mintegral_app_key: 'YOUR_MINTEGRAL_APP_KEY_HERE',
      unity_game_id: 'YOUR_UNITY_GAME_ID_HERE',
      pangle_app_id: 'YOUR_PANGLE_APP_ID_HERE',
      ironsource_app_key: 'YOUR_IRONSOURCE_APP_KEY_HERE',
      test_mode: true
    };
  }

  private getInitialCoinRewards(): CoinRewardItem[] {
    return [
      {
        id: 'rew_vip_1d',
        title: 'VIP 1 Ngày Không Quảng Cáo',
        category: 'vip_pass',
        cost_coins: 500,
        stock: 9999,
        icon_url: 'https://cdn-icons-png.flaticon.com/512/6941/6941697.png',
        description: 'Tận hưởng 24h truyền file tốc độ tối đa 200MB/s không bị làm phiền.',
        payout_value_str: '1 Ngày VIP',
        is_active: true
      },
      {
        id: 'rew_vip_7d',
        title: 'VIP 7 Ngày Cao Cấp',
        category: 'vip_pass',
        cost_coins: 2500,
        stock: 9999,
        icon_url: 'https://cdn-icons-png.flaticon.com/512/2589/2589175.png',
        description: 'Gói 1 tuần miễn phí quảng cáo, mở khóa Két Safebox 50GB.',
        payout_value_str: '7 Ngày VIP',
        is_active: true
      },
      {
        id: 'rew_card_viettel_50k',
        title: 'Thẻ Cào Viettel 50.000đ',
        category: 'telco_card',
        cost_coins: 50000,
        stock: 50,
        icon_url: 'https://cdn-icons-png.flaticon.com/512/9639/9639736.png',
        description: 'Mã thẻ cào Viettel 50K gửi trực tiếp qua thông báo và SMS.',
        payout_value_str: '50.000 VNĐ',
        is_active: true
      },
      {
        id: 'rew_voucher_shopee_20k',
        title: 'Voucher Mua Sắm Shopee 20K',
        category: 'voucher',
        cost_coins: 20000,
        stock: 100,
        icon_url: 'https://cdn-icons-png.flaticon.com/512/833/833314.png',
        description: 'Mã giảm giá áp dụng cho đơn hàng bất kỳ trên sàn thương mại điện tử.',
        payout_value_str: '20.000 VNĐ',
        is_active: true
      },
      {
        id: 'rew_game_garena_50k',
        title: 'Thẻ Nạp Game Garena 50K',
        category: 'game_card',
        cost_coins: 50000,
        stock: 30,
        icon_url: 'https://cdn-icons-png.flaticon.com/512/1068/1068778.png',
        description: 'Mã sò nạp Liên Quân Mobile, Free Fire, FC Online.',
        payout_value_str: '100 Quân Huy / 50K',
        is_active: true
      }
    ];
  }

  private getInitialCoinRedemptions(): CoinRedemption[] {
    return [
      {
        id: 'red_01',
        request_code: 'RED_20260905_9182',
        user_id: 'usr_01',
        device_uuid: 'dev_8f2910c2_4a9b_412e_a91c_89201f92e811',
        reward_id: 'rew_vip_1d',
        reward_title: 'VIP 1 Ngày Không Quảng Cáo',
        coins_spent: 500,
        payout_target: 'Auto-applied to Device dev_8f29...',
        status: 'completed',
        admin_note: 'Tự động kích hoạt VIP thành công',
        created_at: new Date(Date.now() - 3600000 * 2).toISOString(),
        processed_at: new Date(Date.now() - 3600000 * 2).toISOString()
      },
      {
        id: 'red_02',
        request_code: 'RED_20260905_9401',
        user_id: 'usr_01',
        device_uuid: 'dev_8f2910c2_4a9b_412e_a91c_89201f92e811',
        reward_id: 'rew_card_viettel_50k',
        reward_title: 'Thẻ Cào Viettel 50.000đ',
        coins_spent: 50000,
        payout_target: '0988.123.456 (Viettel)',
        status: 'pending',
        admin_note: 'Chờ admin kiểm tra và gửi mã thẻ cào',
        created_at: new Date(Date.now() - 1800000).toISOString()
      }
    ];
  }

  public save(): void {
    try {
      fs.writeFileSync(this.dbPath, JSON.stringify(this.data, null, 2), 'utf8');
    } catch (e) {
      console.error('Error saving db file:', e);
    }
  }

  private getInitialFamilyProducts(): FamilyProduct[] {
    return [
      {
        id: 'fam_01',
        app_name: 'CLEANit - Dọn Rác Tăng Tốc',
        package_name: 'com.ushareit.cleanit',
        icon_url: 'https://cdn-icons-png.flaticon.com/512/2879/2879209.png',
        download_url: 'https://play.google.com/store/apps/details?id=com.ushareit.cleanit',
        category: 'Tiện ích hệ thống',
        badge: 'HOT',
        rating: 4.8,
        download_count_str: '50M+',
        description: 'Giải phóng đến 10GB dung lượng bộ nhớ đệm và tăng tốc RAM thiết bị.',
        cpi_payout: 0.35,
        priority: 10,
        is_active: true
      },
      {
        id: 'fam_02',
        app_name: 'CLONEit - Sao Chép Đổi Máy',
        package_name: 'com.ushareit.cloneit',
        icon_url: 'https://cdn-icons-png.flaticon.com/512/3281/3281307.png',
        download_url: 'https://play.google.com/store/apps/details?id=com.ushareit.cloneit',
        category: 'Công cụ chuyển đổi',
        badge: 'RECOMMENDED',
        rating: 4.7,
        download_count_str: '20M+',
        description: 'Sao chép 12 loại dữ liệu (Danh bạ, Tin nhắn, Ảnh, App) từ máy cũ sang máy mới.',
        cpi_payout: 0.40,
        priority: 9,
        is_active: true
      },
      {
        id: 'fam_03',
        app_name: 'LOCKit - Khóa Ứng Dụng Vân Tay',
        package_name: 'com.ushareit.lockit',
        icon_url: 'https://cdn-icons-png.flaticon.com/512/807/807241.png',
        download_url: 'https://play.google.com/store/apps/details?id=com.ushareit.lockit',
        category: 'Bảo mật quyền riêng tư',
        badge: 'POPULAR',
        rating: 4.6,
        download_count_str: '15M+',
        description: 'Bảo vệ Messenger, Zalo, Thư viện ảnh bằng mật khẩu và cảm biến vân tay.',
        cpi_payout: 0.25,
        priority: 8,
        is_active: true
      },
      {
        id: 'fam_04',
        app_name: 'LISTENit - Nghe Nhạc Offline',
        package_name: 'com.ushareit.listenit',
        icon_url: 'https://cdn-icons-png.flaticon.com/512/3844/3844724.png',
        download_url: 'https://play.google.com/store/apps/details?id=com.ushareit.listenit',
        category: 'Âm nhạc & Âm thanh',
        badge: 'NEW',
        rating: 4.9,
        download_count_str: '30M+',
        description: 'Bộ cân bằng âm thanh Equalizer chuyên nghiệp, phát nhạc nền không tốn pin.',
        cpi_payout: 0.30,
        priority: 7,
        is_active: true
      },
      {
        id: 'fam_05',
        app_name: 'S-Player - Trình Chiếu Video 4K',
        package_name: 'com.ushareit.siplayer',
        icon_url: 'https://cdn-icons-png.flaticon.com/512/709/709592.png',
        download_url: 'https://play.google.com/store/apps/details?id=com.ushareit.siplayer',
        category: 'Xem phim & Video',
        badge: 'POPULAR',
        rating: 4.7,
        download_count_str: '25M+',
        description: 'Hỗ trợ giải mã MKV, MP4, FLV, AVI và tải phụ đề tự động trực tuyến.',
        cpi_payout: 0.20,
        priority: 6,
        is_active: true
      }
    ];
  }

  private getInitialSeed(): DBStructure {
    return {
      devices: [
        {
          id: 'dev_01',
          device_uuid: 'dev_8f2910c2_4a9b_412e_a91c_89201f92e811',
          os_type: 'android',
          os_version: 34,
          app_version: '6.53.38_UD',
          app_version_code: 4565338,
          device_model: 'Pixel 7 Pro',
          manufacturer: 'Google',
          language: 'vi',
          country_code: 'VN',
          gaid_idfv: '38400000-8cf0-11bd-b23e-10b96e40000d',
          is_banned: false,
          first_seen_at: new Date(Date.now() - 86400000 * 5).toISOString(),
          last_active_at: new Date().toISOString(),
        }
      ],
      users: [
        {
          id: 'usr_01',
          display_name: 'Nguyễn Văn A',
          avatar_url: 'https://api.dicebear.com/7.x/avataaars/svg?seed=user01',
          email: 'user@example.com',
          is_vip: true,
          vip_expire_at: new Date(Date.now() + 86400000 * 365).toISOString(),
          coin_balance: 1450,
          status: 'active',
          created_at: new Date(Date.now() - 86400000 * 30).toISOString(),
          updated_at: new Date().toISOString(),
        }
      ],
      mcds_spaces: [
        {
          id: 'space_01',
          space_id: 'home_top_banner',
          name: 'Home Top Carousel Banner',
          description: 'Banner trượt trên đầu trang chủ ứng dụng',
          is_active: true,
          priority: 10,
        },
        {
          id: 'space_02',
          space_id: 'trans_complete_interstitial',
          name: 'Transfer Complete Space',
          description: 'Vị trí hiển thị sau khi hoàn tất phiên gửi file',
          is_active: true,
          priority: 8,
        },
        {
          id: 'space_03',
          space_id: 'family_product_list',
          name: 'SHAREit Family Apps Matrix',
          description: 'Ma trận ứng dụng gia đình và quảng cáo chéo CPI',
          is_active: true,
          priority: 10,
        }
      ],
      mcds_promotes: [
        {
          id: 'prom_01',
          space_id: 'home_top_banner',
          title: 'Nâng cấp VIP - Trải nghiệm tốc độ 200MB/s không quảng cáo',
          content_type: 'image',
          media_url: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800&auto=format&fit=crop',
          action_type: 'deeplink',
          action_value: 'shareit://subscription/paywall',
          priority: 10,
          frequency_capping: {
            max_impressions_per_day: 5,
            interval_sec: 1800,
          },
          is_active: true,
        },
        {
          id: 'prom_02',
          space_id: 'home_top_banner',
          title: 'Điểm danh 7 ngày liên tục nhận ngay 500 Coin!',
          content_type: 'image',
          media_url: 'https://images.unsplash.com/photo-1579621970563-ebec7560ff3e?w=800&auto=format&fit=crop',
          action_type: 'deeplink',
          action_value: 'shareit://coin/store',
          priority: 8,
          frequency_capping: {
            max_impressions_per_day: 3,
            interval_sec: 3600,
          },
          is_active: true,
        }
      ],
      family_products: this.getInitialFamilyProducts(),
      ad_global_keys: this.getInitialAdGlobalKeys(),
      ad_placements: [
        {
          id: 'ad_01',
          placement_id: 'trans_complete_interstitial',
          placement_type: 'interstitial',
          mediation_waterfall: [
            { network: 'admob', unit_id: 'ca-app-pub-3940256099942544/1033173712', ecpm_floor: 3.50, timeout_ms: 3000, is_active: true },
            { network: 'applovin', unit_id: 'applovin_inter_01', ecpm_floor: 2.80, timeout_ms: 2500, is_active: true },
            { network: 'mintegral', unit_id: 'mintegral_inter_01', ecpm_floor: 2.00, timeout_ms: 2000, is_active: true }
          ],
          is_active: true,
        },
        {
          id: 'ad_02',
          placement_id: 'main_bottom_banner',
          placement_type: 'banner',
          mediation_waterfall: [
            { network: 'admob', unit_id: 'ca-app-pub-3940256099942544/6300978111', ecpm_floor: 0.80, timeout_ms: 2000, is_active: true }
          ],
          is_active: true,
        },
        {
          id: 'ad_03',
          placement_id: 'coin_rewarded_video',
          placement_type: 'rewarded',
          mediation_waterfall: [
            { network: 'admob', unit_id: 'ca-app-pub-3940256099942544/5224354917', ecpm_floor: 5.00, timeout_ms: 3500, is_active: true },
            { network: 'unity', unit_id: 'rewarded_video_placement', ecpm_floor: 4.20, timeout_ms: 3000, is_active: true }
          ],
          is_active: true,
        },
        {
          id: 'ad_04',
          placement_id: 'app_open_splash',
          placement_type: 'app_open',
          mediation_waterfall: [
            { network: 'admob', unit_id: 'ca-app-pub-3940256099942544/9257395921', ecpm_floor: 6.00, timeout_ms: 2000, is_active: true }
          ],
          is_active: true,
        },
        {
          id: 'ad_05',
          placement_id: 'files_native_advanced',
          placement_type: 'native',
          mediation_waterfall: [
            { network: 'admob', unit_id: 'ca-app-pub-3940256099942544/2247696110', ecpm_floor: 1.50, timeout_ms: 2500, is_active: true }
          ],
          is_active: true,
        }
      ],
      subscription_plans: [
        {
          id: 'vip_weekly',
          title: 'Gói VIP Tuần',
          price: 1.99,
          currency: 'USD',
          duration_days: 7,
          google_product_id: 'shareit.vip.weekly',
          apple_product_id: 'com.shareit.sub.weekly',
          features: ['Không quảng cáo 100%', 'Tốc độ truyền tối đa 200MB/s', 'Két Safebox 50GB'],
          is_active: true,
        },
        {
          id: 'vip_monthly',
          title: 'Gói VIP Tháng',
          price: 4.99,
          currency: 'USD',
          duration_days: 30,
          google_product_id: 'shareit.vip.monthly',
          apple_product_id: 'com.shareit.sub.monthly',
          features: ['Mọi quyền gói Tuần', 'Tải video HD không giới hạn', 'Dọn rác tự động'],
          is_active: true,
        },
        {
          id: 'vip_yearly',
          title: 'Gói VIP Năm (Tiết kiệm 50%)',
          price: 24.99,
          currency: 'USD',
          duration_days: 365,
          google_product_id: 'shareit.vip.yearly',
          apple_product_id: 'com.shareit.sub.yearly',
          features: ['Mọi quyền gói Tháng', 'Két an toàn đám mây 200GB', 'Huy hiệu VIP vàng', 'Hỗ trợ ưu tiên 24/7'],
          is_active: true,
        },
        {
          id: 'vip_lifetime',
          title: 'Gói VIP Trọn Đời (Mua 1 lần)',
          price: 49.99,
          currency: 'USD',
          duration_days: 36500,
          google_product_id: 'shareit.vip.lifetime',
          apple_product_id: 'com.shareit.sub.lifetime',
          features: ['Sở hữu vĩnh viễn mọi tính năng cao cấp', 'Áp dụng cho mọi thiết bị đăng nhập'],
          is_active: true,
        }
      ],
      payment_orders: [
        {
          id: 'ord_01',
          order_code: 'ORD_20260905_81923',
          user_id: 'usr_01',
          device_uuid: 'dev_8f2910c2_4a9b_412e_a91c_89201f92e811',
          plan_id: 'vip_yearly',
          gateway: 'google_play',
          transaction_id: 'GPA.3312-8819-1123-99123',
          purchase_token: 'token_sample_abc123...',
          amount: 24.99,
          currency: 'USD',
          status: 'paid',
          created_at: new Date().toISOString(),
        }
      ],
      coin_ledger: [
        {
          id: 'tx_01',
          transaction_id: 'tx_coin_20260905_001',
          user_id: 'usr_01',
          device_uuid: 'dev_8f2910c2_4a9b_412e_a91c_89201f92e811',
          event_type: 'daily_checkin',
          amount: 150,
          balance_after: 1450,
          metadata: { day_streak: 3 },
          created_at: new Date().toISOString(),
        }
      ],
      coin_tasks: [
        {
          id: 'task_01',
          title: 'Truyền file siêu tốc qua Wi-Fi Direct',
          description: 'Gửi hoặc nhận bất kỳ 1 tệp tin nào',
          reward_coins: 50,
          category: 'transfer',
          icon_url: 'https://cdn-icons-png.flaticon.com/512/724/724933.png',
          action_deeplink: 'shareit://transfer/send',
          daily_limit: 5,
          is_active: true,
        },
        {
          id: 'task_02',
          title: 'Mời bạn bè cài đặt nhận quà',
          description: 'Nhận ngay 500 Coin khi bạn bè nhập mã mời',
          reward_coins: 500,
          category: 'social',
          icon_url: 'https://cdn-icons-png.flaticon.com/512/1256/1256650.png',
          action_deeplink: 'shareit://me/invite',
          daily_limit: 10,
          is_active: true,
        },
        {
          id: 'task_03',
          title: 'Dọn rác máy tính và tăng tốc RAM',
          description: 'Dọn sạch ít nhất 500MB rác bằng Cleanit',
          reward_coins: 80,
          category: 'system',
          icon_url: 'https://cdn-icons-png.flaticon.com/512/2879/2879209.png',
          action_deeplink: 'shareit://clean/scan',
          daily_limit: 1,
          is_active: true,
        },
        {
          id: 'task_04',
          title: 'Xem video nhận thưởng Rewarded',
          description: 'Xem 1 video ngắn 15s để nhận ngay xu thưởng',
          reward_coins: 30,
          category: 'ads',
          icon_url: 'https://cdn-icons-png.flaticon.com/512/4406/4406319.png',
          action_deeplink: 'shareit://ads/rewarded',
          daily_limit: 10,
          is_active: true,
        }
      ],
      coin_rewards: this.getInitialCoinRewards(),
      coin_redemptions: this.getInitialCoinRedemptions(),
      telemetry_events: [
        {
          id: 'tel_01',
          event_name: 'transfer_file_finish',
          device_uuid: 'dev_8f2910c2_4a9b_412e_a91c_89201f92e811',
          timestamp: Date.now() - 3600000,
          properties: {
            file_type: 'video/mp4',
            file_size_bytes: 104857600,
            transfer_mode: 'wifi_direct',
            duration_ms: 3200,
            speed_mbps: 262.1,
            status: 'success'
          },
          created_at: new Date(Date.now() - 3600000).toISOString(),
        }
      ],
      admin_users: this.getInitialAdminUsers(),
      audit_logs: [
        {
          id: 'log_01',
          admin_user: 'admin',
          action: 'INIT_SYSTEM',
          resource: 'CORE_SERVER',
          details: 'Khởi tạo thành công hệ thống Backend SHAREit cổng 6030',
          ip_address: '127.0.0.1',
          created_at: new Date().toISOString(),
        }
      ]
    };
  }

  // Getters
  public get admin_users() { return this.data.admin_users; }
  public get devices() { return this.data.devices; }
  public get users() { return this.data.users; }
  public get mcds_spaces() { return this.data.mcds_spaces; }
  public get mcds_promotes() { return this.data.mcds_promotes; }
  public get family_products() { return this.data.family_products; }
  public get ad_global_keys() { return this.data.ad_global_keys; }
  public get ad_placements() { return this.data.ad_placements; }
  public get subscription_plans() { return this.data.subscription_plans; }
  public get payment_orders() { return this.data.payment_orders; }
  public get coin_ledger() { return this.data.coin_ledger; }
  public get coin_tasks() { return this.data.coin_tasks; }
  public get coin_rewards() { return this.data.coin_rewards; }
  public get coin_redemptions() { return this.data.coin_redemptions; }
  public get telemetry_events() { return this.data.telemetry_events; }
  public get audit_logs() { return this.data.audit_logs; }
}

export const db = new DatabaseManager();
