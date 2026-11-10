import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { config } from '../config';

export interface AuthRequest extends Request {
  user?: any;
}

export const authenticateToken = (req: AuthRequest, res: Response, next: NextFunction) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ code: 401, message: 'Yêu cầu Access Token để truy cập' });
  }

  jwt.verify(token, config.jwtSecret, (err, decoded) => {
    if (err) {
      return res.status(403).json({ code: 403, message: 'Token không hợp lệ hoặc đã hết hạn' });
    }
    req.user = decoded;
    next();
  });
};

export const authenticateAdmin = (req: AuthRequest, res: Response, next: NextFunction) => {
  const sessionToken = req.headers['x-admin-token'];
  if (sessionToken === 'admin_authenticated_session_token_6030') {
    return next();
  }
  return res.status(401).json({ code: 401, message: 'Yêu cầu đăng nhập tài khoản Quản trị viên CMS' });
};

