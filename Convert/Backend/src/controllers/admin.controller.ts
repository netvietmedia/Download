import fs from 'fs';
import path from 'path';
import {
  db,
  MCDSSpace,
  MCDSPromote,
  AdPlacement,
  AdGlobalKeys,
  SubscriptionPlan,
  FamilyProduct,
  CoinTask,
  CoinRewardItem,
  CoinRedemption,
  AdminUser
} from '../database/db';
import { config } from '../config';
import { signalingService } from '../services/signaling.service';

export const adminLogin = (req: Request, res: Response) => {
  const { username, password } = req.body;

  const user = db.admin_users.find(u => u.username === username && u.password === password);
  const isConfigRoot = username === config.cmsAdminUser && password === config.cmsAdminPass;

  if (user || isConfigRoot) {
    const adminUser = user || {
      id: 'admin_root',
      username: config.cmsAdminUser,
      display_name: 'Super Administrator',
      email: 'superadmin@shareit.io',
      role: 'super_admin' as const,
      avatar_url: 'https://cdn-icons-png.flaticon.com/512/3135/3135715.png',
      is_active: true
    };

    if (!adminUser.is_active) {
      return res.status(403).json({ code: 403, message: 'Tài khoản quản trị viên này đã bị tạm khóa' });
    }

    if (user) {
      user.last_login_at = new Date().toISOString();
    }

    db.audit_logs.unshift({
      id: `log_${Date.now()}`,
      admin_user: adminUser.username,
      action: 'ADMIN_LOGIN',
      resource: 'CMS_PORTAL',
      details: `Đăng nhập thành công vào CMS (${adminUser.display_name} - ${adminUser.role})`,
      ip_address: req.ip || '127.0.0.1',
      created_at: new Date().toISOString()
    });
    db.save();

    return res.json({
      code: 0,
      message: 'Đăng nhập CMS thành công',
      data: {
        token: 'admin_authenticated_session_token_6030',
        id: adminUser.id,
        username: adminUser.username,
        display_name: adminUser.display_name,
        email: adminUser.email,
        role: adminUser.role,
        avatar_url: adminUser.avatar_url || 'https://cdn-icons-png.flaticon.com/512/3135/3135715.png'
      }
    });
  }

  return res.status(401).json({ code: 401, message: 'Sai tên đăng nhập hoặc mật khẩu' });
};

export const getDashboardStats = (req: Request, res: Response) => {
  const totalDevices = db.devices.length;
  const activeToday = db.devices.filter(d => {
    const diff = Date.now() - new Date(d.last_active_at).getTime();
    return diff < 86400000;
  }).length;

  const totalVIPs = db.users.filter(u => u.is_vip).length;
  const totalRevenue = db.payment_orders
    .filter(o => o.status === 'paid')
    .reduce((sum, o) => sum + o.amount, 0);

  const totalTransfers = db.telemetry_events.filter(e => e.event_name === 'transfer_file_finish').length;

  return res.json({
    code: 0,
    data: {
      metrics: {
        total_devices: totalDevices,
        active_today: activeToday || 1,
        active_p2p_sessions: signalingService.getActiveSessionsCount(),
        connected_peers: signalingService.getConnectedPeersCount(),
        total_vips: totalVIPs,
        total_revenue: totalRevenue,
        total_transfers: totalTransfers || 1
      },
      recent_devices: db.devices.slice(-5).reverse(),
      recent_orders: db.payment_orders.slice(-5).reverse(),
      recent_logs: db.audit_logs.slice(0, 8)
    }
  });
};

export const getAdminDevices = (req: Request, res: Response) => {
  return res.json({ code: 0, data: db.devices });
};

export const toggleBanDevice = (req: Request, res: Response) => {
  const { device_uuid } = req.body;
  const device = db.devices.find(d => d.device_uuid === device_uuid);
  if (!device) return res.status(404).json({ code: 404, message: 'Thiết bị không tồn tại' });

  device.is_banned = !device.is_banned;
  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: config.cmsAdminUser,
    action: device.is_banned ? 'BAN_DEVICE' : 'UNBAN_DEVICE',
    resource: 'DEVICE',
    details: `${device.is_banned ? 'Khóa' : 'Mở khóa'} thiết bị: ${device_uuid}`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: 'Cập nhật trạng thái thiết bị thành công', data: device });
};

export const getAdminMCDS = (req: Request, res: Response) => {
  return res.json({
    code: 0,
    data: {
      spaces: db.mcds_spaces,
      promotes: db.mcds_promotes
    }
  });
};

export const createMCDSPromote = (req: Request, res: Response) => {
  const { space_id, title, content_type, media_url, action_type, action_value, priority } = req.body;
  const newProm: MCDSPromote = {
    id: `prom_${Date.now()}`,
    space_id: space_id || 'home_top_banner',
    title: title || 'New Promotion Banner',
    content_type: content_type || 'image',
    media_url: media_url || 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800',
    action_type: action_type || 'deeplink',
    action_value: action_value || 'shareit://subscription/paywall',
    priority: parseInt(priority || '5', 10),
    is_active: true
  };

  db.mcds_promotes.unshift(newProm);
  db.save();
  return res.json({ code: 0, message: 'Thêm banner MCDS mới thành công', data: newProm });
};

export const deleteMCDSPromote = (req: Request, res: Response) => {
  const { id } = req.params;
  const idx = db.mcds_promotes.findIndex(p => p.id === id);
  if (idx !== -1) {
    db.mcds_promotes.splice(idx, 1);
    db.save();
  }
  return res.json({ code: 0, message: 'Đã xóa banner' });
};

import { Request, Response } from 'express';

export const getAdminAds = (req: Request, res: Response) => {
  return res.json({
    code: 0,
    data: {
      global_keys: db.ad_global_keys,
      placements: db.ad_placements
    }
  });
};

export const updateAdminGlobalAdKeys = (req: Request, res: Response) => {
  const { admob_app_id, applovin_sdk_key, mintegral_app_id, mintegral_app_key, unity_game_id, pangle_app_id, ironsource_app_key, test_mode } = req.body;

  if (admob_app_id !== undefined) db.ad_global_keys.admob_app_id = admob_app_id;
  if (applovin_sdk_key !== undefined) db.ad_global_keys.applovin_sdk_key = applovin_sdk_key;
  if (mintegral_app_id !== undefined) db.ad_global_keys.mintegral_app_id = mintegral_app_id;
  if (mintegral_app_key !== undefined) db.ad_global_keys.mintegral_app_key = mintegral_app_key;
  if (unity_game_id !== undefined) db.ad_global_keys.unity_game_id = unity_game_id;
  if (pangle_app_id !== undefined) db.ad_global_keys.pangle_app_id = pangle_app_id;
  if (ironsource_app_key !== undefined) db.ad_global_keys.ironsource_app_key = ironsource_app_key;
  if (test_mode !== undefined) db.ad_global_keys.test_mode = Boolean(test_mode);

  // Sync to config/ads_keys.json
  try {
    const configPath = path.join(process.cwd(), 'src', 'config', 'ads_keys.json');
    const diskConfig = {
      version: '1.1.0',
      updated_at: new Date().toISOString(),
      description: 'Cấu hình Khóa Quảng Cáo Backend Midas / Mediation Waterfall',
      credentials: db.ad_global_keys,
      placements: db.ad_placements.reduce((acc: any, p) => {
        acc[p.placement_id] = {
          format: p.placement_type.toUpperCase(),
          waterfall: p.mediation_waterfall
        };
        return acc;
      }, {})
    };
    fs.writeFileSync(configPath, JSON.stringify(diskConfig, null, 2), 'utf8');
  } catch (err) {
    console.warn('Could not sync ads_keys.json file:', err);
  }

  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: config.cmsAdminUser,
    action: 'UPDATE_GLOBAL_AD_KEYS',
    resource: 'ADS_MEDIATION',
    details: `Cập nhật khóa SDK AdMob/AppLovin/Mintegral (Test mode: ${db.ad_global_keys.test_mode})`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: 'Cập nhật khóa quảng cáo toàn cục thành công', data: db.ad_global_keys });
};

export const createAdminAdPlacement = (req: Request, res: Response) => {
  const { placement_id, placement_type, mediation_waterfall } = req.body;
  if (!placement_id) return res.status(400).json({ code: 400, message: 'Thiếu placement_id' });

  const exists = db.ad_placements.find(p => p.placement_id === placement_id);
  if (exists) return res.status(400).json({ code: 400, message: 'Vị trí placement_id này đã tồn tại' });

  const newPlacement: AdPlacement = {
    id: `ad_${Date.now()}`,
    placement_id,
    placement_type: placement_type || 'banner',
    mediation_waterfall: Array.isArray(mediation_waterfall) && mediation_waterfall.length > 0 ? mediation_waterfall : [
      { network: 'admob', unit_id: 'ca-app-pub-3940256099942544/6300978111', ecpm_floor: 1.00, timeout_ms: 2500, is_active: true }
    ],
    is_active: true
  };

  db.ad_placements.push(newPlacement);
  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: config.cmsAdminUser,
    action: 'CREATE_AD_PLACEMENT',
    resource: 'ADS_MEDIATION',
    details: `Thêm vị trí quảng cáo mới: ${placement_id} (${newPlacement.placement_type})`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: 'Thêm vị trí quảng cáo mới thành công', data: newPlacement });
};

export const updateAdPlacement = (req: Request, res: Response) => {
  const { placement_id, waterfall, is_active, placement_type } = req.body;
  const placement = db.ad_placements.find(p => p.placement_id === placement_id);
  if (!placement) return res.status(404).json({ code: 404, message: 'Placement không tồn tại' });

  if (Array.isArray(waterfall)) {
    placement.mediation_waterfall = waterfall;
  }
  if (is_active !== undefined) {
    placement.is_active = Boolean(is_active);
  }
  if (placement_type) {
    placement.placement_type = placement_type;
  }

  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: config.cmsAdminUser,
    action: 'UPDATE_AD_WATERFALL',
    resource: 'ADS_MEDIATION',
    details: `Cập nhật Waterfall vị trí: ${placement_id}`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();
  return res.json({ code: 0, message: 'Cập nhật ma trận quảng cáo thành công', data: placement });
};

export const deleteAdminAdPlacement = (req: Request, res: Response) => {
  const { placement_id } = req.params;
  const idx = db.ad_placements.findIndex(p => p.placement_id === placement_id);
  if (idx !== -1) {
    const deleted = db.ad_placements.splice(idx, 1)[0];
    db.audit_logs.unshift({
      id: `log_${Date.now()}`,
      admin_user: config.cmsAdminUser,
      action: 'DELETE_AD_PLACEMENT',
      resource: 'ADS_MEDIATION',
      details: `Đã xóa vị trí quảng cáo: ${deleted.placement_id}`,
      ip_address: req.ip || '127.0.0.1',
      created_at: new Date().toISOString()
    });
    db.save();
  }
  return res.json({ code: 0, message: 'Đã xóa vị trí quảng cáo thành công' });
};

export const getAdminPlans = (req: Request, res: Response) => {
  return res.json({ code: 0, data: db.subscription_plans });
};

// COIN ECONOMY & REWARDS MANAGEMENT
export const getAdminCoinOverview = (req: Request, res: Response) => {
  const totalCirculation = db.users.reduce((sum, u) => sum + (u.coin_balance || 0), 0);
  const totalIssued = db.coin_ledger
    .filter(tx => tx.amount > 0)
    .reduce((sum, tx) => sum + tx.amount, 0);
  const totalRedeemed = db.coin_ledger
    .filter(tx => tx.amount < 0)
    .reduce((sum, tx) => sum + Math.abs(tx.amount), 0);
  const pendingRedemptions = db.coin_redemptions.filter(r => r.status === 'pending').length;

  return res.json({
    code: 0,
    data: {
      stats: {
        total_issued: totalIssued,
        total_circulation: totalCirculation,
        total_redeemed: totalRedeemed,
        pending_redemptions: pendingRedemptions,
        total_tasks: db.coin_tasks.length,
        total_rewards: db.coin_rewards.length
      },
      tasks: db.coin_tasks,
      rewards: db.coin_rewards,
      redemptions: db.coin_redemptions.slice(-50).reverse(),
      ledger: db.coin_ledger.slice(-50).reverse()
    }
  });
};

export const createAdminCoinTask = (req: Request, res: Response) => {
  const { title, description, reward_coins, category, icon_url, action_deeplink, daily_limit } = req.body;
  const newTask: CoinTask = {
    id: `task_${Date.now()}`,
    title: title || 'Nhiệm vụ mới',
    description: description || 'Hoàn thành để nhận coin thưởng',
    reward_coins: parseInt(reward_coins || '50', 10),
    category: category || 'transfer',
    icon_url: icon_url || 'https://cdn-icons-png.flaticon.com/512/724/724933.png',
    action_deeplink: action_deeplink || 'shareit://home',
    daily_limit: parseInt(daily_limit || '1', 10),
    is_active: true
  };

  db.coin_tasks.push(newTask);
  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: config.cmsAdminUser,
    action: 'CREATE_COIN_TASK',
    resource: 'COIN_TASK',
    details: `Thêm nhiệm vụ nhận xu: ${newTask.title} (+${newTask.reward_coins} xu)`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: 'Thêm nhiệm vụ kiếm coin mới thành công', data: newTask });
};

export const updateAdminCoinTask = (req: Request, res: Response) => {
  const { id } = req.params;
  const task = db.coin_tasks.find(t => t.id === id);
  if (!task) return res.status(404).json({ code: 404, message: 'Nhiệm vụ không tồn tại' });

  const fields = ['title', 'description', 'reward_coins', 'category', 'icon_url', 'action_deeplink', 'daily_limit', 'is_active'];
  fields.forEach(field => {
    if (req.body[field] !== undefined) {
      if (field === 'reward_coins' || field === 'daily_limit') {
        (task as any)[field] = parseInt(req.body[field], 10);
      } else if (field === 'is_active') {
        (task as any)[field] = Boolean(req.body[field]);
      } else {
        (task as any)[field] = req.body[field];
      }
    }
  });

  db.save();
  return res.json({ code: 0, message: 'Cập nhật nhiệm vụ thành công', data: task });
};

export const deleteAdminCoinTask = (req: Request, res: Response) => {
  const { id } = req.params;
  const idx = db.coin_tasks.findIndex(t => t.id === id);
  if (idx !== -1) {
    db.coin_tasks.splice(idx, 1);
    db.save();
  }
  return res.json({ code: 0, message: 'Đã xóa nhiệm vụ' });
};

export const createAdminCoinReward = (req: Request, res: Response) => {
  const { title, category, cost_coins, stock, icon_url, description, payout_value_str } = req.body;
  const newReward: CoinRewardItem = {
    id: `rew_${Date.now()}`,
    title: title || 'Vật phẩm đổi thưởng mới',
    category: category || 'vip_pass',
    cost_coins: parseInt(cost_coins || '500', 10),
    stock: parseInt(stock || '100', 10),
    icon_url: icon_url || 'https://cdn-icons-png.flaticon.com/512/6941/6941697.png',
    description: description || 'Quyền lợi đổi bằng điểm thưởng xu',
    payout_value_str: payout_value_str || 'Vật phẩm',
    is_active: true
  };

  db.coin_rewards.push(newReward);
  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: config.cmsAdminUser,
    action: 'CREATE_COIN_REWARD',
    resource: 'COIN_SHOP',
    details: `Thêm quà đổi thưởng: ${newReward.title} (${newReward.cost_coins} xu)`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: 'Thêm vật phẩm vào Cửa hàng Coin thành công', data: newReward });
};

export const updateAdminCoinReward = (req: Request, res: Response) => {
  const { id } = req.params;
  const reward = db.coin_rewards.find(r => r.id === id);
  if (!reward) return res.status(404).json({ code: 404, message: 'Vật phẩm không tồn tại' });

  const fields = ['title', 'category', 'cost_coins', 'stock', 'icon_url', 'description', 'payout_value_str', 'is_active'];
  fields.forEach(field => {
    if (req.body[field] !== undefined) {
      if (field === 'cost_coins' || field === 'stock') {
        (reward as any)[field] = parseInt(req.body[field], 10);
      } else if (field === 'is_active') {
        (reward as any)[field] = Boolean(req.body[field]);
      } else {
        (reward as any)[field] = req.body[field];
      }
    }
  });

  db.save();
  return res.json({ code: 0, message: 'Cập nhật vật phẩm thành công', data: reward });
};

export const deleteAdminCoinReward = (req: Request, res: Response) => {
  const { id } = req.params;
  const idx = db.coin_rewards.findIndex(r => r.id === id);
  if (idx !== -1) {
    db.coin_rewards.splice(idx, 1);
    db.save();
  }
  return res.json({ code: 0, message: 'Đã xóa vật phẩm đổi thưởng' });
};

export const processAdminCoinRedemption = (req: Request, res: Response) => {
  const { id } = req.params;
  const { action, admin_note } = req.body; // action: 'approve' | 'reject'
  const redemption = db.coin_redemptions.find(r => r.id === id);
  if (!redemption) return res.status(404).json({ code: 404, message: 'Yêu cầu đổi quà không tồn tại' });

  if (action === 'approve') {
    redemption.status = 'approved';
    redemption.admin_note = admin_note || 'Đã phê duyệt và chuyển thưởng thành công';
    redemption.processed_at = new Date().toISOString();
  } else if (action === 'reject') {
    redemption.status = 'rejected';
    redemption.admin_note = admin_note || 'Yêu cầu bị từ chối - đã hoàn xu vào tài khoản';
    redemption.processed_at = new Date().toISOString();

    // Hoàn xu lại cho người dùng
    const user = db.users.find(u => u.id === redemption.user_id);
    if (user) {
      user.coin_balance += redemption.coins_spent;
      db.coin_ledger.unshift({
        id: `tx_${Date.now()}`,
        transaction_id: `tx_refund_${Date.now()}`,
        user_id: user.id,
        device_uuid: redemption.device_uuid,
        event_type: 'redemption_refund',
        amount: redemption.coins_spent,
        balance_after: user.coin_balance,
        metadata: { redemption_id: redemption.id, reason: redemption.admin_note },
        created_at: new Date().toISOString()
      });
    }
  }

  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: config.cmsAdminUser,
    action: action === 'approve' ? 'APPROVE_REDEMPTION' : 'REJECT_REDEMPTION',
    resource: 'COIN_REDEMPTION',
    details: `${action === 'approve' ? 'Duyệt' : 'Từ chối'} đơn đổi quà ${redemption.request_code} (${redemption.reward_title})`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: `Đã xử lý yêu cầu (${action}) thành công`, data: redemption });
};

export const adjustAdminUserCoin = (req: Request, res: Response) => {
  const { identifier, user_id, amount, reason } = req.body;
  const targetId = identifier || user_id;
  const adjAmount = parseInt(amount || '0', 10);
  if (adjAmount === 0) return res.status(400).json({ code: 400, message: 'Số lượng coin điều chỉnh phải khác 0' });

  const user = db.users.find(u => u.id === targetId || u.display_name === targetId) || db.users[0];
  if (!user) return res.status(404).json({ code: 404, message: 'Người dùng không tồn tại' });

  user.coin_balance = Math.max(0, user.coin_balance + adjAmount);

  const tx = {
    id: `tx_${Date.now()}`,
    transaction_id: `tx_adj_${Date.now()}`,
    user_id: user.id,
    device_uuid: db.devices[0]?.device_uuid || 'admin_manual_adj',
    event_type: adjAmount > 0 ? 'admin_grant' : 'admin_deduct',
    amount: adjAmount,
    balance_after: user.coin_balance,
    metadata: { reason: reason || 'Điều chỉnh thủ công từ CMS' },
    created_at: new Date().toISOString()
  };

  db.coin_ledger.unshift(tx);
  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: config.cmsAdminUser,
    action: 'ADJUST_COIN_BALANCE',
    resource: 'COIN_LEDGER',
    details: `${adjAmount > 0 ? 'Cộng' : 'Trừ'} ${Math.abs(adjAmount)} xu cho ${user.display_name} - Lý do: ${reason}`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: 'Điều chỉnh số dư coin thành công', data: { user, transaction: tx } });
};

export const getAdminLedger = (req: Request, res: Response) => {
  return res.json({ code: 0, data: db.coin_ledger.slice(-50).reverse() });
};

export const getAdminLogs = (req: Request, res: Response) => {
  return res.json({ code: 0, data: db.audit_logs.slice(0, 50) });
};

export const getAdminFamilyProducts = (req: Request, res: Response) => {
  return res.json({ code: 0, data: db.family_products });
};

export const createAdminFamilyProduct = (req: Request, res: Response) => {
  const {
    app_name,
    package_name,
    icon_url,
    download_url,
    category,
    badge,
    rating,
    download_count_str,
    description,
    cpi_payout,
    priority,
    is_active
  } = req.body;

  const newProduct: FamilyProduct = {
    id: `fam_${Date.now()}`,
    app_name: app_name || 'Ứng Dụng Mới',
    package_name: package_name || 'com.ushareit.app',
    icon_url: icon_url || 'https://cdn-icons-png.flaticon.com/512/2879/2879209.png',
    download_url: download_url || 'https://play.google.com/store/apps',
    category: category || 'Tiện ích',
    badge: badge || 'NEW',
    rating: parseFloat(rating || '4.8'),
    download_count_str: download_count_str || '10M+',
    description: description || 'Mô tả tính năng ứng dụng gia đình SHAREit',
    cpi_payout: parseFloat(cpi_payout || '0.30'),
    priority: parseInt(priority || '5', 10),
    is_active: is_active !== undefined ? Boolean(is_active) : true
  };

  db.family_products.unshift(newProduct);
  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: config.cmsAdminUser,
    action: 'CREATE_FAMILY_APP',
    resource: 'FAMILY_PRODUCT',
    details: `Thêm ứng dụng gia đình / CPI ad: ${newProduct.app_name} (${newProduct.package_name})`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: 'Thêm ứng dụng SHAREit Family thành công', data: newProduct });
};

export const updateAdminFamilyProduct = (req: Request, res: Response) => {
  const { id } = req.params;
  const product = db.family_products.find(p => p.id === id);
  if (!product) return res.status(404).json({ code: 404, message: 'Không tìm thấy ứng dụng gia đình' });

  const fields = ['app_name', 'package_name', 'icon_url', 'download_url', 'category', 'badge', 'rating', 'download_count_str', 'description', 'cpi_payout', 'priority', 'is_active'];
  fields.forEach(field => {
    if (req.body[field] !== undefined) {
      if (field === 'rating' || field === 'cpi_payout') {
        (product as any)[field] = parseFloat(req.body[field]);
      } else if (field === 'priority') {
        (product as any)[field] = parseInt(req.body[field], 10);
      } else if (field === 'is_active') {
        (product as any)[field] = Boolean(req.body[field]);
      } else {
        (product as any)[field] = req.body[field];
      }
    }
  });

  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: config.cmsAdminUser,
    action: 'UPDATE_FAMILY_APP',
    resource: 'FAMILY_PRODUCT',
    details: `Cập nhật thông tin ứng dụng: ${product.app_name} (Active: ${product.is_active})`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: 'Cập nhật ứng dụng thành công', data: product });
};

export const deleteAdminFamilyProduct = (req: Request, res: Response) => {
  const { id } = req.params;
  const idx = db.family_products.findIndex(p => p.id === id);
  if (idx !== -1) {
    const deleted = db.family_products.splice(idx, 1)[0];
    db.audit_logs.unshift({
      id: `log_${Date.now()}`,
      admin_user: config.cmsAdminUser,
      action: 'DELETE_FAMILY_APP',
      resource: 'FAMILY_PRODUCT',
      details: `Đã gỡ ứng dụng gia đình: ${deleted.app_name}`,
      ip_address: req.ip || '127.0.0.1',
      created_at: new Date().toISOString()
    });
    db.save();
  }
  return res.json({ code: 0, message: 'Đã xóa ứng dụng thành công' });
};

// ==========================================
// ADMIN USER MANAGEMENT & RBAC
// ==========================================
export const getAdminUsers = (req: Request, res: Response) => {
  const sanitized = db.admin_users.map(u => ({
    id: u.id,
    username: u.username,
    display_name: u.display_name,
    email: u.email,
    role: u.role,
    avatar_url: u.avatar_url || 'https://cdn-icons-png.flaticon.com/512/3135/3135715.png',
    is_active: u.is_active,
    created_at: u.created_at,
    last_login_at: u.last_login_at
  }));
  return res.json({ code: 0, data: sanitized });
};

export const createAdminUser = (req: Request, res: Response) => {
  const { username, password, display_name, email, role, avatar_url } = req.body;
  if (!username || !password || !display_name) {
    return res.status(400).json({ code: 400, message: 'Vui lòng nhập đầy đủ tên đăng nhập, mật khẩu và tên hiển thị' });
  }

  const existing = db.admin_users.find(u => u.username.toLowerCase() === username.trim().toLowerCase());
  if (existing) {
    return res.status(400).json({ code: 400, message: `Tên đăng nhập '${username}' đã tồn tại trong hệ thống` });
  }

  const newAdmin: AdminUser = {
    id: `admin_${Date.now()}`,
    username: username.trim(),
    password: password.trim(),
    display_name: display_name.trim(),
    email: email ? email.trim() : `${username.trim()}@shareit.io`,
    role: role || 'admin',
    avatar_url: avatar_url || 'https://cdn-icons-png.flaticon.com/512/3135/3135768.png',
    is_active: true,
    created_at: new Date().toISOString(),
    last_login_at: undefined
  };

  db.admin_users.unshift(newAdmin);
  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: (req as any).admin_username || 'admin',
    action: 'CREATE_ADMIN_USER',
    resource: 'ADMIN_ACCOUNTS',
    details: `Tạo quản trị viên mới: ${newAdmin.username} (${newAdmin.display_name}, Quyền: ${newAdmin.role})`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: 'Tạo tài khoản quản trị viên thành công', data: { id: newAdmin.id, username: newAdmin.username } });
};

export const updateAdminUser = (req: Request, res: Response) => {
  const { id } = req.params;
  const { display_name, email, role, is_active, avatar_url } = req.body;

  const target = db.admin_users.find(u => u.id === id || u.username === id);
  if (!target) {
    return res.status(404).json({ code: 404, message: 'Không tìm thấy quản trị viên' });
  }

  // Guard: Cannot deactivate or demote the last super_admin
  if (target.role === 'super_admin' && (is_active === false || (role && role !== 'super_admin'))) {
    const superAdmins = db.admin_users.filter(u => u.role === 'super_admin' && u.is_active && u.id !== target.id);
    if (superAdmins.length === 0) {
      return res.status(400).json({ code: 400, message: 'Không thể khóa hoặc hạ quyền Super Admin duy nhất còn lại của hệ thống' });
    }
  }

  if (display_name !== undefined) target.display_name = display_name;
  if (email !== undefined) target.email = email;
  if (role !== undefined) target.role = role;
  if (is_active !== undefined) target.is_active = is_active;
  if (avatar_url !== undefined) target.avatar_url = avatar_url;

  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: (req as any).admin_username || 'admin',
    action: 'UPDATE_ADMIN_USER',
    resource: 'ADMIN_ACCOUNTS',
    details: `Cập nhật thông tin quản trị viên: ${target.username} (Display: ${target.display_name}, Role: ${target.role}, Active: ${target.is_active})`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: 'Cập nhật thông tin quản trị viên thành công' });
};

export const changeAdminPassword = (req: Request, res: Response) => {
  const { id } = req.params;
  const { new_password, current_password } = req.body;

  if (!new_password || new_password.trim().length < 6) {
    return res.status(400).json({ code: 400, message: 'Mật khẩu mới phải có ít nhất 6 ký tự' });
  }

  const target = db.admin_users.find(u => u.id === id || u.username === id);
  if (!target) {
    return res.status(404).json({ code: 404, message: 'Không tìm thấy quản trị viên' });
  }

  // If current_password is provided, verify it
  if (current_password && target.password && target.password !== current_password) {
    return res.status(400).json({ code: 400, message: 'Mật khẩu hiện tại không chính xác' });
  }

  target.password = new_password.trim();

  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: (req as any).admin_username || 'admin',
    action: 'CHANGE_ADMIN_PASSWORD',
    resource: 'ADMIN_ACCOUNTS',
    details: `Đổi mật khẩu thành công cho tài khoản quản trị: ${target.username}`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: `Đã đổi mật khẩu cho quản trị viên ${target.username} thành công!` });
};

export const deleteAdminUser = (req: Request, res: Response) => {
  const { id } = req.params;
  const idx = db.admin_users.findIndex(u => u.id === id || u.username === id);
  if (idx === -1) {
    return res.status(404).json({ code: 404, message: 'Không tìm thấy quản trị viên' });
  }

  const target = db.admin_users[idx];

  // Protect root admin and prevent deleting the last super_admin
  if (target.username === 'admin' || target.role === 'super_admin') {
    const superAdmins = db.admin_users.filter(u => u.role === 'super_admin' && u.id !== target.id);
    if (superAdmins.length === 0) {
      return res.status(400).json({ code: 400, message: 'Không thể xóa tài khoản Super Administrator duy nhất của hệ thống' });
    }
  }

  db.admin_users.splice(idx, 1);
  db.audit_logs.unshift({
    id: `log_${Date.now()}`,
    admin_user: (req as any).admin_username || 'admin',
    action: 'DELETE_ADMIN_USER',
    resource: 'ADMIN_ACCOUNTS',
    details: `Đã xóa tài khoản quản trị viên: ${target.username} (${target.display_name})`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({ code: 0, message: `Đã xóa tài khoản ${target.username} thành công!` });
};

