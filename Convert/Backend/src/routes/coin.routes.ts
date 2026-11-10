import { Router } from 'express';
import { getCoinBalance, getTaskList, dailyCheckin, claimTask, redeemReward } from '../controllers/coin.controller';

const router = Router();

router.get('/balance', getCoinBalance);
router.get('/coin/balance', getCoinBalance);
router.post('/task/list', getTaskList);
router.post('/coin/task/list', getTaskList);
router.post('/checkin', dailyCheckin);
router.post('/coin/checkin', dailyCheckin);
router.post('/task/claim', claimTask);
router.post('/coin/task/claim', claimTask);
router.post('/redeem', redeemReward);
router.post('/coin/redeem', redeemReward);

export default router;

