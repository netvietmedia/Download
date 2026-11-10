import { WebSocketServer, WebSocket } from 'ws';

interface ClientPeer {
  ws: WebSocket;
  deviceId: string;
  sessionId?: string;
}

export class SignalingService {
  private wss: WebSocketServer | null = null;
  private peers = new Map<string, ClientPeer>();
  private sessions = new Map<string, { senderId: string; receiverId?: string; pinCode: string }>();

  public init(wss: WebSocketServer) {
    this.wss = wss;

    this.wss.on('connection', (ws: WebSocket, req) => {
      let currentDeviceId = '';

      ws.on('message', (message: string) => {
        try {
          const data = JSON.parse(message.toString());
          const { type, deviceId, targetDeviceId, sessionId, pinCode, payload } = data;

          switch (type) {
            case 'REGISTER':
              currentDeviceId = deviceId;
              this.peers.set(deviceId, { ws, deviceId });
              ws.send(JSON.stringify({ type: 'REGISTERED', deviceId, status: 'ok' }));
              break;

            case 'CREATE_SESSION':
              const newPin = Math.floor(100000 + Math.random() * 900000).toString();
              const newSessionId = `sess_${Date.now()}_${newPin}`;
              this.sessions.set(newSessionId, { senderId: deviceId, pinCode: newPin });
              ws.send(JSON.stringify({ type: 'SESSION_CREATED', sessionId: newSessionId, pinCode: newPin }));
              break;

            case 'JOIN_SESSION':
              let targetSession: string | undefined;
              for (const [sId, sess] of this.sessions.entries()) {
                if (sess.pinCode === pinCode || sId === sessionId) {
                  targetSession = sId;
                  sess.receiverId = deviceId;
                  break;
                }
              }

              if (targetSession) {
                const sessionInfo = this.sessions.get(targetSession);
                ws.send(JSON.stringify({ type: 'SESSION_JOINED', sessionId: targetSession, senderId: sessionInfo?.senderId }));

                // Báo cho máy gửi biết máy nhận đã vào phiên
                if (sessionInfo?.senderId && this.peers.has(sessionInfo.senderId)) {
                  this.peers.get(sessionInfo.senderId)?.ws.send(
                    JSON.stringify({ type: 'PEER_JOINED', receiverId: deviceId, sessionId: targetSession })
                  );
                }
              } else {
                ws.send(JSON.stringify({ type: 'ERROR', message: 'Mã PIN hoặc phiên không tồn tại' }));
              }
              break;

            case 'SIGNAL_OFFER':
            case 'SIGNAL_ANSWER':
            case 'ICE_CANDIDATE':
              if (targetDeviceId && this.peers.has(targetDeviceId)) {
                this.peers.get(targetDeviceId)?.ws.send(
                  JSON.stringify({ type, fromDeviceId: deviceId, payload, sessionId })
                );
              }
              break;

            default:
              break;
          }
        } catch (e) {
          console.error('Signaling message parse error:', e);
        }
      });

      ws.on('close', () => {
        if (currentDeviceId) {
          this.peers.delete(currentDeviceId);
        }
      });
    });
  }

  public getActiveSessionsCount(): number {
    return this.sessions.size;
  }

  public getConnectedPeersCount(): number {
    return this.peers.size;
  }
}

export const signalingService = new SignalingService();

