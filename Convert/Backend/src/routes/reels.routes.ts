import { Router } from 'express';
import {
  startReelsDownload,
  previewNormalizedUrl,
  listReelsJobs,
  getReelsJobById,
  cancelReelsJobById,
  getReelsLibrary,
  streamLibraryReelFile,
  getFacebookSessionStatus,
  openFacebookSessionLogin,
  closeFacebookSessionLogin,
  getFavorites,
  addOrUpdateFavorite,
  updateFavorite,
  deleteFavorite,
  scanSingleFavorite,
  scanAllFavorites,
  updateFavoritesScheduler,
} from '../controllers/reels.controller';

const router = Router();

// AI Agent & Web UI 1-Step Endpoints (Facebook Page / Instagram Channel / YouTube Channel)
router.post('/auto-download', startReelsDownload);
router.get('/auto-download', startReelsDownload);
router.post('/jobs', startReelsDownload);
router.get('/jobs', listReelsJobs);
router.get('/jobs/:jobId', getReelsJobById);
router.post('/jobs/:jobId/cancel', cancelReelsJobById);

// Favorite Channels / Pages & Daily Auto-Scan Watchlist
router.get('/favorites', getFavorites);
router.post('/favorites', addOrUpdateFavorite);
router.post('/favorites/scan-all', scanAllFavorites);
router.post('/favorites/scheduler', updateFavoritesScheduler);
router.put('/favorites/:id', updateFavorite);
router.delete('/favorites/:id', deleteFavorite);
router.post('/favorites/:id/scan', scanSingleFavorite);

// URL Normalizer helper
router.get('/normalize', previewNormalizedUrl);

// VideoLibrary inspection & local MP4 streaming
router.get('/library', getReelsLibrary);
router.get('/file/:pageSlug/:fileName', streamLibraryReelFile);

// Optional Browser Login Session helper (Facebook / Instagram / YouTube)
router.get('/session/status', getFacebookSessionStatus);
router.post('/session/login', openFacebookSessionLogin);
router.post('/session/close', closeFacebookSessionLogin);

export default router;
