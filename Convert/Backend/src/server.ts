import express from 'express';
import http from 'http';
import { WebSocketServer } from 'ws';
import cors from 'cors';
import path from 'path';
import { config } from './config';
import { signalingService } from './services/signaling.service';

import authRoutes from './routes/auth.routes';
import relayRoutes from './routes/relay.routes';
import mcdsRoutes from './routes/mcds.routes';
import adsRoutes from './routes/ads.routes';
import payRoutes from './routes/pay.routes';
import coinRoutes from './routes/coin.routes';
import telemetryRoutes from './routes/telemetry.routes';
import adminRoutes from './routes/admin.routes';

const app = express();
const server = http.createServer(app);

// 1. Cấu hình Middlewares
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// 2. Static files cho giao diện CMS Admin Dashboard
const publicDir = path.join(__dirname, '../public');
app.use(express.static(publicDir));

// 3. Health Check & Ping Endpoints
app.get('/healthz', (req, res) => {
  res.json({
    status: 'UP',
    service: 'SHAREit Modular Backend & Enterprise CMS',
    port: config.port,
    timestamp: new Date().toISOString(),
    uptime_sec: Math.floor(process.uptime())
  });
});

// Tương thích ngược với các endpoint ping cũ của SHAREit DEX
app.get('/ping/', (req, res) => res.send('PONG'));
app.get('/config/group/deeplink', (req, res) => {
  res.json({ code: 0, data: { deeplinks: ['shareit://transfer/send', 'shareit://clean/scan', 'shareit://subscription/paywall'] } });
});

// 4. Đăng ký các phân hệ REST API
app.use('/api/v1/auth', authRoutes);
app.use('/api/v1/relay', relayRoutes);
app.use('/api/v1/mcds', mcdsRoutes);
app.use('/api/v1/ads', adsRoutes);
app.use('/api/v1/pay', payRoutes);
app.use('/api/v1/coin', coinRoutes);
app.use('/api/v1/telemetry', telemetryRoutes);
app.use('/api/v1/admin', adminRoutes);

// Điều hướng mặc định về CMS Dashboard
app.get('/', (req, res) => {
  res.redirect('/admin');
});

// 5. Khởi tạo WebSocket Signaling Server cho P2P WebRTC
const wss = new WebSocketServer({ server, path: '/api/v1/signaling/webrtc' });
signalingService.init(wss);

// 6. Khởi động Server trên Cổng 6030
server.listen(config.port, '0.0.0.0', () => {
  console.log(`=============================================================`);
  console.log(`🚀 SHAREit Enterprise Backend & CMS Server IS RUNNING!`);
  console.log(`🌐 Server Port: ${config.port}`);
  console.log(`🔗 REST API Base: http://localhost:${config.port}/api/v1`);
  console.log(`📡 WebSocket Signaling: ws://localhost:${config.port}/api/v1/signaling/webrtc`);
  console.log(`🖥️  CMS Admin Dashboard: http://localhost:${config.port}/admin`);
  console.log(`👤 Default CMS Account: ${config.cmsAdminUser} / ${config.cmsAdminPass}`);
  console.log(`=============================================================`);
});

