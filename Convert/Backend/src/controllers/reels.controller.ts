import { Request, Response } from 'express';
import fs from 'fs';
import path from 'path';
import { config } from '../config';
import { facebookReelsService, normalizeFacebookInput, SupportedPlatform } from '../services/facebookReels.service';

export const startReelsDownload = async (req: Request, res: Response) => {
  try {
    const rawUrl = String(
      req.body?.url ||
        req.body?.pageUrl ||
        req.body?.link ||
        req.query?.url ||
        req.query?.pageUrl ||
        ''
    ).trim();

    if (!rawUrl) {
      return res.status(400).json({
        success: false,
        error:
          'Thiếu tham số "url" (ví dụ: https://www.facebook.com/samfamily.6826/, https://www.instagram.com/instagram/reels/, https://www.youtube.com/@YouTube/shorts).',
      });
    }

    const maxReels = Number(req.body?.maxReels ?? req.query?.maxReels ?? 0);
    const preferQualityRaw = String(req.body?.preferQuality ?? req.query?.preferQuality ?? '1080p').toLowerCase();
    const preferQuality =
      preferQualityRaw === 'sd' ? 'sd' : preferQualityRaw === 'hd' || preferQualityRaw === '720p' ? 'hd' : '1080p';
    const forceRedownload =
      req.body?.forceRedownload === true ||
      req.body?.forceRedownload === 'true' ||
      req.query?.forceRedownload === '1' ||
      req.query?.forceRedownload === 'true';
    const incrementalOnly =
      req.body?.incrementalOnly === true ||
      req.body?.incrementalOnly === 'true' ||
      req.query?.incrementalOnly === '1' ||
      req.query?.incrementalOnly === 'true';
    const outputDir = String(req.body?.outputDir ?? req.query?.outputDir ?? '').trim() || undefined;
    const concurrency = Number(req.body?.concurrency ?? req.query?.concurrency ?? 3);
    const shouldWait =
      req.body?.wait === true ||
      req.body?.wait === 'true' ||
      req.query?.wait === '1' ||
      req.query?.wait === 'true';

    const job = facebookReelsService.startPageDownloadJob({
      url: rawUrl,
      maxReels,
      preferQuality,
      forceRedownload,
      incrementalOnly,
      outputDir,
      concurrency,
    });

    if (shouldWait) {
      const finishedJob = await facebookReelsService.waitForJobCompletion(job.jobId);
      return res.status(200).json({
        success: finishedJob.status === 'COMPLETED' || finishedJob.status === 'PARTIAL',
        jobId: finishedJob.jobId,
        statusUrl: `/api/v1/reels/jobs/${finishedJob.jobId}`,
        job: finishedJob,
      });
    }

    return res.status(202).json({
      success: true,
      jobId: job.jobId,
      statusUrl: `/api/v1/reels/jobs/${job.jobId}`,
      job,
    });
  } catch (err: any) {
    return res.status(400).json({
      success: false,
      error: err?.message || 'Không thể khởi tạo tác vụ tải Reels.',
    });
  }
};

export const previewNormalizedUrl = (req: Request, res: Response) => {
  try {
    const rawUrl = String(req.query?.url || req.body?.url || '').trim();
    const normalized = normalizeFacebookInput(rawUrl);
    return res.json({ success: true, data: normalized });
  } catch (err: any) {
    return res.status(400).json({ success: false, error: err?.message || 'URL không hợp lệ' });
  }
};

export const listReelsJobs = (_req: Request, res: Response) => {
  return res.json({
    success: true,
    session: facebookReelsService.getSessionStatus(),
    jobs: facebookReelsService.getAllJobs(),
  });
};

export const getReelsJobById = (req: Request, res: Response) => {
  const job = facebookReelsService.getJob(req.params.jobId);
  if (!job) {
    return res.status(404).json({ success: false, error: 'Không tìm thấy jobId yêu cầu.' });
  }
  return res.json({ success: true, job });
};

export const cancelReelsJobById = (req: Request, res: Response) => {
  const job = facebookReelsService.cancelJob(req.params.jobId);
  if (!job) {
    return res.status(404).json({ success: false, error: 'Không tìm thấy jobId yêu cầu.' });
  }
  return res.json({ success: true, job });
};

export const getReelsLibrary = (_req: Request, res: Response) => {
  return res.json({
    success: true,
    ...facebookReelsService.listLibraryPages(),
  });
};

export const streamLibraryReelFile = (req: Request, res: Response) => {
  try {
    const safeSlug = path.basename(req.params.pageSlug || '');
    const safeFile = path.basename(req.params.fileName || '');
    if (!safeSlug || !safeFile || !safeFile.toLowerCase().endsWith('.mp4')) {
      return res.status(400).json({ success: false, error: 'Tên tệp không hợp lệ.' });
    }
    const targetPath = path.join(config.videoLibraryDir, safeSlug, safeFile);
    if (!fs.existsSync(targetPath)) {
      return res.status(404).json({ success: false, error: 'Không tìm thấy tệp video.' });
    }
    return res.sendFile(targetPath);
  } catch (err: any) {
    return res.status(500).json({ success: false, error: err?.message || 'Lỗi đọc tệp video.' });
  }
};

export const getFacebookSessionStatus = (_req: Request, res: Response) => {
  return res.json({
    success: true,
    session: facebookReelsService.getSessionStatus(),
  });
};

export const openFacebookSessionLogin = async (req: Request, res: Response) => {
  try {
    const rawPlatform = String(req.body?.platform || req.query?.platform || 'facebook').toLowerCase();
    const platform: SupportedPlatform =
      rawPlatform === 'instagram' ? 'instagram' : rawPlatform === 'youtube' ? 'youtube' : 'facebook';
    const result = await facebookReelsService.openInteractiveLoginWindow(platform);
    return res.json({ success: true, ...result });
  } catch (err: any) {
    return res.status(409).json({ success: false, error: err?.message || 'Không thể mở Chrome đăng nhập.' });
  }
};

export const closeFacebookSessionLogin = async (_req: Request, res: Response) => {
  const result = await facebookReelsService.closeInteractiveLoginWindow();
  return res.json({ success: true, ...result });
};

// ============================================================================
// FAVORITE CHANNELS / PAGES & DAILY AUTO-SCAN SCHEDULER
// ============================================================================

export const getFavorites = (_req: Request, res: Response) => {
  return res.json({
    success: true,
    ...facebookReelsService.getFavoritesState(),
  });
};

export const addOrUpdateFavorite = (req: Request, res: Response) => {
  try {
    const url = String(req.body?.url || req.body?.pageUrl || req.query?.url || '').trim();
    if (!url) {
      return res.status(400).json({
        success: false,
        error: 'Thiếu URL Page/Kênh cần lưu vào danh sách yêu thích.',
      });
    }
    const title = req.body?.title ? String(req.body.title).trim() : undefined;
    const enabled =
      req.body?.enabled !== undefined
        ? Boolean(req.body.enabled)
        : req.body?.autoScanEnabled !== undefined
        ? Boolean(req.body.autoScanEnabled)
        : true;
    const checkIntervalMinutes = Number(req.body?.checkIntervalMinutes || 0) || undefined;
    const maxReelsPerScan =
      req.body?.maxReelsPerScan !== undefined ? Number(req.body.maxReelsPerScan) : undefined;
    const preferQualityRaw = String(req.body?.preferQuality || '1080p').toLowerCase();
    const preferQuality =
      preferQualityRaw === 'sd' ? 'sd' : preferQualityRaw === 'hd' ? 'hd' : '1080p';
    const scanNow =
      req.body?.scanNow === true || req.body?.scanNow === 'true' || req.query?.scanNow === '1';

    const { favorite, job: triggeredJob } = facebookReelsService.addOrUpdateFavorite({
      url,
      title,
      enabled,
      checkIntervalMinutes,
      maxReelsPerScan,
      preferQuality,
      scanNow,
    });

    return res.json({
      success: true,
      favorite,
      triggeredJob: triggeredJob || null,
      ...facebookReelsService.getFavoritesState(),
    });
  } catch (err: any) {
    return res.status(400).json({
      success: false,
      error: err?.message || 'Không thể lưu Page/Kênh yêu thích.',
    });
  }
};

export const updateFavorite = (req: Request, res: Response) => {
  try {
    const enabledPatch =
      req.body?.enabled !== undefined
        ? Boolean(req.body.enabled)
        : req.body?.autoScanEnabled !== undefined
        ? Boolean(req.body.autoScanEnabled)
        : undefined;
    const favorite = facebookReelsService.updateFavoriteById(req.params.id, {
      title: req.body?.title,
      enabled: enabledPatch,
      checkIntervalMinutes: req.body?.checkIntervalMinutes,
      maxReelsPerScan: req.body?.maxReelsPerScan,
      preferQuality: req.body?.preferQuality,
    });
    if (!favorite) {
      return res.status(404).json({ success: false, error: 'Không tìm thấy kênh yêu thích.' });
    }
    return res.json({
      success: true,
      favorite,
      ...facebookReelsService.getFavoritesState(),
    });
  } catch (err: any) {
    return res.status(400).json({
      success: false,
      error: err?.message || 'Không thể cập nhật kênh yêu thích.',
    });
  }
};

export const deleteFavorite = (req: Request, res: Response) => {
  const removed = facebookReelsService.removeFavoriteById(req.params.id);
  if (!removed) {
    return res.status(404).json({ success: false, error: 'Không tìm thấy kênh yêu thích cần xóa.' });
  }
  return res.json({
    success: true,
    ...facebookReelsService.getFavoritesState(),
  });
};

export const scanSingleFavorite = async (req: Request, res: Response) => {
  try {
    const shouldWait =
      req.body?.wait === true ||
      req.body?.wait === 'true' ||
      req.query?.wait === '1' ||
      req.query?.wait === 'true';
    const job = facebookReelsService.triggerFavoriteScan(req.params.id, 'FAVORITE_MANUAL');

    if (shouldWait) {
      const finishedJob = await facebookReelsService.waitForJobCompletion(job.jobId);
      const favorite = facebookReelsService.getFavoritesState().favorites.find((f) => f.id === req.params.id);
      return res.json({
        success: true,
        favorite,
        jobId: finishedJob.jobId,
        job: finishedJob,
      });
    }

    const favorite = facebookReelsService.getFavoritesState().favorites.find((f) => f.id === req.params.id);
    return res.status(202).json({
      success: true,
      favorite,
      jobId: job.jobId,
      job,
    });
  } catch (err: any) {
    return res.status(400).json({
      success: false,
      error: err?.message || 'Không thể quét kênh yêu thích.',
    });
  }
};

export const scanAllFavorites = async (_req: Request, res: Response) => {
  const jobs = await facebookReelsService.scanAllFavoritesNow();
  return res.status(202).json({
    success: true,
    jobs,
    ...facebookReelsService.getFavoritesState(),
  });
};

export const updateFavoritesScheduler = (req: Request, res: Response) => {
  const state = facebookReelsService.updateSchedulerSettings({
    schedulerEnabled: req.body?.schedulerEnabled,
    defaultIntervalMinutes: req.body?.defaultIntervalMinutes,
  });
  return res.json({
    success: true,
    ...state,
  });
};
