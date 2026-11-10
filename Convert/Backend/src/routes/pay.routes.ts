import { Router } from 'express';
import { getSubscriptionPlans, createOrder, verifyOrder, getSubscriptionStatus, googleWebhook } from '../controllers/pay.controller';

const router = Router();

router.get('/plans', getSubscriptionPlans);
router.get('/pay/plans', getSubscriptionPlans);
router.post('/order/create', createOrder);
router.post('/pay/order/create', createOrder);
router.post('/order/verify', verifyOrder);
router.post('/pay/order/verify', verifyOrder);
router.get('/subscription/status', getSubscriptionStatus);
router.get('/pay/subscription/status', getSubscriptionStatus);
router.post('/webhook/google', googleWebhook);

export default router;

