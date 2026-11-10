import { Request, Response } from 'express';
import fs from 'fs';
import path from 'path';
import { config } from '../config';

// Đảm bảo thư mục relay tồn tại
if (!fs.existsSync(config.relayTempDir)) {
  fs.mkdirSync(config.relayTempDir, { recursive: true });
}

export const createRelaySession = (req: Request, res: Response) => {
  const pinCode = Math.floor(100000 + Math.random() * 900000).toString();
  const sessionId = `relay_${Date.now()}_${pinCode}`;

  return res.json({
    code: 0,
    message: 'Tạo phiên Cloud Relay thành công',
    data: {
      session_id: sessionId,
      pin_code: pinCode,
      qr_payload: `shareit://relay/join?session=${sessionId}&pin=${pinCode}`,
      expires_in_sec: 1800
    }
  });
};

export const joinRelaySession = (req: Request, res: Response) => {
  const { session_id, pin_code } = req.body;
  return res.json({
    code: 0,
    message: 'Tham gia phiên thành công',
    data: {
      session_id,
      status: 'ready'
    }
  });
};

export const uploadRelayChunk = (req: Request, res: Response) => {
  const { session_id, chunk_index, total_chunks, checksum } = req.body;
  return res.json({
    code: 0,
    message: 'Chunk đã nhận thành công',
    data: {
      chunk_index,
      received_bytes: req.file ? req.file.size : 0
    }
  });
};

export const downloadRelayChunk = (req: Request, res: Response) => {
  const { session_id, chunk_index } = req.params;
  return res.json({
    code: 0,
    message: 'Stream chunk ready'
  });
};

