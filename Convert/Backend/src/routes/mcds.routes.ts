import { Router } from 'express';
import { fetchMCDS, initAppConfig, checkUpgrade, getFamilyProducts } from '../controllers/mcds.controller';

const router = Router();

router.post('/fetch', fetchMCDS);
router.post('/mcds/fetch', fetchMCDS);
router.post('/config/app/init', initAppConfig);
router.post('/init', initAppConfig);
router.post('/upgrade/check', checkUpgrade);
router.post('/upgrade', checkUpgrade);
router.get('/family-products', getFamilyProducts);
router.post('/family-products', getFamilyProducts);

export default router;
