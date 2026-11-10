import { Router } from 'express';
import {
  adminLogin,
  getDashboardStats,
  getAdminDevices,
  toggleBanDevice,
  getAdminMCDS,
  createMCDSPromote,
  deleteMCDSPromote,
  getAdminAds,
  updateAdminGlobalAdKeys,
  createAdminAdPlacement,
  updateAdPlacement,
  deleteAdminAdPlacement,
  getAdminPlans,
  getAdminLedger,
  getAdminLogs,
  getAdminFamilyProducts,
  createAdminFamilyProduct,
  updateAdminFamilyProduct,
  deleteAdminFamilyProduct,
  getAdminCoinOverview,
  createAdminCoinTask,
  updateAdminCoinTask,
  deleteAdminCoinTask,
  createAdminCoinReward,
  updateAdminCoinReward,
  deleteAdminCoinReward,
  processAdminCoinRedemption,
  adjustAdminUserCoin,
  getAdminUsers,
  createAdminUser,
  updateAdminUser,
  changeAdminPassword,
  deleteAdminUser
} from '../controllers/admin.controller';
import { authenticateAdmin } from '../middlewares/auth.middleware';

const router = Router();

// Public Admin Auth
router.post('/login', adminLogin);

// Admin User Accounts & RBAC
router.get('/users', authenticateAdmin, getAdminUsers);
router.post('/users', authenticateAdmin, createAdminUser);
router.put('/users/:id', authenticateAdmin, updateAdminUser);
router.put('/users/:id/password', authenticateAdmin, changeAdminPassword);
router.delete('/users/:id', authenticateAdmin, deleteAdminUser);

// Protected Admin Operations
router.get('/stats', authenticateAdmin, getDashboardStats);
router.get('/devices', authenticateAdmin, getAdminDevices);
router.post('/devices/toggle-ban', authenticateAdmin, toggleBanDevice);
router.get('/mcds', authenticateAdmin, getAdminMCDS);
router.post('/mcds/promotes', authenticateAdmin, createMCDSPromote);
router.delete('/mcds/promotes/:id', authenticateAdmin, deleteMCDSPromote);

// Ads Mediation & Global Keys
router.get('/ads', authenticateAdmin, getAdminAds);
router.post('/ads/global-keys', authenticateAdmin, updateAdminGlobalAdKeys);
router.post('/ads/placements', authenticateAdmin, createAdminAdPlacement);
router.post('/ads/update', authenticateAdmin, updateAdPlacement);
router.delete('/ads/placements/:placement_id', authenticateAdmin, deleteAdminAdPlacement);

// Subscription & IAP
router.get('/plans', authenticateAdmin, getAdminPlans);

// SHAREit Family & CPI Cross-Promo
router.get('/family-products', authenticateAdmin, getAdminFamilyProducts);
router.post('/family-products', authenticateAdmin, createAdminFamilyProduct);
router.put('/family-products/:id', authenticateAdmin, updateAdminFamilyProduct);
router.delete('/family-products/:id', authenticateAdmin, deleteAdminFamilyProduct);

// Coin Economy & Reward Shop
router.get('/coin/overview', authenticateAdmin, getAdminCoinOverview);
router.post('/coin/tasks', authenticateAdmin, createAdminCoinTask);
router.put('/coin/tasks/:id', authenticateAdmin, updateAdminCoinTask);
router.delete('/coin/tasks/:id', authenticateAdmin, deleteAdminCoinTask);
router.post('/coin/rewards', authenticateAdmin, createAdminCoinReward);
router.put('/coin/rewards/:id', authenticateAdmin, updateAdminCoinReward);
router.delete('/coin/rewards/:id', authenticateAdmin, deleteAdminCoinReward);
router.post('/coin/redemptions/:id/process', authenticateAdmin, processAdminCoinRedemption);
router.post('/coin/adjust', authenticateAdmin, adjustAdminUserCoin);

router.get('/ledger', authenticateAdmin, getAdminLedger);
router.get('/logs', authenticateAdmin, getAdminLogs);

export default router;

