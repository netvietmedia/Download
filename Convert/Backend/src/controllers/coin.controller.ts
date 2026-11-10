import { Request, Response } from 'express';
import { db, CoinTransaction } from '../database/db';

export const getCoinBalance = (req: Request, res: Response) => {
  const user = db.users[0];
  const history = db.coin_ledger.slice(-10).reverse();

  return res.json({
    code: 0,
    data: {
      balance: user ? user.coin_balance : 0,
      history
    }
  });
};

export const getTaskList = (req: Request, res: Response) => {
  const tasks = db.coin_tasks.filter(t => t.is_active);
  return res.json({
    code: 0,
    data: { tasks }
  });
};

export const dailyCheckin = (req: Request, res: Response) => {
  const { device_uuid } = req.body;
  const user = db.users[0];

  const reward = 100;
  user.coin_balance += reward;

  const tx: CoinTransaction = {
    id: `tx_${Date.now()}`,
    transaction_id: `tx_checkin_${Date.now()}`,
    user_id: user.id,
    device_uuid: device_uuid || 'dev_sample',
    event_type: 'daily_checkin',
    amount: reward,
    balance_after: user.coin_balance,
    metadata: { day_streak: 4 },
    created_at: new Date().toISOString()
  };

  db.coin_ledger.push(tx);
  db.save();

  return res.json({
    code: 0,
    message: 'Điểm danh ngày thành công!',
    data: {
      coins_awarded: reward,
      total_balance: user.coin_balance,
      day_streak: 4,
      transaction_id: tx.transaction_id
    }
  });
};

export const claimTask = (req: Request, res: Response) => {
  const { task_id, device_uuid } = req.body;
  const task = db.coin_tasks.find(t => t.id === task_id);

  if (!task) {
    return res.status(404).json({ code: 404, message: 'Nhiệm vụ không tồn tại' });
  }

  const user = db.users[0];
  user.coin_balance += task.reward_coins;

  const tx: CoinTransaction = {
    id: `tx_${Date.now()}`,
    transaction_id: `tx_task_${Date.now()}`,
    user_id: user.id,
    device_uuid: device_uuid || 'dev_sample',
    event_type: 'task_claim',
    amount: task.reward_coins,
    balance_after: user.coin_balance,
    metadata: { task_id: task.id, title: task.title },
    created_at: new Date().toISOString()
  };

  db.coin_ledger.push(tx);
  db.save();

  return res.json({
    code: 0,
    message: `Nhận thành công ${task.reward_coins} Coin!`,
    data: {
      coins_awarded: task.reward_coins,
      total_balance: user.coin_balance
    }
  });
};

export const redeemReward = (req: Request, res: Response) => {
  const { reward_id, cost_coins } = req.body;
  const user = db.users[0];

  const cost = parseInt(cost_coins || '500', 10);
  if (user.coin_balance < cost) {
    return res.status(400).json({ code: 400, message: 'Số dư Coin không đủ để đổi quà' });
  }

  user.coin_balance -= cost;

  const tx: CoinTransaction = {
    id: `tx_${Date.now()}`,
    transaction_id: `tx_redeem_${Date.now()}`,
    user_id: user.id,
    device_uuid: 'dev_sample',
    event_type: 'redeem_gift',
    amount: -cost,
    balance_after: user.coin_balance,
    metadata: { reward_id },
    created_at: new Date().toISOString()
  };

  db.coin_ledger.push(tx);
  db.save();

  return res.json({
    code: 0,
    message: 'Đổi thưởng thành công!',
    data: {
      voucher_code: `VOUCHER_${Math.random().toString(36).substring(2, 8).toUpperCase()}`,
      balance_remaining: user.coin_balance
    }
  });
};

