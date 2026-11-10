import { Router } from 'express';
import { ingestBatchEvents, reportCrash, submitFeedback } from '../controllers/telemetry.controller';

const router = Router();

router.post('/events/batch', ingestBatchEvents);
router.post('/telemetry/events/batch', ingestBatchEvents);
router.post('/crash/report', reportCrash);
router.post('/telemetry/crash/report', reportCrash);
router.post('/feedback/submit', submitFeedback);
router.post('/feedback', submitFeedback);

// Tương thích ngược endpoint Ladon cũ
router.post('/ladon/notify2/report', ingestBatchEvents);
router.get('/ladon/notify2/report', (req, res) => res.json({ code: 0, status: 'ok' }));

export default router;

