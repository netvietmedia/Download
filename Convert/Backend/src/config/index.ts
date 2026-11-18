import dotenv from 'dotenv';
import path from 'path';
dotenv.config();

export const config = {
  port: parseInt(process.env.PORT || '6030', 10),
  nodeEnv: process.env.NODE_ENV || 'development',
  jwtSecret: process.env.JWT_SECRET || 'shareit_super_secret_jwt_key_2026_antigravity',
  appSigningSecret: process.env.APP_SIGNING_SECRET || 'shareit_hmac_sha256_master_key_9182390123',
  cmsAdminUser: process.env.CMS_ADMIN_USER || 'admin',
  cmsAdminPass: process.env.CMS_ADMIN_PASS || 'admin@123456',
  uploadDir: process.env.UPLOAD_DIR || './uploads',
  relayTempDir: process.env.RELAY_TEMP_DIR || './relay_chunks',
  videoLibraryDir: process.env.VIDEO_LIBRARY_DIR || path.resolve(__dirname, '../../../../../VideoLibrary'),
  browserProfileDir: process.env.BROWSER_PROFILE_DIR || path.resolve(__dirname, '../../.browser-profile'),
  reelsJobsFile: path.resolve(__dirname, '../../reels_jobs.json'),
  reelsFavoritesFile: path.resolve(__dirname, '../../reels_favorites.json'),
  ytDlpBin: path.resolve(__dirname, '../../bin/yt-dlp.exe'),
};

