import { Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { db, Device, User } from '../database/db';
import { config } from '../config';

export const registerDevice = (req: Request, res: Response) => {
  const { device_fingerprint } = req.body;
  const deviceUuid = device_fingerprint?.device_uuid || `dev_${Math.random().toString(36).substring(2, 12)}_${Date.now()}`;

  let device = db.devices.find(d => d.device_uuid === deviceUuid);
  let isNew = false;

  if (!device) {
    device = {
      id: `dev_${Date.now()}`,
      device_uuid: deviceUuid,
      os_type: device_fingerprint?.os_type || 'android',
      os_version: device_fingerprint?.os_version || 34,
      app_version: device_fingerprint?.app_version || '6.53.38_UD',
      app_version_code: device_fingerprint?.app_version_code || 4565338,
      device_model: device_fingerprint?.device_model || 'Android Device',
      manufacturer: device_fingerprint?.manufacturer || 'Generic',
      language: device_fingerprint?.language || 'vi',
      country_code: device_fingerprint?.country_code || 'VN',
      gaid_idfv: device_fingerprint?.gaid || '',
      fcm_token: device_fingerprint?.fcm_token || '',
      is_banned: false,
      first_seen_at: new Date().toISOString(),
      last_active_at: new Date().toISOString(),
    };
    db.devices.push(device);
    isNew = true;
    db.save();
  } else {
    device.last_active_at = new Date().toISOString();
    db.save();
  }

  const token = jwt.sign({ device_uuid: device.device_uuid, role: 'client' }, config.jwtSecret, { expiresIn: '7d' });

  return res.json({
    code: 0,
    message: 'success',
    data: {
      device_uuid: device.device_uuid,
      access_token: token,
      expires_in: 604800,
      is_new_device: isNew,
      server_time: Math.floor(Date.now() / 1000)
    }
  });
};

export const signIn = (req: Request, res: Response) => {
  const { provider, auth_token, device_uuid } = req.body;

  let user = db.users[0]; // Sample user
  const token = jwt.sign({ user_id: user.id, device_uuid, role: 'user' }, config.jwtSecret, { expiresIn: '30d' });

  return res.json({
    code: 0,
    message: 'Đăng nhập thành công',
    data: {
      user: {
        id: user.id,
        display_name: user.display_name,
        avatar_url: user.avatar_url,
        is_vip: user.is_vip,
        vip_expire_at: user.vip_expire_at,
        coin_balance: user.coin_balance
      },
      access_token: token
    }
  });
};

export const getProfile = (req: Request, res: Response) => {
  const user = db.users[0];
  return res.json({
    code: 0,
    data: user
  });
};

