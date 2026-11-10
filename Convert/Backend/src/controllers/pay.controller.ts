import { Request, Response } from 'express';
import { db, PaymentOrder } from '../database/db';

export const getSubscriptionPlans = (req: Request, res: Response) => {
  const plans = db.subscription_plans.filter(p => p.is_active);
  return res.json({
    code: 0,
    message: 'success',
    data: { plans }
  });
};

export const createOrder = (req: Request, res: Response) => {
  const { plan_id, device_uuid, gateway } = req.body;
  const plan = db.subscription_plans.find(p => p.id === plan_id);

  if (!plan) {
    return res.status(404).json({ code: 404, message: 'Gói VIP không tồn tại' });
  }

  const orderCode = `ORD_${Date.now()}_${Math.floor(1000 + Math.random() * 9000)}`;
  const order: PaymentOrder = {
    id: `ord_${Date.now()}`,
    order_code: orderCode,
    device_uuid,
    plan_id: plan.id,
    gateway: gateway || 'google_play',
    transaction_id: '',
    purchase_token: '',
    amount: plan.price,
    currency: plan.currency,
    status: 'pending',
    created_at: new Date().toISOString()
  };

  db.payment_orders.push(order);
  db.save();

  return res.json({
    code: 0,
    message: 'Tạo đơn hàng thành công',
    data: { order }
  });
};

export const verifyOrder = (req: Request, res: Response) => {
  const { order_code, purchase_token, product_id, device_uuid } = req.body;

  let order = db.payment_orders.find(o => o.order_code === order_code);
  if (!order) {
    order = {
      id: `ord_${Date.now()}`,
      order_code: order_code || `ORD_${Date.now()}`,
      device_uuid,
      plan_id: product_id || 'vip_yearly',
      gateway: 'google_play',
      transaction_id: `GPA.${Math.floor(1000 + Math.random() * 9000)}-${Date.now()}`,
      purchase_token: purchase_token || 'token_sample',
      amount: 24.99,
      currency: 'USD',
      status: 'paid',
      created_at: new Date().toISOString()
    };
    db.payment_orders.push(order);
  } else {
    order.status = 'paid';
    order.purchase_token = purchase_token;
  }

  // Cập nhật trạng thái VIP cho người dùng đầu tiên hoặc thiết bị
  if (db.users.length > 0) {
    db.users[0].is_vip = true;
    db.users[0].vip_expire_at = new Date(Date.now() + 86400000 * 365).toISOString();
  }
  db.save();

  return res.json({
    code: 0,
    message: 'Xác thực thanh toán VIP thành công',
    data: {
      is_vip: true,
      vip_expire_at: new Date(Date.now() + 86400000 * 365).toISOString(),
      entitlements: ['no_ads', 'max_speed_200mb', 'cloud_vault_200gb']
    }
  });
};

export const getSubscriptionStatus = (req: Request, res: Response) => {
  const user = db.users[0];
  return res.json({
    code: 0,
    data: {
      is_vip: user ? user.is_vip : false,
      vip_expire_at: user ? user.vip_expire_at : null
    }
  });
};

export const googleWebhook = (req: Request, res: Response) => {
  console.log('Google Cloud Pub/Sub Webhook Received:', req.body);
  return res.status(200).send('OK');
};

