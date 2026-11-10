import { Request, Response } from 'express';
import { db, TelemetryEvent } from '../database/db';

export const ingestBatchEvents = (req: Request, res: Response) => {
  const { device_uuid, events } = req.body;

  if (Array.isArray(events)) {
    events.forEach(evt => {
      const telEvent: TelemetryEvent = {
        id: `tel_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
        event_name: evt.event_name || 'unknown',
        device_uuid: device_uuid || 'dev_sample',
        timestamp: evt.timestamp || Date.now(),
        properties: evt.properties || {},
        created_at: new Date().toISOString()
      };
      db.telemetry_events.push(telEvent);
    });

    // Giữ tối đa 1000 sự kiện gần nhất trong bộ nhớ
    if (db.telemetry_events.length > 1000) {
      db.telemetry_events.splice(0, db.telemetry_events.length - 1000);
    }
    db.save();
  }

  return res.json({
    code: 0,
    message: 'Batch events accepted',
    data: {
      accepted_count: Array.isArray(events) ? events.length : 0,
      server_time: Math.floor(Date.now() / 1000)
    }
  });
};

export const reportCrash = (req: Request, res: Response) => {
  const { device_uuid, stack_trace, crash_type } = req.body;
  console.warn(`[CRASH REPORT] Device: ${device_uuid}, Type: ${crash_type}`);

  db.audit_logs.push({
    id: `crash_${Date.now()}`,
    admin_user: 'system',
    action: 'APP_CRASH',
    resource: 'CLIENT_DEVICE',
    details: `${crash_type}: ${String(stack_trace).substring(0, 120)}...`,
    ip_address: req.ip || '127.0.0.1',
    created_at: new Date().toISOString()
  });
  db.save();

  return res.json({
    code: 0,
    message: 'Crash report recorded'
  });
};

export const submitFeedback = (req: Request, res: Response) => {
  const { device_uuid, content, contact } = req.body;
  return res.json({
    code: 0,
    message: 'Cảm ơn bạn đã gửi phản hồi đóng góp!'
  });
};

