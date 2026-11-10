import { Request, Response } from 'express';
import { db } from '../database/db';

export const fetchMCDS = (req: Request, res: Response) => {
  const { space_ids, context } = req.body;
  const requestedSpaces = space_ids || ['home_top_banner', 'trans_complete_interstitial'];

  const spacesResult = db.mcds_spaces
    .filter(space => space.is_active && requestedSpaces.includes(space.space_id))
    .map(space => {
      const promotes = db.mcds_promotes
        .filter(p => p.space_id === space.space_id && p.is_active)
        .sort((a, b) => b.priority - a.priority);

      return {
        space_id: space.space_id,
        space_name: space.name,
        prom_units: promotes
      };
    });

  return res.json({
    code: 0,
    message: 'success',
    data: {
      spaces: spacesResult,
      server_time: Math.floor(Date.now() / 1000)
    }
  });
};

export const initAppConfig = (req: Request, res: Response) => {
  return res.json({
    code: 0,
    message: 'success',
    data: {
      feature_flags: {
        enable_p2p_5ghz: true,
        enable_webshare: true,
        enable_cloud_relay: true,
        enable_safebox_vault: true,
        enable_muslim_daily: true,
        enable_coin_gamification: true,
        enable_admob_mediation: true,
      },
      kill_switches: {
        maintenance_mode: false,
        force_upgrade: false
      },
      network: {
        signaling_ws_url: 'ws://localhost:6030/api/v1/signaling/webrtc',
        relay_base_url: 'http://localhost:6030/api/v1/relay',
        api_base_url: 'http://localhost:6030/api/v1'
      },
      latest_version: {
        version_name: '6.53.38_UD',
        version_code: 4565338,
        update_url: 'https://play.google.com/store/apps/details?id=com.nextai.sharenow'
      }
    }
  });
};

export const checkUpgrade = (req: Request, res: Response) => {
  const { app_version_code } = req.body;
  const currentCode = parseInt(app_version_code || '0', 10);
  const latestCode = 4565338;

  return res.json({
    code: 0,
    data: {
      has_update: currentCode < latestCode,
      force_update: false,
      version_name: '6.53.38_UD',
      version_code: latestCode,
      release_notes: '1. Tối ưu hóa tốc độ truyền file Wi-Fi Direct lên tới 200MB/s.\n2. Cập nhật giao diện bảo mật Safebox chuẩn Zero-Knowledge.\n3. Vá các lỗi bảo mật và nâng cao hiệu năng hoạt động.',
      download_url: 'http://localhost:6030/download/shareit_latest.apk',
      sha256: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
    }
  });
};

export const getFamilyProducts = (req: Request, res: Response) => {
  const activeProducts = db.family_products
    .filter(p => p.is_active)
    .sort((a, b) => b.priority - a.priority);

  return res.json({
    code: 0,
    message: 'success',
    data: {
      total: activeProducts.length,
      products: activeProducts,
      server_time: Math.floor(Date.now() / 1000)
    }
  });
};

