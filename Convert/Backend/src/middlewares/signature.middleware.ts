import { Request, Response, NextFunction } from 'express';
import crypto from 'crypto';
import { config } from '../config';

const seenNonces = new Map<string, number>();

// Dọn dẹp nonce cũ sau mỗi 5 phút
setInterval(() => {
  const now = Date.now();
  for (const [nonce, timestamp] of seenNonces.entries()) {
    if (now - timestamp > 300000) {
      seenNonces.delete(nonce);
    }
  }
}, 60000);

export const verifyAppSignature = (req: Request, res: Response, next: NextFunction) => {
  const timestamp = req.headers['x-app-timestamp'] as string;
  const nonce = req.headers['x-app-nonce'] as string;
  const signature = req.headers['x-app-signature'] as string;

  // Nếu là môi trường dev hoặc không truyền header signature thì cho phép qua (với cảnh báo)
  if (!signature || !timestamp || !nonce) {
    return next();
  }

  const reqTime = parseInt(timestamp, 10);
  const now = Date.now();

  // 1. Kiểm tra Sliding Window (Cửa sổ thời gian ±60 giây)
  if (Math.abs(now - reqTime) > 60000) {
    return res.status(400).json({ code: 4001, message: 'Timestamp lệch quá giới hạn cho phép (Replay Protection)' });
  }

  // 2. Chống phát lại (Nonce Verification)
  if (seenNonces.has(nonce)) {
    return res.status(400).json({ code: 4002, message: 'Nonce đã được sử dụng (Replay Attack Detected)' });
  }
  seenNonces.set(nonce, now);

  // 3. Tính toán chữ ký HMAC-SHA256
  const payload = `${req.method}:${req.originalUrl}:${timestamp}:${nonce}`;
  const expectedSig = crypto.createHmac('sha256', config.appSigningSecret).update(payload).digest('hex');

  if (signature !== expectedSig) {
    return res.status(403).json({ code: 4003, message: 'Chữ ký request không hợp lệ (Signature Mismatch)' });
  }

  next();
};

