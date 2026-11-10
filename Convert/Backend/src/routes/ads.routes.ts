import { Router } from 'express';
import { getAdStrategy, trackImpression, trackClick, trackCPI } from '../controllers/ads.controller';

const router = Router();

// Endpoint mới v1
router.post('/strategy/get', getAdStrategy);
router.post('/ads/strategy/get', getAdStrategy);
router.post('/track/impression', trackImpression);
router.post('/track/click', trackClick);
router.post('/attribution/cpi', trackCPI);

// Tương thích ngược với các endpoint cũ từ DEX của SHAREit
router.get('/shareit/get_ads', (req, res) => getAdStrategy(req, res));
router.post('/collect', (req, res) => trackImpression(req, res));
router.post('/client/ci', (req, res) => trackClick(req, res));

export default router;

