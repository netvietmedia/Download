import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import https from 'https';
import http from 'http';
import { execFile } from 'child_process';
import { promisify } from 'util';
import { chromium, BrowserContext, Page } from 'playwright';
import ffmpegStatic from 'ffmpeg-static';
import { config } from '../config';

const execFileAsync = promisify(execFile);
const FFMPEG_BIN: string = (ffmpegStatic as unknown as string) || 'ffmpeg';

export type SupportedPlatform = 'facebook' | 'instagram' | 'youtube';

export type JobStatus =
  | 'QUEUED'
  | 'SCANNING'
  | 'DOWNLOADING'
  | 'COMPLETED'
  | 'PARTIAL'
  | 'NEEDS_LOGIN'
  | 'BLOCKED_CAPTCHA'
  | 'CANCELLED'
  | 'FAILED';

export type ReelItemStatus =
  | 'DISCOVERED'
  | 'DOWNLOADING'
  | 'MUXING_AUDIO'
  | 'COMPLETED'
  | 'SKIPPED_EXISTING'
  | 'FAILED';

export interface ReelStreamCandidate {
  reelId: string;
  reelUrl: string;
  title?: string;
  caption?: string;
  hashtags?: string[];
  viewCountText?: string;
  thumbnailUrl?: string;
  durationSec?: number;
  publishTime?: number;
  hdUrl?: string;
  sdUrl?: string;
  dashVideoUrl?: string;
  dashVideoHeight?: number;
  dashVideoWidth?: number;
  dashVideoBitrate?: number;
  dashAudioUrl?: string;
  dashAudioBitrate?: number;
}

export interface ReelDownloadItem {
  index: number;
  reelId: string;
  reelUrl: string;
  title?: string;
  caption: string;
  hashtags?: string[];
  viewCountText: string;
  durationSec: number;
  publishTime: number | null;
  qualityLabel: string;
  width: number;
  height: number;
  hasAudio: boolean;
  sizeBytes: number;
  sha256: string;
  fileName: string;
  filePath: string;
  status: ReelItemStatus;
  error?: string;
  downloadedAt?: string;
}

export interface ReelsDownloadOptions {
  url: string;
  maxReels?: number; // 0 or undefined = ALL reels/videos on channel
  preferQuality?: '1080p' | 'hd' | 'sd';
  forceRedownload?: boolean;
  outputDir?: string;
  concurrency?: number;
  incrementalOnly?: boolean; // Stop scanning early once existing downloaded videos are reached
  favoriteId?: string;
  triggeredBy?: 'MANUAL' | 'FAVORITE_AUTO' | 'FAVORITE_MANUAL' | 'API';
}

export interface ReelsDownloadJob {
  jobId: string;
  platform: SupportedPlatform;
  status: JobStatus;
  inputUrl: string;
  normalizedPageUrl: string;
  reelsTabUrl: string;
  pageSlug: string;
  pageTitle: string;
  isSingleReel: boolean;
  incrementalOnly: boolean;
  favoriteId: string | null;
  triggeredBy: string;
  outputDir: string;
  manifestPath: string;
  preferQuality: '1080p' | 'hd' | 'sd';
  maxReels: number;
  forceRedownload: boolean;
  totalDiscovered: number;
  processedCount: number;
  downloadedCount: number;
  skippedCount: number;
  failedCount: number;
  progressPercent: number;
  hasReachedEndOfPage: boolean;
  loginRecommended: boolean;
  message: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  cancelRequested?: boolean;
  logs: string[];
  items: ReelDownloadItem[];
}

export interface NormalizedChannelTarget {
  platform: SupportedPlatform;
  inputUrl: string;
  normalizedPageUrl: string;
  reelsTabUrl: string;
  pageSlug: string;
  isSingleReel: boolean;
  singleReelId: string | null;
}

export interface FavoriteChannel {
  id: string;
  platform: SupportedPlatform;
  url: string;
  normalizedPageUrl: string;
  reelsTabUrl: string;
  pageSlug: string;
  title: string;
  enabled: boolean;
  preferQuality: '1080p' | 'hd' | 'sd';
  maxReelsPerScan: number;
  checkIntervalMinutes: number;
  lastCheckedAt: string | null;
  nextCheckAt: string | null;
  lastNewVideoAt: string | null;
  lastJobId: string | null;
  lastStatus: string;
  lastNewVideosCount: number;
  totalDownloadedInLibrary: number;
  createdAt: string;
}

export interface FavoritesStoreData {
  schedulerEnabled: boolean;
  defaultIntervalMinutes: number;
  lastSchedulerTickAt: string | null;
  favorites: FavoriteChannel[];
}

interface MetaEfgParsed {
  videoId: string;
  xpvAssetId: string;
  vencodeTag: string;
  isAudioOnly: boolean;
  isDashVideoOnly: boolean;
  durationSec: number;
  bitrate: number;
  qualityRank: number;
}

interface ProbedMediaInfo {
  hasVideo: boolean;
  hasAudio: boolean;
  width: number;
  height: number;
  durationSec: number;
}

const DESKTOP_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36';

export function unescapeFbUrl(raw: string): string {
  if (!raw) return '';
  return raw
    .trim()
    .replace(/\\\//g, '/')
    .replace(/\\u0026/gi, '&')
    .replace(/\\u0025/gi, '%')
    .replace(/\\u003d/gi, '=')
    .replace(/\\u003c/gi, '<')
    .replace(/\\u003e/gi, '>')
    .replace(/\\"/g, '"')
    .replace(/&amp;/g, '&');
}

export function stripByteRangeParams(rawUrl: string): string {
  return unescapeFbUrl(rawUrl)
    .replace(/([?&])bytestart=\d+&?/g, '$1')
    .replace(/([?&])byteend=\d+&?/g, '$1')
    .replace(/\?&/g, '?')
    .replace(/[?&]+$/g, '');
}

export function sanitizeFolderSlug(raw: string): string {
  const cleaned = raw
    .trim()
    .replace(/[<>:"/\\|?*\x00-\x1F]/g, '_')
    .replace(/\s+/g, '_')
    .replace(/^[._]+|[.\s]+$/g, '');
  return cleaned || 'channel_videos';
}

export interface ParsedVideoTextMeta {
  title: string;
  caption: string;
  hashtags: string[];
}

export function extractCaptionTitleAndHashtags(
  rawCaption?: string,
  rawTitle?: string,
  rawTags?: string[]
): ParsedVideoTextMeta {
  const cleanStr = (s?: string) =>
    (s || '')
      .replace(/[\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF]/g, '')
      .trim();

  const cap = cleanStr(rawCaption);
  let tit = cleanStr(rawTitle);

  if (/^Video by /i.test(tit)) {
    tit = '';
  }
  if (tit && /\bviews\b.*\|.*\|/i.test(tit)) {
    const parts = tit.split('|').map((p) => p.trim());
    if (parts.length >= 2) {
      tit = parts[1];
    }
  }

  const combinedSource = [cap, tit].filter(Boolean).join('\n');
  const hashtagRegex = /#[\p{L}\p{N}_]+/gu;
  const seenLower = new Set<string>();
  const hashtags: string[] = [];

  const pushTag = (tagRaw: string) => {
    const cleaned = String(tagRaw || '').trim().replace(/^#+/, '');
    if (!cleaned) return;
    const normalized = cleaned.replace(/[^\p{L}\p{N}_]/gu, '');
    if (!normalized) return;
    const tag = `#${normalized}`;
    const low = tag.toLowerCase();
    if (!seenLower.has(low)) {
      seenLower.add(low);
      hashtags.push(tag);
    }
  };

  const matches = combinedSource.match(hashtagRegex) || [];
  for (const m of matches) {
    pushTag(m);
  }
  if (Array.isArray(rawTags)) {
    for (const t of rawTags) {
      if (typeof t === 'string' && t.trim()) {
        pushTag(t);
      }
    }
  }

  const baseText = cap || tit;
  const bodyWithoutHashtags = baseText
    .replace(hashtagRegex, ' ')
    .replace(/\s+/g, ' ')
    .replace(/^[\s\-–—•|,:;]+|[\s\-–—•|,:;]+$/g, '')
    .trim();

  const title = bodyWithoutHashtags || (hashtags.length > 0 ? hashtags.join(' ') : '');
  const fullCaption =
    baseText ||
    [bodyWithoutHashtags, hashtags.join(' ')].filter(Boolean).join('\n');

  return {
    title,
    caption: fullCaption,
    hashtags,
  };
}

export function sanitizeForWindowsFileName(input: string): string {
  return (input || '')
    .replace(/[\u0000-\u001F\u007F-\u009F\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF]/g, ' ')
    .replace(/[\u{1F000}-\u{1FFFF}\u{2600}-\u{27BF}]/gu, ' ')
    .replace(/[<>:"/\\|?*]/g, ' - ')
    .replace(/\s+/g, ' ')
    .replace(/(?:\s*-\s*)+/g, ' - ')
    .replace(/^[\s.\-_]+|[\s.\-_]+$/g, '')
    .trim();
}

export function buildVideoFileName(
  reelId: string,
  rawCaption?: string,
  rawTitle?: string,
  rawTags?: string[]
): string {
  const safeId = String(reelId || '').replace(/[^A-Za-z0-9_-]/g, '');
  const meta = extractCaptionTitleAndHashtags(rawCaption, rawTitle, rawTags);

  let cleanBody = sanitizeForWindowsFileName(meta.title);
  if (cleanBody.length > 95) {
    cleanBody = cleanBody.slice(0, 95).replace(/\s+\S*$/, '').trim();
  }

  let cleanTags = sanitizeForWindowsFileName(meta.hashtags.slice(0, 8).join(' '));
  if (cleanTags.length > 65) {
    cleanTags = cleanTags.slice(0, 65).replace(/\s+\S*$/, '').trim();
  }

  const parts: string[] = [];
  if (cleanBody) parts.push(cleanBody);
  if (cleanTags && cleanTags.toLowerCase() !== cleanBody.toLowerCase()) {
    parts.push(cleanTags);
  }

  if (parts.length === 0) {
    return `${safeId}.mp4`;
  }

  const prefix = parts.join(' - ').replace(/^[\s.\-_]+|[\s.\-_]+$/g, '');
  return `${prefix} [${safeId}].mp4`;
}

export function extractReelIdFromFileName(fileName: string): string {
  const base = path.basename(fileName || '');
  if (!base.toLowerCase().endsWith('.mp4')) return '';
  const withoutExt = base.slice(0, -4).trim();
  const bracketMatch = withoutExt.match(/\[([A-Za-z0-9_-]{6,30})\]$/);
  if (bracketMatch && bracketMatch[1]) {
    return bracketMatch[1];
  }
  return withoutExt;
}

export function normalizeFacebookInput(rawInput: string): NormalizedChannelTarget {
  let trimmed = (rawInput || '').trim();
  if (!trimmed) {
    throw new Error('URL Page / Kênh không được để trống.');
  }

  // Shorthand handle support
  if (!/^https?:\/\//i.test(trimmed)) {
    const low = trimmed.toLowerCase();
    if (
      low.includes('facebook.com') ||
      low.includes('fb.watch') ||
      low.includes('fb.com') ||
      low.includes('instagram.com') ||
      low.includes('youtube.com') ||
      low.includes('youtu.be')
    ) {
      trimmed = 'https://' + trimmed.replace(/^\/+/, '');
    } else if (trimmed.startsWith('@')) {
      trimmed = `https://www.youtube.com/${trimmed}`;
    } else {
      trimmed = `https://www.facebook.com/${trimmed.replace(/^\/+/, '')}`;
    }
  }

  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    throw new Error(`URL không hợp lệ: ${rawInput}`);
  }

  const host = parsed.hostname.toLowerCase();

  // ============================================================================
  // 1. YOUTUBE CHANNEL / SHORTS / VIDEOS
  // ============================================================================
  if (host.includes('youtube.com') || host.includes('youtu.be')) {
    if (host.includes('youtu.be')) {
      const vid = parsed.pathname.replace(/^\/+/, '').split('/')[0];
      if (!vid) throw new Error('URL YouTube rút gọn không hợp lệ.');
      const watchUrl = `https://www.youtube.com/watch?v=${vid}`;
      return {
        platform: 'youtube',
        inputUrl: rawInput.trim(),
        normalizedPageUrl: watchUrl,
        reelsTabUrl: watchUrl,
        pageSlug: 'yt_single_videos',
        isSingleReel: true,
        singleReelId: vid,
      };
    }

    parsed.hostname = 'www.youtube.com';
    const pathname = parsed.pathname.replace(/\/+$/, '');
    const watchVid = parsed.searchParams.get('v');
    const shortsMatch = pathname.match(/^\/shorts\/([A-Za-z0-9_-]{6,20})/i);

    if (shortsMatch || watchVid) {
      const vid = shortsMatch ? shortsMatch[1] : watchVid!;
      const singleUrl = shortsMatch
        ? `https://www.youtube.com/shorts/${vid}`
        : `https://www.youtube.com/watch?v=${vid}`;
      return {
        platform: 'youtube',
        inputUrl: rawInput.trim(),
        normalizedPageUrl: singleUrl,
        reelsTabUrl: singleUrl,
        pageSlug: 'yt_single_videos',
        isSingleReel: true,
        singleReelId: vid,
      };
    }

    // Channel formats: /@handle, /@handle/shorts, /@handle/videos, /channel/UC..., /c/..., /user/...
    const parts = pathname.split('/').filter(Boolean);
    if (parts.length === 0) {
      throw new Error('Vui lòng nhập URL Kênh YouTube cụ thể (ví dụ: https://www.youtube.com/@YouTube).');
    }

    let channelBase = '';
    let rawSlug = '';
    let subTab = 'shorts';

    if (parts[0].startsWith('@')) {
      channelBase = `https://www.youtube.com/${parts[0]}`;
      rawSlug = parts[0].slice(1);
      if (parts[1] && ['shorts', 'videos', 'streams'].includes(parts[1].toLowerCase())) {
        subTab = parts[1].toLowerCase();
      }
    } else if (['channel', 'c', 'user'].includes(parts[0].toLowerCase()) && parts.length >= 2) {
      channelBase = `https://www.youtube.com/${parts[0]}/${parts[1]}`;
      rawSlug = parts[1];
      if (parts[2] && ['shorts', 'videos', 'streams'].includes(parts[2].toLowerCase())) {
        subTab = parts[2].toLowerCase();
      }
    } else {
      channelBase = `https://www.youtube.com/${parts[0]}`;
      rawSlug = parts[0];
      if (parts[1] && ['shorts', 'videos', 'streams'].includes(parts[1].toLowerCase())) {
        subTab = parts[1].toLowerCase();
      }
    }

    const cleanSlug = sanitizeFolderSlug(`yt_${decodeURIComponent(rawSlug)}`);
    return {
      platform: 'youtube',
      inputUrl: rawInput.trim(),
      normalizedPageUrl: `${channelBase}/`,
      reelsTabUrl: `${channelBase}/${subTab}`,
      pageSlug: cleanSlug,
      isSingleReel: false,
      singleReelId: null,
    };
  }

  // ============================================================================
  // 2. INSTAGRAM PROFILE / CHANNEL / REELS
  // ============================================================================
  if (host.includes('instagram.com')) {
    parsed.hostname = 'www.instagram.com';
    const pathname = parsed.pathname.replace(/\/+$/, '');
    const singleMatch = pathname.match(/^\/(?:reel|reels|p|tv)\/([A-Za-z0-9_-]{5,25})/i);
    if (singleMatch && singleMatch[1]) {
      const code = singleMatch[1];
      const singleUrl = `https://www.instagram.com/reel/${code}/`;
      return {
        platform: 'instagram',
        inputUrl: rawInput.trim(),
        normalizedPageUrl: singleUrl,
        reelsTabUrl: singleUrl,
        pageSlug: 'ig_single_reels',
        isSingleReel: true,
        singleReelId: code,
      };
    }

    const parts = pathname.split('/').filter(Boolean);
    if (parts.length === 0 || ['explore', 'accounts', 'direct', 'stories'].includes(parts[0].toLowerCase())) {
      throw new Error('Vui lòng nhập URL Kênh/Profile Instagram cụ thể (ví dụ: https://www.instagram.com/instagram/).');
    }

    const username = parts[0].replace(/^@/, '');
    const cleanSlug = sanitizeFolderSlug(`ig_${decodeURIComponent(username)}`);
    return {
      platform: 'instagram',
      inputUrl: rawInput.trim(),
      normalizedPageUrl: `https://www.instagram.com/${username}/`,
      reelsTabUrl: `https://www.instagram.com/${username}/reels/`,
      pageSlug: cleanSlug,
      isSingleReel: false,
      singleReelId: null,
    };
  }

  // ============================================================================
  // 3. FACEBOOK PAGE / PROFILE / REELS
  // ============================================================================
  if (host === 'm.facebook.com' || host === 'web.facebook.com' || host === 'touch.facebook.com' || host === 'fb.com') {
    parsed.hostname = 'www.facebook.com';
  }

  const pathname = parsed.pathname.replace(/\/+$/, '');
  const reelMatch = pathname.match(/\/reel\/(\d{8,25})/i) || pathname.match(/\/videos\/(\d{8,25})/i);
  const watchVid = parsed.searchParams.get('v');

  if (reelMatch || (watchVid && /^\d{8,25}$/.test(watchVid))) {
    const rid = reelMatch ? reelMatch[1] : watchVid!;
    const singleUrl = `https://www.facebook.com/reel/${rid}/`;
    return {
      platform: 'facebook',
      inputUrl: rawInput.trim(),
      normalizedPageUrl: singleUrl,
      reelsTabUrl: singleUrl,
      pageSlug: 'single_reels',
      isSingleReel: true,
      singleReelId: rid,
    };
  }

  if (pathname.toLowerCase() === '/profile.php') {
    const profileId = parsed.searchParams.get('id') || 'unknown';
    const basePage = `https://www.facebook.com/profile.php?id=${encodeURIComponent(profileId)}`;
    return {
      platform: 'facebook',
      inputUrl: rawInput.trim(),
      normalizedPageUrl: basePage,
      reelsTabUrl: `${basePage}&sk=reels_tab`,
      pageSlug: sanitizeFolderSlug(`profile_${profileId}`),
      isSingleReel: false,
      singleReelId: null,
    };
  }

  const peopleMatch = pathname.match(/^\/people\/([^/]+)\/(\d{6,25})/i);
  if (peopleMatch) {
    const namePart = decodeURIComponent(peopleMatch[1]);
    const idPart = peopleMatch[2];
    const basePage = `https://www.facebook.com/profile.php?id=${idPart}`;
    return {
      platform: 'facebook',
      inputUrl: rawInput.trim(),
      normalizedPageUrl: basePage,
      reelsTabUrl: `${basePage}&sk=reels_tab`,
      pageSlug: sanitizeFolderSlug(`${namePart}_${idPart}`),
      isSingleReel: false,
      singleReelId: null,
    };
  }

  const segments = pathname.split('/').filter(Boolean);
  if (segments.length === 0) {
    throw new Error('Vui lòng nhập URL cụ thể của Page Facebook (ví dụ: https://www.facebook.com/samfamily.6826/).');
  }

  let pageSegment = segments[0];
  if (pageSegment.toLowerCase() === 'pg' && segments.length >= 2) {
    pageSegment = segments[1];
  }

  const cleanSlug = sanitizeFolderSlug(decodeURIComponent(pageSegment));
  const basePageUrl = `https://www.facebook.com/${pageSegment}/`;
  const reelsTabUrl = `https://www.facebook.com/${pageSegment}/reels/`;

  return {
    platform: 'facebook',
    inputUrl: rawInput.trim(),
    normalizedPageUrl: basePageUrl,
    reelsTabUrl,
    pageSlug: cleanSlug,
    isSingleReel: false,
    singleReelId: null,
  };
}

export function parseMetaEfg(rawUrl: string): MetaEfgParsed {
  const empty: MetaEfgParsed = {
    videoId: '',
    xpvAssetId: '',
    vencodeTag: '',
    isAudioOnly: false,
    isDashVideoOnly: false,
    durationSec: 0,
    bitrate: 0,
    qualityRank: 0,
  };
  try {
    const idx = rawUrl.indexOf('efg=');
    if (idx === -1) return empty;
    const rawParam = rawUrl.slice(idx + 4).split('&')[0];
    const decodedParam = decodeURIComponent(rawParam);
    const jsonStr = Buffer.from(decodedParam, 'base64').toString('utf8');
    const obj = JSON.parse(jsonStr);
    const vencodeTag = String(obj.vencode_tag || '').toLowerCase();
    const videoId = obj.video_id ? String(obj.video_id) : '';
    const xpvAssetId = obj.xpv_asset_id ? String(obj.xpv_asset_id) : '';
    const durationSec = Number(obj.duration_s || 0);
    const bitrate = Number(obj.bitrate || 0);

    const isAudioOnly =
      vencodeTag.includes('audio') ||
      vencodeTag.includes('heaac') ||
      vencodeTag.includes('aac') ||
      vencodeTag.includes('opus');

    const isDashVideoOnly =
      !isAudioOnly &&
      !vencodeTag.includes('progressive') &&
      (vencodeTag.startsWith('dash_') ||
        vencodeTag.includes('_dash') ||
        vencodeTag.includes('vp9') ||
        vencodeTag.includes('av1') ||
        vencodeTag.includes('h264-basic'));

    let qualityRank = 400;
    if (isAudioOnly) qualityRank = 1;
    else if (vencodeTag.includes('1080')) qualityRank = 1080;
    else if (vencodeTag.includes('720') || vencodeTag.includes('hd')) qualityRank = 720;
    else if (vencodeTag.includes('540')) qualityRank = 540;
    else if (vencodeTag.includes('480')) qualityRank = 480;
    else if (vencodeTag.includes('360')) qualityRank = 360;

    return {
      videoId: videoId || xpvAssetId,
      xpvAssetId,
      vencodeTag,
      isAudioOnly,
      isDashVideoOnly,
      durationSec,
      bitrate,
      qualityRank,
    };
  } catch {
    return empty;
  }
}

function parseDashManifestXml(rawXml: string): {
  bestVideoUrl?: string;
  bestVideoWidth?: number;
  bestVideoHeight?: number;
  bestVideoBitrate?: number;
  bestAudioUrl?: string;
  bestAudioBitrate?: number;
} {
  const xml = rawXml
    .replace(/\\u003C/gi, '<')
    .replace(/\\u003E/gi, '>')
    .replace(/\\u0026/gi, '&')
    .replace(/\\\//g, '/')
    .replace(/\\"/g, '"')
    .replace(/&amp;/g, '&');

  const repRegex = /<Representation\b([^>]*)>([\s\S]*?)<\/Representation>/gi;
  let bestVideoScore = -1;
  let bestVideoUrl: string | undefined;
  let bestVideoWidth = 0;
  let bestVideoHeight = 0;
  let bestVideoBitrate = 0;

  let bestAudioBitrate = -1;
  let bestAudioUrl: string | undefined;

  let m: RegExpExecArray | null;
  while ((m = repRegex.exec(xml)) !== null) {
    const attrs = m[1] || '';
    const inner = m[2] || '';
    const baseMatch = inner.match(/<BaseURL>([^<]+)<\/BaseURL>/i);
    if (!baseMatch || !baseMatch[1]) continue;

    const url = stripByteRangeParams(baseMatch[1].trim());
    if (!url.startsWith('http')) continue;

    const width = Number((attrs.match(/\bwidth="(\d+)"/i) || [])[1] || 0);
    const height = Number((attrs.match(/\bheight="(\d+)"/i) || [])[1] || 0);
    const bandwidth = Number((attrs.match(/\bbandwidth="(\d+)"/i) || [])[1] || 0);
    const mime = ((attrs.match(/\bmimeType="([^"]+)"/i) || [])[1] || '').toLowerCase();
    const codecs = ((attrs.match(/\bcodecs="([^"]+)"/i) || [])[1] || '').toLowerCase();
    const efg = parseMetaEfg(url);

    const isAudio =
      mime.startsWith('audio/') ||
      codecs.startsWith('mp4a') ||
      codecs.includes('opus') ||
      efg.isAudioOnly ||
      (width === 0 && height === 0 && (attrs.toLowerCase().includes('audio') || url.includes('audio')));

    if (isAudio) {
      const aScore = bandwidth || efg.bitrate || 64000;
      if (aScore > bestAudioBitrate) {
        bestAudioBitrate = aScore;
        bestAudioUrl = url;
      }
    } else {
      const effectiveDim = Math.max(width, height);
      const codecBonus = codecs.startsWith('avc1') ? 50_000 : 0;
      const score = effectiveDim * 10_000_000 + bandwidth + codecBonus;
      if (score > bestVideoScore) {
        bestVideoScore = score;
        bestVideoUrl = url;
        bestVideoWidth = width;
        bestVideoHeight = height;
        bestVideoBitrate = bandwidth;
      }
    }
  }

  return {
    bestVideoUrl,
    bestVideoWidth,
    bestVideoHeight,
    bestVideoBitrate,
    bestAudioUrl,
    bestAudioBitrate: bestAudioBitrate > 0 ? bestAudioBitrate : undefined,
  };
}

interface HarvestContext {
  candidates: Map<string, ReelStreamCandidate>;
  pageTitle?: string;
  latestHasNextPage?: boolean;
}

function mergeCandidate(
  map: Map<string, ReelStreamCandidate>,
  incoming: Partial<ReelStreamCandidate> & { reelId: string },
  defaultUrlPrefix: string = 'https://www.facebook.com/reel/'
) {
  const id = incoming.reelId;
  if (!id) return;
  const existing: ReelStreamCandidate = map.get(id) || {
    reelId: id,
    reelUrl: incoming.reelUrl || `${defaultUrlPrefix}${id}/`,
  };

  if (incoming.reelUrl) existing.reelUrl = incoming.reelUrl;
  if (incoming.caption && (!existing.caption || incoming.caption.length > existing.caption.length)) {
    existing.caption = incoming.caption;
  }
  if (incoming.viewCountText && !existing.viewCountText) {
    existing.viewCountText = incoming.viewCountText;
  }
  if (incoming.thumbnailUrl && !existing.thumbnailUrl) {
    existing.thumbnailUrl = incoming.thumbnailUrl;
  }
  if (incoming.durationSec && (!existing.durationSec || incoming.durationSec > existing.durationSec)) {
    existing.durationSec = incoming.durationSec;
  }
  if (incoming.publishTime && !existing.publishTime) {
    existing.publishTime = incoming.publishTime;
  }
  if (incoming.hdUrl && !existing.hdUrl) {
    existing.hdUrl = stripByteRangeParams(incoming.hdUrl);
  }
  if (incoming.sdUrl && !existing.sdUrl) {
    existing.sdUrl = stripByteRangeParams(incoming.sdUrl);
  }
  if (incoming.dashVideoUrl) {
    const newDim = Math.max(incoming.dashVideoWidth || 0, incoming.dashVideoHeight || 0);
    const oldDim = Math.max(existing.dashVideoWidth || 0, existing.dashVideoHeight || 0);
    if (!existing.dashVideoUrl || newDim >= oldDim) {
      existing.dashVideoUrl = stripByteRangeParams(incoming.dashVideoUrl);
      existing.dashVideoWidth = incoming.dashVideoWidth || existing.dashVideoWidth;
      existing.dashVideoHeight = incoming.dashVideoHeight || existing.dashVideoHeight;
      existing.dashVideoBitrate = incoming.dashVideoBitrate || existing.dashVideoBitrate;
    }
  }
  if (incoming.dashAudioUrl && !existing.dashAudioUrl) {
    existing.dashAudioUrl = stripByteRangeParams(incoming.dashAudioUrl);
    existing.dashAudioBitrate = incoming.dashAudioBitrate || existing.dashAudioBitrate;
  }

  map.set(id, existing);
}

function walkRelayObject(node: any, ctx: HarvestContext, parentContext: { publishTime?: number; caption?: string } = {}) {
  if (!node || typeof node !== 'object') return;

  if (Array.isArray(node)) {
    for (const item of node) {
      walkRelayObject(item, ctx, parentContext);
    }
    return;
  }

  if (node.__typename === 'TimelineAppCollectionReelsRenderer' && node.collection) {
    const col = node.collection;
    if (typeof col.name === 'string' && col.name.trim()) {
      ctx.pageTitle = col.name.replace(/'s reels$/i, '').trim() || col.name.trim();
    }
    if (col.aggregated_fb_shorts?.page_info && typeof col.aggregated_fb_shorts.page_info.has_next_page === 'boolean') {
      ctx.latestHasNextPage = col.aggregated_fb_shorts.page_info.has_next_page;
    }
  }
  if (node.aggregated_fb_shorts?.page_info && typeof node.aggregated_fb_shorts.page_info.has_next_page === 'boolean') {
    ctx.latestHasNextPage = node.aggregated_fb_shorts.page_info.has_next_page;
  }

  let localContext = parentContext;
  let extractedPublishTime = parentContext.publishTime;
  let extractedCaption = parentContext.caption;

  if (typeof node.creation_time === 'number' && node.creation_time > 1_000_000_000) {
    extractedPublishTime = node.creation_time;
  }
  if (typeof node.publish_time === 'number' && node.publish_time > 1_000_000_000) {
    extractedPublishTime = node.publish_time;
  }
  if (typeof node.tracking === 'string' && node.tracking.includes('publish_time')) {
    try {
      const tr = JSON.parse(node.tracking);
      if (tr?.publish_time) {
        extractedPublishTime = Number(tr.publish_time);
      } else if (tr?.page_insights) {
        for (const k of Object.keys(tr.page_insights)) {
          const pt = tr.page_insights[k]?.post_context?.publish_time;
          if (pt) {
            extractedPublishTime = Number(pt);
            break;
          }
        }
      }
    } catch {}
  }

  if (typeof node.message?.text === 'string' && node.message.text.trim()) {
    extractedCaption = node.message.text.trim();
  } else if (typeof node.creation_story?.message?.text === 'string' && node.creation_story.message.text.trim()) {
    extractedCaption = node.creation_story.message.text.trim();
  } else if (typeof node.title?.text === 'string' && node.title.text.trim() && !extractedCaption) {
    extractedCaption = node.title.text.trim();
  }

  if (extractedPublishTime !== parentContext.publishTime || extractedCaption !== parentContext.caption) {
    localContext = { publishTime: extractedPublishTime, caption: extractedCaption };
  }

  const rawId = node.video_id || node.id;
  const idStr = rawId ? String(rawId) : '';
  const hasStreamFields =
    typeof node.browser_native_hd_url === 'string' ||
    typeof node.browser_native_sd_url === 'string' ||
    typeof node.playable_url_quality_hd === 'string' ||
    typeof node.playable_url === 'string' ||
    typeof node.hd_src === 'string' ||
    typeof node.sd_src === 'string' ||
    Array.isArray(node.progressive_urls) ||
    typeof node.dash_manifest === 'string' ||
    node.__typename === 'Video';

  if (idStr && /^\d{8,25}$/.test(idStr) && hasStreamFields) {
    let hdUrl =
      node.browser_native_hd_url ||
      node.playable_url_quality_hd ||
      node.hd_src_no_ratelimit ||
      node.hd_src ||
      undefined;
    let sdUrl =
      node.browser_native_sd_url ||
      node.playable_url ||
      node.sd_src_no_ratelimit ||
      node.sd_src ||
      undefined;

    if (Array.isArray(node.progressive_urls)) {
      for (const p of node.progressive_urls) {
        const pUrl = p?.progressive_url;
        const q = String(p?.metadata?.quality || '').toUpperCase();
        if (typeof pUrl === 'string' && pUrl.startsWith('http')) {
          if (q === 'HD' && !hdUrl) hdUrl = pUrl;
          else if (!sdUrl) sdUrl = pUrl;
        }
      }
    }

    const dashXml =
      typeof node.dash_manifest === 'string'
        ? node.dash_manifest
        : typeof node.dash_manifest_xml_string === 'string'
        ? node.dash_manifest_xml_string
        : '';

    const dashParsed = dashXml ? parseDashManifestXml(dashXml) : {};

    const progResult = node.videoDeliveryResponseFragment?.videoDeliveryResponseResult;
    if (progResult) {
      if (Array.isArray(progResult.progressive_urls)) {
        for (const p of progResult.progressive_urls) {
          const pUrl = p?.progressive_url;
          const q = String(p?.metadata?.quality || '').toUpperCase();
          if (typeof pUrl === 'string' && pUrl.startsWith('http')) {
            if (q === 'HD' && !hdUrl) hdUrl = pUrl;
            else if (!sdUrl) sdUrl = pUrl;
          }
        }
      }
      if (Array.isArray(progResult.dash_manifests) && progResult.dash_manifests[0]?.manifest_xml) {
        const dm2 = parseDashManifestXml(progResult.dash_manifests[0].manifest_xml);
        if (dm2.bestVideoUrl && !dashParsed.bestVideoUrl) Object.assign(dashParsed, dm2);
      }
    }

    const durMs = Number(node.playable_duration_in_ms || 0);
    const durSec = durMs > 0 ? Math.round(durMs / 1000) : Number(node.length_in_second || 0);
    const thumb =
      node.first_frame_thumbnail ||
      node.preferred_thumbnail?.image?.uri ||
      node.thumbnailImage?.uri ||
      undefined;

    mergeCandidate(ctx.candidates, {
      reelId: idStr,
      reelUrl: `https://www.facebook.com/reel/${idStr}/`,
      caption: localContext.caption,
      publishTime: localContext.publishTime,
      durationSec: durSec > 0 ? durSec : undefined,
      thumbnailUrl: typeof thumb === 'string' ? unescapeFbUrl(thumb) : undefined,
      hdUrl: typeof hdUrl === 'string' ? unescapeFbUrl(hdUrl) : undefined,
      sdUrl: typeof sdUrl === 'string' ? unescapeFbUrl(sdUrl) : undefined,
      dashVideoUrl: dashParsed.bestVideoUrl,
      dashVideoWidth: dashParsed.bestVideoWidth,
      dashVideoHeight: dashParsed.bestVideoHeight,
      dashVideoBitrate: dashParsed.bestVideoBitrate,
      dashAudioUrl: dashParsed.bestAudioUrl,
      dashAudioBitrate: dashParsed.bestAudioBitrate,
    });
  }

  for (const key of Object.keys(node)) {
    const val = node[key];
    if (val && typeof val === 'object') {
      walkRelayObject(val, ctx, localContext);
    }
  }
}

export function harvestPayloadText(rawText: string, ctx: HarvestContext) {
  if (!rawText || rawText.length < 40) return;

  if (rawText.includes('<script')) {
    const scriptRegex = /<script[^>]*type=["']application\/json["'][^>]*>([\s\S]*?)<\/script>/gi;
    let sm: RegExpExecArray | null;
    while ((sm = scriptRegex.exec(rawText)) !== null) {
      const jsonContent = sm[1]?.trim();
      if (!jsonContent || jsonContent.length < 20) continue;
      if (
        jsonContent.includes('browser_native_') ||
        jsonContent.includes('playable_url') ||
        jsonContent.includes('progressive_url') ||
        jsonContent.includes('short_form_video_context') ||
        jsonContent.includes('TimelineAppCollectionReelsRenderer') ||
        jsonContent.includes('dash_manifest')
      ) {
        try {
          const parsed = JSON.parse(jsonContent);
          walkRelayObject(parsed, ctx);
        } catch {}
      }
    }
  } else {
    const lines = rawText.split(/\r?\n/);
    for (const line of lines) {
      const trimmed = line.trim().replace(/^for\s*\(\s*;\s*;\s*\)\s*;/, '');
      if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) continue;
      if (
        trimmed.includes('browser_native_') ||
        trimmed.includes('playable_url') ||
        trimmed.includes('progressive_url') ||
        trimmed.includes('short_form_video_context') ||
        trimmed.includes('TimelineAppCollectionReelsRenderer') ||
        trimmed.includes('aggregated_fb_shorts') ||
        trimmed.includes('dash_manifest')
      ) {
        try {
          const parsed = JSON.parse(trimmed);
          walkRelayObject(parsed, ctx);
        } catch {}
      }
    }
  }

  const idStreamRegex =
    /"(?:video_id|id)"\s*:\s*"(\d{8,25})"[\s\S]{1,3500}?"(browser_native_sd_url|playable_url)"\s*:\s*"(https?:[^"]+)"(?:[\s\S]{1,1500}?"(browser_native_hd_url|playable_url_quality_hd)"\s*:\s*"(https?:[^"]+)")?/g;
  let rm: RegExpExecArray | null;
  while ((rm = idStreamRegex.exec(rawText)) !== null) {
    const vid = rm[1];
    const sd = rm[3] ? unescapeFbUrl(rm[3]) : undefined;
    const hd = rm[5] ? unescapeFbUrl(rm[5]) : undefined;
    mergeCandidate(ctx.candidates, {
      reelId: vid,
      reelUrl: `https://www.facebook.com/reel/${vid}/`,
      hdUrl: hd,
      sdUrl: sd,
    });
  }
}

// Instagram JSON AST Harvester
function walkInstagramObject(node: any, ctx: HarvestContext) {
  if (!node || typeof node !== 'object') return;
  if (Array.isArray(node)) {
    for (const item of node) walkInstagramObject(item, ctx);
    return;
  }

  const code = node.code || node.shortcode;
  if (typeof code === 'string' && /^[A-Za-z0-9_-]{6,20}$/.test(code)) {
    let hdUrl: string | undefined;
    if (Array.isArray(node.video_versions) && node.video_versions.length > 0) {
      const sorted = [...node.video_versions].sort(
        (a, b) => (Number(b?.height || 0) * Number(b?.width || 0)) - (Number(a?.height || 0) * Number(a?.width || 0))
      );
      if (typeof sorted[0]?.url === 'string') {
        hdUrl = unescapeFbUrl(sorted[0].url);
      }
    }
    if (!hdUrl && typeof node.video_url === 'string') {
      hdUrl = unescapeFbUrl(node.video_url);
    }

    const dashXml = typeof node.video_dash_manifest === 'string' ? node.video_dash_manifest : '';
    const dashParsed = dashXml ? parseDashManifestXml(dashXml) : {};

    const caption =
      node.caption?.text ||
      node.edge_media_to_caption?.edges?.[0]?.node?.text ||
      undefined;
    const durSec = Math.round(Number(node.video_duration || 0));
    const publishTime = Number(node.taken_at || node.taken_at_timestamp || 0) || undefined;
    const views = node.play_count || node.view_count || node.video_view_count;

    if (hdUrl || dashParsed.bestVideoUrl || node.is_video === true || node.media_type === 2) {
      mergeCandidate(
        ctx.candidates,
        {
          reelId: code,
          reelUrl: `https://www.instagram.com/reel/${code}/`,
          caption: typeof caption === 'string' ? caption : undefined,
          viewCountText: views ? String(views) : undefined,
          durationSec: durSec > 0 ? durSec : undefined,
          publishTime,
          hdUrl,
          dashVideoUrl: dashParsed.bestVideoUrl,
          dashVideoWidth: dashParsed.bestVideoWidth,
          dashVideoHeight: dashParsed.bestVideoHeight,
          dashAudioUrl: dashParsed.bestAudioUrl,
        },
        'https://www.instagram.com/reel/'
      );
    }
  }

  for (const k of Object.keys(node)) {
    const val = node[k];
    if (val && typeof val === 'object') {
      walkInstagramObject(val, ctx);
    }
  }
}

function harvestInstagramText(rawText: string, ctx: HarvestContext) {
  if (!rawText || rawText.length < 30) return;
  if (rawText.includes('<script')) {
    const scriptRegex = /<script[^>]*type=["']application\/json["'][^>]*>([\s\S]*?)<\/script>/gi;
    let sm: RegExpExecArray | null;
    while ((sm = scriptRegex.exec(rawText)) !== null) {
      const jsonContent = sm[1]?.trim();
      if (!jsonContent) continue;
      if (
        jsonContent.includes('video_versions') ||
        jsonContent.includes('video_url') ||
        jsonContent.includes('shortcode') ||
        jsonContent.includes('clips')
      ) {
        try {
          walkInstagramObject(JSON.parse(jsonContent), ctx);
        } catch {}
      }
    }
  } else {
    try {
      walkInstagramObject(JSON.parse(rawText), ctx);
    } catch {}
  }
}

async function probeLocalMediaFile(filePath: string): Promise<ProbedMediaInfo> {
  if (!fs.existsSync(filePath) || fs.statSync(filePath).size < 4096) {
    return { hasVideo: false, hasAudio: false, width: 0, height: 0, durationSec: 0 };
  }
  try {
    await execFileAsync(FFMPEG_BIN, ['-hide_banner', '-i', filePath]);
    return { hasVideo: true, hasAudio: true, width: 0, height: 0, durationSec: 0 };
  } catch (err: any) {
    const stderr = String(err?.stderr || err?.message || '');
    const hasVideo = /Stream #\d+:\d+.*Video:/i.test(stderr);
    const hasAudio = /Stream #\d+:\d+.*Audio:/i.test(stderr);
    const dimMatch = stderr.match(/Video:.*?\b(\d{2,5})x(\d{2,5})\b/);
    const width = dimMatch ? Number(dimMatch[1]) : 0;
    const height = dimMatch ? Number(dimMatch[2]) : 0;
    const durMatch = stderr.match(/Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)/);
    let durationSec = 0;
    if (durMatch) {
      durationSec = Math.round(Number(durMatch[1]) * 3600 + Number(durMatch[2]) * 60 + Number(durMatch[3]));
    }
    return { hasVideo, hasAudio, width, height, durationSec };
  }
}

async function muxVideoAndAudioFiles(videoPath: string, audioPath: string, outputPath: string): Promise<boolean> {
  if (fs.existsSync(outputPath)) {
    try { fs.unlinkSync(outputPath); } catch {}
  }
  try {
    await execFileAsync(FFMPEG_BIN, [
      '-y',
      '-hide_banner',
      '-loglevel', 'error',
      '-i', videoPath,
      '-i', audioPath,
      '-map', '0:v:0',
      '-map', '1:a:0',
      '-c:v', 'copy',
      '-c:a', 'copy',
      '-movflags', '+faststart',
      '-shortest',
      outputPath,
    ]);
    const info = await probeLocalMediaFile(outputPath);
    if (info.hasVideo && info.hasAudio) return true;
  } catch {}

  try {
    await execFileAsync(FFMPEG_BIN, [
      '-y',
      '-hide_banner',
      '-loglevel', 'error',
      '-i', videoPath,
      '-i', audioPath,
      '-map', '0:v:0',
      '-map', '1:a:0',
      '-c:v', 'copy',
      '-c:a', 'aac',
      '-b:a', '128k',
      '-movflags', '+faststart',
      '-shortest',
      outputPath,
    ]);
    const info = await probeLocalMediaFile(outputPath);
    return info.hasVideo && info.hasAudio;
  } catch {
    return false;
  }
}

function computeFileSha256(filePath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = fs.createReadStream(filePath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('end', () => resolve(hash.digest('hex')));
    stream.on('error', reject);
  });
}

function downloadStreamToFile(
  streamUrl: string,
  destPath: string,
  cookieHeader: string = '',
  referer: string = 'https://www.facebook.com/',
  maxRedirects: number = 5
): Promise<number> {
  return new Promise((resolve, reject) => {
    const cleanUrl = stripByteRangeParams(streamUrl);
    const client = cleanUrl.startsWith('https') ? https : http;
    const headers: Record<string, string> = {
      'User-Agent': DESKTOP_UA,
      'Accept': '*/*',
      'Referer': referer,
      'Connection': 'keep-alive',
    };
    if (cookieHeader) {
      headers['Cookie'] = cookieHeader;
    }

    const req = client.get(cleanUrl, { headers, timeout: 60_000 }, (res) => {
      const code = res.statusCode || 0;
      if (code >= 300 && code < 400 && res.headers.location) {
        res.resume();
        if (maxRedirects <= 0) {
          return reject(new Error('Quá nhiều lần chuyển hướng CDN.'));
        }
        const nextUrl = res.headers.location.startsWith('http')
          ? res.headers.location
          : new URL(res.headers.location, cleanUrl).toString();
        return downloadStreamToFile(nextUrl, destPath, cookieHeader, referer, maxRedirects - 1)
          .then(resolve)
          .catch(reject);
      }

      if (code < 200 || code >= 300) {
        res.resume();
        return reject(new Error(`CDN HTTP ${code}`));
      }

      const fileOut = fs.createWriteStream(destPath);
      let written = 0;
      res.on('data', (chunk: Buffer) => {
        written += chunk.length;
      });
      res.pipe(fileOut);
      fileOut.on('finish', () => {
        fileOut.close(() => resolve(written));
      });
      fileOut.on('error', (err) => {
        try { fs.unlinkSync(destPath); } catch {}
        reject(err);
      });
    });

    req.on('timeout', () => {
      req.destroy(new Error('Timeout khi tải video từ CDN.'));
    });
    req.on('error', (err) => {
      try { if (fs.existsSync(destPath)) fs.unlinkSync(destPath); } catch {}
      reject(err);
    });
  });
}

async function detectCaptchaOrCheckpoint(page: Page): Promise<{ blocked: boolean; reason: string }> {
  const url = page.url().toLowerCase();
  if (
    url.includes('/checkpoint/') ||
    url.includes('/challenge/') ||
    url.includes('/captcha') ||
    url.includes('recover/initiate')
  ) {
    return {
      blocked: true,
      reason: 'Nền tảng hiển thị cổng xác minh bảo mật / CAPTCHA. Hệ thống tự động dừng theo quy tắc an toàn; vui lòng bấm mở Chrome đăng nhập và xác nhận trực tiếp.',
    };
  }

  const hasChallengeFrame = await page.evaluate(() => {
    const iframes = Array.from(document.querySelectorAll('iframe'));
    for (const f of iframes) {
      const src = (f.getAttribute('src') || '').toLowerCase();
      if (
        src.includes('recaptcha') ||
        src.includes('hcaptcha') ||
        src.includes('challenges.cloudflare.com') ||
        src.includes('arkoselabs') ||
        src.includes('funcaptcha')
      ) {
        const r = f.getBoundingClientRect();
        if (r.width > 20 && r.height > 20) return true;
      }
    }
    return Boolean(document.querySelector('#captcha_dialog, [id*="captcha_"]'));
  }).catch(() => false);

  if (hasChallengeFrame) {
    return {
      blocked: true,
      reason: 'Phát hiện khung xác thực CAPTCHA trên trang. Hệ thống đã dừng ngay để người dùng tự xử lý trong trình duyệt.',
    };
  }

  return { blocked: false, reason: '' };
}

class FacebookReelsService {
  private jobs = new Map<string, ReelsDownloadJob>();
  private profileBusy = false;
  private loginBrowserContext: BrowserContext | null = null;
  private favoritesStore: FavoritesStoreData = {
    schedulerEnabled: true,
    defaultIntervalMinutes: 1440,
    lastSchedulerTickAt: null,
    favorites: [],
  };
  private schedulerTimer: NodeJS.Timeout | null = null;

  constructor() {
    this.loadJobsFromDisk();
    this.loadFavoritesFromDisk();
    this.startFavoritesScheduler();
    setTimeout(() => {
      this.upgradeExistingLibraryFileNames().catch(() => {});
    }, 500);
  }

  private loadJobsFromDisk() {
    try {
      if (fs.existsSync(config.reelsJobsFile)) {
        const raw = JSON.parse(fs.readFileSync(config.reelsJobsFile, 'utf8'));
        if (Array.isArray(raw)) {
          for (const j of raw) {
            if (j && j.jobId) {
              if (!j.platform) j.platform = 'facebook';
              if (j.status === 'SCANNING' || j.status === 'DOWNLOADING' || j.status === 'QUEUED') {
                j.status = 'PARTIAL';
                j.message = 'Tiến trình trước đó bị gián đoạn khi khởi động lại server. Bấm chạy lại để tiếp tục tải các video còn thiếu.';
              }
              this.jobs.set(j.jobId, j);
            }
          }
        }
      }
    } catch {}
  }

  private persistJobsToDisk() {
    try {
      const list = Array.from(this.jobs.values())
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .slice(0, 60);
      fs.writeFileSync(config.reelsJobsFile, JSON.stringify(list, null, 2), 'utf8');
    } catch {}
  }

  private countMp4InPageDir(pageSlug: string): number {
    try {
      const dir = path.join(config.videoLibraryDir, pageSlug);
      if (!fs.existsSync(dir)) return 0;
      return fs.readdirSync(dir).filter((f) => f.toLowerCase().endsWith('.mp4') && !f.startsWith('.tmp_')).length;
    } catch {
      return 0;
    }
  }

  private loadFavoritesFromDisk() {
    try {
      if (fs.existsSync(config.reelsFavoritesFile)) {
        const raw = JSON.parse(fs.readFileSync(config.reelsFavoritesFile, 'utf8'));
        if (raw && Array.isArray(raw.favorites)) {
          this.favoritesStore = {
            schedulerEnabled: raw.schedulerEnabled !== false,
            defaultIntervalMinutes: Number(raw.defaultIntervalMinutes || 1440),
            lastSchedulerTickAt: raw.lastSchedulerTickAt || null,
            favorites: raw.favorites.map((f: FavoriteChannel) => ({
              ...f,
              totalDownloadedInLibrary: this.countMp4InPageDir(f.pageSlug),
            })),
          };
          return;
        }
      }
    } catch {}

    // Pre-seed Sam Family default favorite Page on first run
    const defaultTarget = normalizeFacebookInput('https://www.facebook.com/samfamily.6826/');
    const now = new Date();
    const next = new Date(now.getTime() + 1440 * 60_000);
    this.favoritesStore = {
      schedulerEnabled: true,
      defaultIntervalMinutes: 1440,
      lastSchedulerTickAt: now.toISOString(),
      favorites: [
        {
          id: `fav_${defaultTarget.pageSlug}`,
          platform: defaultTarget.platform,
          url: defaultTarget.inputUrl,
          normalizedPageUrl: defaultTarget.normalizedPageUrl,
          reelsTabUrl: defaultTarget.reelsTabUrl,
          pageSlug: defaultTarget.pageSlug,
          title: 'Sam Family (samfamily.6826)',
          enabled: true,
          preferQuality: '1080p',
          maxReelsPerScan: 0,
          checkIntervalMinutes: 1440,
          lastCheckedAt: now.toISOString(),
          nextCheckAt: next.toISOString(),
          lastNewVideoAt: now.toISOString(),
          lastJobId: null,
          lastStatus: 'READY',
          lastNewVideosCount: 0,
          totalDownloadedInLibrary: this.countMp4InPageDir(defaultTarget.pageSlug),
          createdAt: now.toISOString(),
        },
      ],
    };
    this.persistFavoritesToDisk();
  }

  private persistFavoritesToDisk() {
    try {
      for (const f of this.favoritesStore.favorites) {
        f.totalDownloadedInLibrary = this.countMp4InPageDir(f.pageSlug);
      }
      fs.writeFileSync(config.reelsFavoritesFile, JSON.stringify(this.favoritesStore, null, 2), 'utf8');
    } catch {}
  }

  private startFavoritesScheduler() {
    if (this.schedulerTimer) clearInterval(this.schedulerTimer);
    this.schedulerTimer = setInterval(() => {
      this.tickFavoritesScheduler();
    }, 30_000);
  }

  private tickFavoritesScheduler() {
    const now = new Date();
    this.favoritesStore.lastSchedulerTickAt = now.toISOString();
    if (!this.favoritesStore.schedulerEnabled) return;

    // Do not start a new auto-job if another scan/download job is currently running
    const anyRunning = Array.from(this.jobs.values()).some(
      (j) => j.status === 'QUEUED' || j.status === 'SCANNING' || j.status === 'DOWNLOADING'
    );
    if (anyRunning || this.profileBusy) return;

    for (const fav of this.favoritesStore.favorites) {
      if (!fav.enabled) continue;
      const dueTime = fav.nextCheckAt ? new Date(fav.nextCheckAt).getTime() : 0;
      if (now.getTime() >= dueTime) {
        this.triggerFavoriteScan(fav.id, 'FAVORITE_AUTO');
        break; // Run one channel at a time cleanly
      }
    }
  }

  public getFavoritesState(): FavoritesStoreData {
    for (const f of this.favoritesStore.favorites) {
      f.totalDownloadedInLibrary = this.countMp4InPageDir(f.pageSlug);
    }
    return this.favoritesStore;
  }

  public addOrUpdateFavorite(params: {
    url: string;
    title?: string;
    checkIntervalMinutes?: number;
    preferQuality?: '1080p' | 'hd' | 'sd';
    maxReelsPerScan?: number;
    enabled?: boolean;
    scanNow?: boolean;
  }): { favorite: FavoriteChannel; job?: ReelsDownloadJob } {
    const target = normalizeFacebookInput(params.url);
    const id = `fav_${target.pageSlug}`;
    const interval = Math.max(5, Number(params.checkIntervalMinutes || this.favoritesStore.defaultIntervalMinutes || 1440));
    const now = new Date();
    const next = new Date(now.getTime() + interval * 60_000);

    let fav = this.favoritesStore.favorites.find((f) => f.id === id || f.pageSlug === target.pageSlug);
    if (fav) {
      fav.url = target.inputUrl;
      fav.normalizedPageUrl = target.normalizedPageUrl;
      fav.reelsTabUrl = target.reelsTabUrl;
      fav.platform = target.platform;
      if (params.title?.trim()) fav.title = params.title.trim();
      fav.checkIntervalMinutes = interval;
      if (params.preferQuality) fav.preferQuality = params.preferQuality;
      if (params.maxReelsPerScan !== undefined) fav.maxReelsPerScan = Math.max(0, Number(params.maxReelsPerScan));
      if (params.enabled !== undefined) fav.enabled = Boolean(params.enabled);
      fav.nextCheckAt = next.toISOString();
    } else {
      fav = {
        id,
        platform: target.platform,
        url: target.inputUrl,
        normalizedPageUrl: target.normalizedPageUrl,
        reelsTabUrl: target.reelsTabUrl,
        pageSlug: target.pageSlug,
        title: params.title?.trim() || target.pageSlug,
        enabled: params.enabled !== false,
        preferQuality: params.preferQuality || '1080p',
        maxReelsPerScan: Math.max(0, Number(params.maxReelsPerScan || 0)),
        checkIntervalMinutes: interval,
        lastCheckedAt: null,
        nextCheckAt: next.toISOString(),
        lastNewVideoAt: null,
        lastJobId: null,
        lastStatus: 'IDLE',
        lastNewVideosCount: 0,
        totalDownloadedInLibrary: this.countMp4InPageDir(target.pageSlug),
        createdAt: now.toISOString(),
      };
      this.favoritesStore.favorites.unshift(fav);
    }

    this.persistFavoritesToDisk();

    let job: ReelsDownloadJob | undefined;
    if (params.scanNow) {
      job = this.triggerFavoriteScan(fav.id, 'FAVORITE_MANUAL');
    }
    return { favorite: fav, job };
  }

  public updateFavoriteById(
    id: string,
    patch: Partial<Pick<FavoriteChannel, 'title' | 'enabled' | 'checkIntervalMinutes' | 'preferQuality' | 'maxReelsPerScan'>>
  ): FavoriteChannel | undefined {
    const fav = this.favoritesStore.favorites.find((f) => f.id === id);
    if (!fav) return undefined;
    if (patch.title !== undefined && patch.title.trim()) fav.title = patch.title.trim();
    if (patch.enabled !== undefined) fav.enabled = Boolean(patch.enabled);
    if (patch.checkIntervalMinutes !== undefined) {
      fav.checkIntervalMinutes = Math.max(5, Number(patch.checkIntervalMinutes));
      fav.nextCheckAt = new Date(Date.now() + fav.checkIntervalMinutes * 60_000).toISOString();
    }
    if (patch.preferQuality) fav.preferQuality = patch.preferQuality;
    if (patch.maxReelsPerScan !== undefined) fav.maxReelsPerScan = Math.max(0, Number(patch.maxReelsPerScan));
    this.persistFavoritesToDisk();
    return fav;
  }

  public removeFavoriteById(id: string): boolean {
    const before = this.favoritesStore.favorites.length;
    this.favoritesStore.favorites = this.favoritesStore.favorites.filter((f) => f.id !== id);
    this.persistFavoritesToDisk();
    return this.favoritesStore.favorites.length < before;
  }

  public updateSchedulerSettings(settings: { schedulerEnabled?: boolean; defaultIntervalMinutes?: number }): FavoritesStoreData {
    if (settings.schedulerEnabled !== undefined) {
      this.favoritesStore.schedulerEnabled = Boolean(settings.schedulerEnabled);
    }
    if (settings.defaultIntervalMinutes !== undefined) {
      const mins = Math.max(5, Number(settings.defaultIntervalMinutes));
      this.favoritesStore.defaultIntervalMinutes = mins;
      for (const f of this.favoritesStore.favorites) {
        f.checkIntervalMinutes = mins;
        f.nextCheckAt = new Date(Date.now() + mins * 60_000).toISOString();
      }
    }
    this.persistFavoritesToDisk();
    return this.favoritesStore;
  }

  public triggerFavoriteScan(
    favoriteId: string,
    triggeredBy: 'FAVORITE_AUTO' | 'FAVORITE_MANUAL' = 'FAVORITE_MANUAL'
  ): ReelsDownloadJob {
    const fav = this.favoritesStore.favorites.find((f) => f.id === favoriteId);
    if (!fav) {
      throw new Error(`Không tìm thấy kênh yêu thích ${favoriteId}`);
    }
    const now = new Date();
    fav.lastCheckedAt = now.toISOString();
    fav.nextCheckAt = new Date(now.getTime() + fav.checkIntervalMinutes * 60_000).toISOString();
    fav.lastStatus = 'SCANNING';

    const job = this.startPageDownloadJob({
      url: fav.url,
      maxReels: fav.maxReelsPerScan,
      preferQuality: fav.preferQuality,
      forceRedownload: false,
      incrementalOnly: true,
      favoriteId: fav.id,
      triggeredBy,
    });

    fav.lastJobId = job.jobId;
    this.persistFavoritesToDisk();
    return job;
  }

  public async scanAllFavoritesNow(): Promise<ReelsDownloadJob[]> {
    const enabledFavs = this.favoritesStore.favorites.filter((f) => f.enabled);
    const jobs: ReelsDownloadJob[] = [];
    for (const fav of enabledFavs) {
      const j = this.triggerFavoriteScan(fav.id, 'FAVORITE_MANUAL');
      jobs.push(j);
    }
    return jobs;
  }

  private appendJobLog(job: ReelsDownloadJob, line: string) {
    const ts = new Date().toLocaleTimeString('vi-VN', { hour12: false });
    const entry = `[${ts}] ${line}`;
    job.logs.push(entry);
    if (job.logs.length > 250) {
      job.logs.shift();
    }
    job.updatedAt = new Date().toISOString();
    console.log(`[ReelsJob ${job.jobId.slice(0, 8)} | ${job.platform.toUpperCase()}] ${line}`);
  }

  public getAllJobs(): ReelsDownloadJob[] {
    return Array.from(this.jobs.values()).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  public getJob(jobId: string): ReelsDownloadJob | undefined {
    return this.jobs.get(jobId);
  }

  public cancelJob(jobId: string): ReelsDownloadJob | undefined {
    const job = this.jobs.get(jobId);
    if (!job) return undefined;
    if (job.status === 'SCANNING' || job.status === 'DOWNLOADING' || job.status === 'QUEUED') {
      job.cancelRequested = true;
      job.status = 'CANCELLED';
      job.message = 'Đã nhận lệnh dừng từ người dùng / AI Agent.';
      this.appendJobLog(job, 'Đã kích hoạt lệnh dừng (Kill Switch).');
      this.persistJobsToDisk();
    }
    return job;
  }

  public getSessionStatus() {
    const hasProfile = fs.existsSync(config.browserProfileDir);
    return {
      browserProfileDir: config.browserProfileDir,
      hasSavedProfile: hasProfile,
      isLoginWindowOpen: this.loginBrowserContext !== null,
      isProfileBusy: this.profileBusy,
      defaultOutputDir: config.videoLibraryDir,
      hasYtDlp: fs.existsSync(config.ytDlpBin),
    };
  }

  public async openInteractiveLoginWindow(platform: SupportedPlatform = 'facebook'): Promise<{ opened: boolean; message: string }> {
    const loginUrls: Record<SupportedPlatform, string> = {
      facebook: 'https://www.facebook.com/',
      instagram: 'https://www.instagram.com/accounts/login/',
      youtube: 'https://accounts.google.com/ServiceLogin?service=youtube',
    };
    const targetUrl = loginUrls[platform] || loginUrls.facebook;

    if (this.loginBrowserContext) {
      const page = this.loginBrowserContext.pages()[0] || (await this.loginBrowserContext.newPage());
      await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 30_000 }).catch(() => {});
      return {
        opened: true,
        message: `Đã chuyển cửa sổ Chrome sang trang đăng nhập ${platform.toUpperCase()}. Sau khi đăng nhập xong, hãy đóng cửa sổ Chrome đó để lưu phiên.`,
      };
    }
    if (this.profileBusy) {
      throw new Error('Đang có tiến trình quét chạy trên Profile trình duyệt. Vui lòng đợi hoặc dừng job hiện tại trước khi mở cửa sổ đăng nhập.');
    }

    fs.mkdirSync(config.browserProfileDir, { recursive: true });
    this.profileBusy = true;

    const ctx = await this.launchPersistentChrome(config.browserProfileDir, false);
    this.loginBrowserContext = ctx;
    const page = ctx.pages()[0] || (await ctx.newPage());
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 30_000 }).catch(() => {});

    ctx.on('close', () => {
      this.loginBrowserContext = null;
      this.profileBusy = false;
    });

    return {
      opened: true,
      message: `Đã mở cửa sổ Google Chrome trên máy (${platform.toUpperCase()})! Hãy đăng nhập tài khoản của bạn, sau đó đóng cửa sổ Chrome đó lại để hệ thống lưu phiên cho các lần quét tiếp theo.`,
    };
  }

  public async closeInteractiveLoginWindow(): Promise<{ closed: boolean }> {
    if (this.loginBrowserContext) {
      try {
        await this.loginBrowserContext.close();
      } catch {}
      this.loginBrowserContext = null;
      this.profileBusy = false;
      return { closed: true };
    }
    return { closed: false };
  }

  private async launchPersistentChrome(userDataDir: string, headless: boolean): Promise<BrowserContext> {
    const commonOpts = {
      headless,
      userAgent: DESKTOP_UA,
      viewport: { width: 1366, height: 900 },
      locale: 'vi-VN',
      args: ['--disable-blink-features=AutomationControlled', '--no-default-browser-check'],
    };

    try {
      return await chromium.launchPersistentContext(userDataDir, {
        ...commonOpts,
        channel: 'chrome',
      });
    } catch {
      try {
        return await chromium.launchPersistentContext(userDataDir, {
          ...commonOpts,
          channel: 'msedge',
        });
      } catch {
        return await chromium.launchPersistentContext(userDataDir, commonOpts);
      }
    }
  }

  private async launchEphemeralChrome(): Promise<{ closeAll: () => Promise<void>; context: BrowserContext }> {
    const launchArgs = ['--disable-blink-features=AutomationControlled'];
    let browser;
    try {
      browser = await chromium.launch({ channel: 'chrome', headless: true, args: launchArgs });
    } catch {
      try {
        browser = await chromium.launch({ channel: 'msedge', headless: true, args: launchArgs });
      } catch {
        browser = await chromium.launch({ headless: true, args: launchArgs });
      }
    }
    const context = await browser.newContext({
      userAgent: DESKTOP_UA,
      viewport: { width: 1366, height: 900 },
      locale: 'vi-VN',
    });
    return {
      context,
      closeAll: async () => {
        try { await context.close(); } catch {}
        try { await browser.close(); } catch {}
      },
    };
  }

  private indexExistingFilesByReelId(outputDir: string): Map<string, string> {
    const map = new Map<string, string>();
    try {
      if (!fs.existsSync(outputDir)) return map;
      for (const f of fs.readdirSync(outputDir)) {
        if (f.toLowerCase().endsWith('.mp4') && !f.startsWith('.tmp_') && !f.startsWith('.mux_')) {
          const full = path.join(outputDir, f);
          try {
            if (fs.statSync(full).size > 25 * 1024) {
              const rid = extractReelIdFromFileName(f);
              if (rid) {
                map.set(rid, full);
              }
              map.set(f.slice(0, -4), full);
            }
          } catch {}
        }
      }
    } catch {}
    return map;
  }

  private getExistingDownloadedIdsOnDisk(outputDir: string): Set<string> {
    return new Set(this.indexExistingFilesByReelId(outputDir).keys());
  }

  private async fetchMetadataViaYtDlp(videoUrl: string): Promise<{
    title: string;
    caption: string;
    hashtags: string[];
    viewCountText?: string;
    durationSec?: number;
  } | null> {
    try {
      const { stdout } = await execFileAsync(
        config.ytDlpBin,
        ['--skip-download', '--dump-json', '--no-playlist', '--no-warnings', videoUrl],
        { timeout: 25_000, maxBuffer: 15 * 1024 * 1024 }
      );
      const line = stdout
        .split(/\r?\n/)
        .map((l) => l.trim())
        .find((l) => l.startsWith('{'));
      if (!line) return null;
      const obj = JSON.parse(line);
      const rawDesc = String(obj.description || '');
      const rawTitle = String(obj.title || '');
      const rawTags = Array.isArray(obj.tags) ? obj.tags.map(String) : [];
      const parsed = extractCaptionTitleAndHashtags(rawDesc, rawTitle, rawTags);
      return {
        title: parsed.title,
        caption: parsed.caption,
        hashtags: parsed.hashtags,
        viewCountText: obj.view_count ? `${Number(obj.view_count).toLocaleString('vi-VN')} views` : undefined,
        durationSec: obj.duration ? Math.round(Number(obj.duration)) : undefined,
      };
    } catch {
      return null;
    }
  }

  private applyItemMetadataAndFileName(
    item: ReelDownloadItem,
    outputDir: string,
    existingFileMap?: Map<string, string>
  ) {
    const parsed = extractCaptionTitleAndHashtags(item.caption, item.title, item.hashtags);
    item.title = parsed.title;
    item.caption = parsed.caption;
    item.hashtags = parsed.hashtags;

    const desiredFileName = buildVideoFileName(item.reelId, item.caption, item.title, item.hashtags);
    const desiredPath = path.join(outputDir, desiredFileName);

    const oldPath =
      existingFileMap?.get(item.reelId) ||
      (item.filePath && fs.existsSync(item.filePath) ? item.filePath : null) ||
      (fs.existsSync(path.join(outputDir, `${item.reelId}.mp4`))
        ? path.join(outputDir, `${item.reelId}.mp4`)
        : null);

    // Do not downgrade an already-descriptive filename on disk if item currently lacks caption
    if (
      !item.caption &&
      oldPath &&
      fs.existsSync(oldPath) &&
      path.basename(oldPath).toLowerCase() !== `${item.reelId.toLowerCase()}.mp4`
    ) {
      item.fileName = path.basename(oldPath);
      item.filePath = oldPath;
      return;
    }

    if (oldPath && fs.existsSync(oldPath) && path.resolve(oldPath) !== path.resolve(desiredPath)) {
      try {
        if (!fs.existsSync(desiredPath)) {
          fs.renameSync(oldPath, desiredPath);
        }
        if (existingFileMap) {
          existingFileMap.set(item.reelId, desiredPath);
        }
      } catch {
        // Fallback to keeping existing path if file is locked
        item.fileName = path.basename(oldPath);
        item.filePath = oldPath;
        return;
      }
    }

    item.fileName = desiredFileName;
    item.filePath = desiredPath;
  }

  public async upgradeExistingLibraryFileNames(): Promise<void> {
    try {
      const baseDir = config.videoLibraryDir;
      if (!fs.existsSync(baseDir)) return;

      // Build lookup map of known captions from all historical jobs
      const historicalItems = new Map<string, ReelDownloadItem>();
      for (const j of this.jobs.values()) {
        for (const it of j.items || []) {
          if (it && it.reelId) {
            const existing = historicalItems.get(it.reelId);
            if (!existing || (!existing.caption && it.caption)) {
              historicalItems.set(it.reelId, it);
            }
          }
        }
      }

      const dirs = fs.readdirSync(baseDir, { withFileTypes: true }).filter((d) => d.isDirectory());
      for (const dir of dirs) {
        const pagePath = path.join(baseDir, dir.name);
        const manifestPath = path.join(pagePath, 'manifest.json');
        const fileMap = this.indexExistingFilesByReelId(pagePath);
        if (fileMap.size === 0) continue;

        let manifest: any = null;
        if (fs.existsSync(manifestPath)) {
          try {
            manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
          } catch {}
        }

        const platform: SupportedPlatform =
          manifest?.platform ||
          (dir.name.startsWith('yt_') ? 'youtube' : dir.name.startsWith('ig_') ? 'instagram' : 'facebook');

        const manifestMap = new Map<string, any>();
        if (Array.isArray(manifest?.reels)) {
          for (const r of manifest.reels) {
            if (r && r.reelId) manifestMap.set(String(r.reelId), r);
          }
        }

        // Ensure all MP4 files on disk in this folder are represented in manifestMap
        for (const [rid, fullPath] of fileMap.entries()) {
          // Skip full filename keys when they differ from the extracted reelId
          if (rid.includes(' ') || rid.endsWith(']')) continue;
          if (!manifestMap.has(rid)) {
            const hist = historicalItems.get(rid);
            const stat = fs.statSync(fullPath);
            manifestMap.set(rid, {
              index: manifestMap.size + 1,
              reelId: rid,
              reelUrl:
                hist?.reelUrl ||
                (platform === 'youtube'
                  ? `https://www.youtube.com/shorts/${rid}`
                  : platform === 'instagram'
                  ? `https://www.instagram.com/reel/${rid}/`
                  : `https://www.facebook.com/reel/${rid}/`),
              fileName: path.basename(fullPath),
              filePath: fullPath,
              status: 'COMPLETED',
              title: hist?.title || '',
              caption: hist?.caption || '',
              hashtags: hist?.hashtags || [],
              viewCountText: hist?.viewCountText || '',
              durationSec: hist?.durationSec || 0,
              publishTime: hist?.publishTime || null,
              qualityLabel: hist?.qualityLabel || '1080p Full HD',
              width: hist?.width || 1080,
              height: hist?.height || 1920,
              hasAudio: hist?.hasAudio ?? true,
              sizeBytes: stat.size,
              sha256: hist?.sha256 || '',
              downloadedAt: hist?.downloadedAt || stat.mtime.toISOString(),
            });
          }
        }

        let updatedAny = false;
        const reelsList = Array.from(manifestMap.values());
        for (let i = 0; i < reelsList.length; i++) {
          const r = reelsList[i];
          r.index = i + 1;
          const hist = historicalItems.get(String(r.reelId));
          if (!r.caption && hist?.caption) {
            r.caption = hist.caption;
          }
          if (!r.caption && r.reelUrl && (platform === 'instagram' || platform === 'youtube')) {
            const fetched = await this.fetchMetadataViaYtDlp(r.reelUrl);
            if (fetched && fetched.caption) {
              r.caption = fetched.caption;
              r.title = fetched.title;
              r.hashtags = fetched.hashtags;
            }
          }

          const beforeName = r.fileName;
          this.applyItemMetadataAndFileName(r as ReelDownloadItem, pagePath, fileMap);
          if (r.caption) {
            historicalItems.set(String(r.reelId), r as ReelDownloadItem);
          }
          if (r.fileName !== beforeName || !r.hashtags) {
            updatedAny = true;
          }
        }

        if (manifest || updatedAny) {
          const updatedManifest = {
            schemaVersion: '1.2',
            platform,
            pageSlug: dir.name,
            pageTitle: manifest?.pageTitle || dir.name,
            inputUrl: manifest?.inputUrl || '',
            normalizedPageUrl: manifest?.normalizedPageUrl || '',
            reelsTabUrl: manifest?.reelsTabUrl || '',
            outputDir: pagePath,
            updatedAt: new Date().toISOString(),
            hasReachedEndOfPage: manifest?.hasReachedEndOfPage ?? true,
            stats: {
              totalDiscovered: reelsList.length,
              downloadedCount: manifest?.stats?.downloadedCount ?? reelsList.length,
              skippedCount: manifest?.stats?.skippedCount ?? 0,
              failedCount: 0,
            },
            reels: reelsList,
          };
          fs.writeFileSync(manifestPath, JSON.stringify(updatedManifest, null, 2), 'utf8');
        }
      }

      // Also update cached jobs so UI displays updated fileNames, titles & hashtags immediately
      for (const job of this.jobs.values()) {
        if (!job.outputDir || !fs.existsSync(job.outputDir)) continue;
        const fileMap = this.indexExistingFilesByReelId(job.outputDir);
        for (const item of job.items || []) {
          const hist = historicalItems.get(item.reelId);
          if (!item.caption && hist?.caption) {
            item.caption = hist.caption;
            item.title = hist.title;
            item.hashtags = hist.hashtags;
          }
          this.applyItemMetadataAndFileName(item, job.outputDir, fileMap);
        }
      }
      this.persistJobsToDisk();
    } catch (err) {
      console.error('Error upgrading existing library file names:', err);
    }
  }

  public startPageDownloadJob(options: ReelsDownloadOptions): ReelsDownloadJob {
    const target = normalizeFacebookInput(options.url);
    const baseLibraryDir = (options.outputDir || '').trim() || config.videoLibraryDir;
    const pageDir = path.resolve(baseLibraryDir, target.pageSlug);
    const manifestPath = path.join(pageDir, 'manifest.json');

    const nowIso = new Date().toISOString();
    const jobId = `job_${Date.now()}_${crypto.randomBytes(3).toString('hex')}`;
    const preferQuality = options.preferQuality || '1080p';
    const maxReels = Math.max(0, Number(options.maxReels || 0));
    const forceRedownload = Boolean(options.forceRedownload);
    const incrementalOnly = Boolean(options.incrementalOnly);

    const job: ReelsDownloadJob = {
      jobId,
      platform: target.platform,
      status: 'QUEUED',
      inputUrl: target.inputUrl,
      normalizedPageUrl: target.normalizedPageUrl,
      reelsTabUrl: target.reelsTabUrl,
      pageSlug: target.pageSlug,
      pageTitle: target.pageSlug,
      isSingleReel: target.isSingleReel,
      incrementalOnly,
      favoriteId: options.favoriteId || null,
      triggeredBy: options.triggeredBy || 'MANUAL',
      outputDir: pageDir,
      manifestPath,
      preferQuality,
      maxReels,
      forceRedownload,
      totalDiscovered: 0,
      processedCount: 0,
      downloadedCount: 0,
      skippedCount: 0,
      failedCount: 0,
      progressPercent: 0,
      hasReachedEndOfPage: false,
      loginRecommended: false,
      message: `Đang khởi tạo trình quét ${target.platform.toUpperCase()}...`,
      createdAt: nowIso,
      updatedAt: nowIso,
      completedAt: null,
      logs: [],
      items: [],
    };

    this.jobs.set(jobId, job);
    this.appendJobLog(job, `Nhận yêu cầu [${target.platform.toUpperCase()}]: ${target.inputUrl} -> ${target.reelsTabUrl}`);
    this.persistJobsToDisk();

    setImmediate(() => {
      this.dispatchPlatformJob(job, target, options.concurrency || 3)
        .catch((err) => {
          job.status = 'FAILED';
          job.message = err?.message || 'Lỗi không xác định khi tải video.';
          job.completedAt = new Date().toISOString();
          this.appendJobLog(job, `LỖI: ${job.message}`);
        })
        .finally(() => {
          this.syncJobResultToFavorite(job);
          this.persistJobsToDisk();
        });
    });

    return job;
  }

  private syncJobResultToFavorite(job: ReelsDownloadJob) {
    const fav = this.favoritesStore.favorites.find(
      (f) => (job.favoriteId && f.id === job.favoriteId) || f.pageSlug === job.pageSlug
    );
    if (!fav) return;
    fav.lastJobId = job.jobId;
    fav.lastCheckedAt = new Date().toISOString();
    fav.lastStatus = job.status;
    fav.lastNewVideosCount = job.downloadedCount;
    if (job.downloadedCount > 0) {
      fav.lastNewVideoAt = new Date().toISOString();
    }
    if (job.pageTitle && job.pageTitle !== job.pageSlug) {
      fav.title = job.pageTitle;
    }
    fav.totalDownloadedInLibrary = this.countMp4InPageDir(fav.pageSlug);
    this.persistFavoritesToDisk();
  }

  private async dispatchPlatformJob(job: ReelsDownloadJob, target: NormalizedChannelTarget, concurrency: number) {
    if (target.platform === 'youtube') {
      await this.executeYouTubeJob(job, target, concurrency);
    } else if (target.platform === 'instagram') {
      await this.executeInstagramJob(job, target, concurrency);
    } else {
      await this.executeFacebookJob(job, target, concurrency);
    }
  }

  public async waitForJobCompletion(jobId: string, timeoutMs: number = 600_000): Promise<ReelsDownloadJob> {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      const job = this.jobs.get(jobId);
      if (!job) throw new Error(`Không tìm thấy job ${jobId}`);
      if (
        job.status === 'COMPLETED' ||
        job.status === 'PARTIAL' ||
        job.status === 'NEEDS_LOGIN' ||
        job.status === 'BLOCKED_CAPTCHA' ||
        job.status === 'CANCELLED' ||
        job.status === 'FAILED'
      ) {
        return job;
      }
      await new Promise((r) => setTimeout(r, 1000));
    }
    const job = this.jobs.get(jobId);
    if (!job) throw new Error(`Không tìm thấy job ${jobId}`);
    return job;
  }

  // ============================================================================
  // PLATFORM 1: YOUTUBE CHANNEL / SHORTS / VIDEOS ENGINE
  // ============================================================================
  private async executeYouTubeJob(job: ReelsDownloadJob, target: NormalizedChannelTarget, concurrency: number) {
    fs.mkdirSync(job.outputDir, { recursive: true });
    if (!fs.existsSync(config.ytDlpBin)) {
      throw new Error(`Không tìm thấy công cụ yt-dlp tại ${config.ytDlpBin}`);
    }

    job.status = 'SCANNING';
    job.message = `Đang quét danh sách video YouTube từ ${target.reelsTabUrl}...`;
    this.persistJobsToDisk();

    const existingDiskIds = this.getExistingDownloadedIdsOnDisk(job.outputDir);
    const playlistArgs: string[] = ['--flat-playlist', '--dump-json', '--no-warnings'];
    if (job.maxReels > 0) {
      playlistArgs.push('--playlist-end', String(job.maxReels));
    } else if (job.incrementalOnly && existingDiskIds.size > 0) {
      playlistArgs.push('--playlist-end', '60');
    }

    let entries: any[] = [];
    const fetchFlatPlaylist = async (tabUrl: string): Promise<any[]> => {
      const { stdout } = await execFileAsync(config.ytDlpBin, [...playlistArgs, tabUrl], {
        maxBuffer: 50 * 1024 * 1024,
        timeout: 90_000,
      });
      return stdout
        .trim()
        .split(/\r?\n/)
        .filter(Boolean)
        .map((line) => {
          try { return JSON.parse(line); } catch { return null; }
        })
        .filter(Boolean);
    };

    if (target.isSingleReel && target.singleReelId) {
      entries = [{ id: target.singleReelId, title: `YouTube_${target.singleReelId}`, url: target.reelsTabUrl }];
    } else {
      entries = await fetchFlatPlaylist(target.reelsTabUrl).catch(() => []);
      // Fallback from /shorts to /videos if channel has no Shorts tab
      if (entries.length === 0 && target.reelsTabUrl.endsWith('/shorts')) {
        const videosFallbackUrl = target.reelsTabUrl.replace(/\/shorts$/, '/videos');
        this.appendJobLog(job, `Kênh không có tab Shorts; tự động chuyển sang quét tab Videos: ${videosFallbackUrl}`);
        job.reelsTabUrl = videosFallbackUrl;
        entries = await fetchFlatPlaylist(videosFallbackUrl).catch(() => []);
      }
    }

    if (entries.length === 0) {
      job.status = 'COMPLETED';
      job.message = 'Không tìm thấy video / Shorts công khai nào trên kênh YouTube này.';
      job.completedAt = new Date().toISOString();
      this.appendJobLog(job, job.message);
      return;
    }

    const chName = entries[0]?.playlist_uploader || entries[0]?.channel || entries[0]?.uploader;
    if (typeof chName === 'string' && chName.trim()) {
      job.pageTitle = chName.trim();
    }

    // Apply incremental early-stop if scanning favorite channel with existing videos
    if (job.incrementalOnly && existingDiskIds.size > 0 && job.maxReels === 0) {
      const filtered: any[] = [];
      let consecutiveExisting = 0;
      for (const e of entries) {
        filtered.push(e);
        if (existingDiskIds.has(String(e.id))) {
          consecutiveExisting++;
          if (consecutiveExisting >= 6) break;
        } else {
          consecutiveExisting = 0;
        }
      }
      entries = filtered;
    }

    if (job.maxReels > 0) {
      entries = entries.slice(0, job.maxReels);
    }

    const existingFileMap = this.indexExistingFilesByReelId(job.outputDir);
    job.totalDiscovered = entries.length;
    job.hasReachedEndOfPage = job.maxReels === 0 && !job.incrementalOnly;
    job.status = 'DOWNLOADING';
    job.items = entries.map((e, idx) => {
      const vid = String(e.id);
      const isShort = (e.url && String(e.url).includes('/shorts/')) || job.reelsTabUrl.endsWith('/shorts');
      const vUrl = isShort ? `https://www.youtube.com/shorts/${vid}` : `https://www.youtube.com/watch?v=${vid}`;
      const rawCap = String(e.description || e.title || '');
      const rawTitle = String(e.title || '');
      const rawTags = Array.isArray(e.tags) ? e.tags.map(String) : [];
      const parsed = extractCaptionTitleAndHashtags(rawCap, rawTitle, rawTags);
      const item: ReelDownloadItem = {
        index: idx + 1,
        reelId: vid,
        reelUrl: vUrl,
        title: parsed.title,
        caption: parsed.caption,
        hashtags: parsed.hashtags,
        viewCountText: e.view_count ? `${Number(e.view_count).toLocaleString('vi-VN')} views` : '',
        durationSec: Math.round(Number(e.duration || 0)),
        publishTime: null,
        qualityLabel: 'Pending',
        width: 0,
        height: 0,
        hasAudio: false,
        sizeBytes: 0,
        sha256: '',
        fileName: `${vid}.mp4`,
        filePath: path.join(job.outputDir, `${vid}.mp4`),
        status: 'DISCOVERED',
      };
      this.applyItemMetadataAndFileName(item, job.outputDir, existingFileMap);
      return item;
    });

    this.appendJobLog(job, `Đã phát hiện ${job.items.length} video YouTube của kênh "${job.pageTitle}". Bắt đầu tải...`);
    this.persistJobsToDisk();

    await this.runBatchItemsDownload(job, concurrency, async (item) => {
      const tmpOut = path.join(job.outputDir, `.tmp_${item.reelId}.mp4`);
      const tmpInfoJson = path.join(job.outputDir, `.tmp_${item.reelId}.info.json`);
      if (fs.existsSync(tmpOut)) {
        try { fs.unlinkSync(tmpOut); } catch {}
      }
      if (fs.existsSync(tmpInfoJson)) {
        try { fs.unlinkSync(tmpInfoJson); } catch {}
      }
      const maxH = job.preferQuality === 'sd' ? 480 : job.preferQuality === 'hd' ? 720 : 1080;
      const fmtSelector = `bestvideo[height<=${maxH}][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=${maxH}]+bestaudio/best[height<=${maxH}][ext=mp4]/best`;

      await execFileAsync(
        config.ytDlpBin,
        [
          '--ffmpeg-location', FFMPEG_BIN,
          '-f', fmtSelector,
          '--merge-output-format', 'mp4',
          '--write-info-json',
          '--no-playlist',
          '--no-warnings',
          '-o', tmpOut,
          item.reelUrl,
        ],
        { timeout: 180_000 }
      );

      if (fs.existsSync(tmpInfoJson)) {
        try {
          const infoObj = JSON.parse(fs.readFileSync(tmpInfoJson, 'utf8'));
          const fullDesc = String(infoObj.description || '');
          const fullTitle = String(infoObj.title || item.title || '');
          const fullTags = Array.isArray(infoObj.tags) ? infoObj.tags.map(String) : [];
          const combined = [fullTitle, fullDesc].filter(Boolean).join('\n');
          item.caption = combined || item.caption;
          item.title = fullTitle || item.title;
          item.hashtags = fullTags;
          this.applyItemMetadataAndFileName(item, job.outputDir);
        } catch {}
        try { fs.unlinkSync(tmpInfoJson); } catch {}
      }

      if (!fs.existsSync(tmpOut)) {
        throw new Error('Không tạo được file MP4 từ YouTube.');
      }
      if (fs.existsSync(item.filePath)) fs.unlinkSync(item.filePath);
      fs.renameSync(tmpOut, item.filePath);
    });
  }

  // ============================================================================
  // PLATFORM 2: INSTAGRAM CHANNEL / PROFILE REELS ENGINE
  // ============================================================================
  private async executeInstagramJob(job: ReelsDownloadJob, target: NormalizedChannelTarget, concurrency: number) {
    fs.mkdirSync(job.outputDir, { recursive: true });
    const existingDiskIds = this.getExistingDownloadedIdsOnDisk(job.outputDir);

    let closeBrowserFn: () => Promise<void> = async () => {};
    let context: BrowserContext;
    let usedPersistentProfile = false;

    if (!this.profileBusy) {
      this.profileBusy = true;
      usedPersistentProfile = true;
      fs.mkdirSync(config.browserProfileDir, { recursive: true });
      context = await this.launchPersistentChrome(config.browserProfileDir, true);
      closeBrowserFn = async () => {
        try { await context.close(); } catch {}
        this.profileBusy = false;
      };
    } else {
      const eph = await this.launchEphemeralChrome();
      context = eph.context;
      closeBrowserFn = eph.closeAll;
    }

    const harvestCtx: HarvestContext = {
      candidates: new Map<string, ReelStreamCandidate>(),
    };
    const orderedIds: string[] = [];
    const orderedSet = new Set<string>();
    const pushId = (code: string) => {
      if (!code || !/^[A-Za-z0-9_-]{6,20}$/.test(code)) return;
      if (!orderedSet.has(code)) {
        orderedSet.add(code);
        orderedIds.push(code);
      }
      mergeCandidate(
        harvestCtx.candidates,
        { reelId: code, reelUrl: `https://www.instagram.com/reel/${code}/` },
        'https://www.instagram.com/reel/'
      );
    };

    try {
      job.status = 'SCANNING';
      job.message = `Đang mở kênh Instagram: ${target.reelsTabUrl}`;
      this.persistJobsToDisk();

      const page = context.pages()[0] || (await context.newPage());
      page.on('response', async (res) => {
        try {
          const u = res.url();
          if (u.includes('instagram.com') && (u.includes('graphql') || u.includes('/api/v1/'))) {
            const txt = await res.text();
            harvestInstagramText(txt, harvestCtx);
          }
        } catch {}
      });

      if (target.isSingleReel && target.singleReelId) {
        pushId(target.singleReelId);
        await page.goto(target.reelsTabUrl, { waitUntil: 'domcontentloaded', timeout: 30_000 }).catch(() => {});
        await page.waitForTimeout(2500);
        const html = await page.content().catch(() => '');
        harvestInstagramText(html, harvestCtx);
      } else {
        await page.goto(target.reelsTabUrl, { waitUntil: 'domcontentloaded', timeout: 35_000 });
        await page.waitForTimeout(3200);

        const capCheck = await detectCaptchaOrCheckpoint(page);
        if (capCheck.blocked) {
          job.status = 'BLOCKED_CAPTCHA';
          job.message = capCheck.reason;
          job.completedAt = new Date().toISOString();
          this.appendJobLog(job, capCheck.reason);
          return;
        }

        const pageTitle = await page.title().catch(() => '');
        if (pageTitle && !pageTitle.toLowerCase().includes('login')) {
          job.pageTitle = pageTitle.replace(/\s*•\s*Instagram.*$/i, '').trim() || job.pageTitle;
        }

        const initialHtml = await page.content();
        harvestInstagramText(initialHtml, harvestCtx);

        // Dismiss optional public modal Close button
        const closeBtn = page.locator('[role="dialog"] svg[aria-label="Close"], [role="dialog"] [aria-label="Đóng"], button:has(svg[aria-label="Close"])').first();
        if ((await closeBtn.count().catch(() => 0)) > 0) {
          await closeBtn.click({ timeout: 2000 }).catch(() => {});
          await page.waitForTimeout(700);
        }

        let idleRounds = 0;
        let scrollRound = 0;
        while (scrollRound < 120 && !job.cancelRequested) {
          scrollRound++;
          const domEntries = await page.evaluate(() => {
            const out: { code: string; alt: string }[] = [];
            for (const a of Array.from(document.querySelectorAll('a[href]'))) {
              const href = (a as HTMLAnchorElement).href || '';
              const m = href.match(/\/(?:reel|p)\/([A-Za-z0-9_-]{6,20})/);
              if (m && m[1]) {
                const img = a.querySelector('img[alt]') as HTMLImageElement | null;
                out.push({ code: m[1], alt: img?.alt || '' });
              }
            }
            return out;
          }).catch(() => []);

          const before = orderedIds.length;
          for (const entry of domEntries) {
            pushId(entry.code);
            if (entry.alt) {
              mergeCandidate(
                harvestCtx.candidates,
                { reelId: entry.code, reelUrl: `https://www.instagram.com/reel/${entry.code}/`, caption: entry.alt },
                'https://www.instagram.com/reel/'
              );
            }
          }
          for (const c of harvestCtx.candidates.keys()) pushId(c);
          const after = orderedIds.length;
          job.totalDiscovered = after;

          if (after > before) {
            idleRounds = 0;
            this.appendJobLog(job, `Cuộn Instagram #${scrollRound}: phát hiện ${after} Reels (+${after - before}).`);
            this.persistJobsToDisk();
          } else {
            idleRounds++;
          }

          if (job.maxReels > 0 && after >= job.maxReels) break;

          // Incremental check: if top N items already include 6 consecutive existing items on disk, stop scrolling early
          if (job.incrementalOnly && existingDiskIds.size > 0 && job.maxReels === 0) {
            let consecutiveExisting = 0;
            for (const id of orderedIds) {
              if (existingDiskIds.has(id)) {
                consecutiveExisting++;
                if (consecutiveExisting >= 6) break;
              } else {
                consecutiveExisting = 0;
              }
            }
            if (consecutiveExisting >= 6) {
              this.appendJobLog(job, 'Đã gặp các Reels cũ đã có sẵn trong máy (Incremental Mode) — dừng cuộn sớm.');
              break;
            }
          }

          if (idleRounds >= 4) {
            break;
          }

          await page.evaluate(() => window.scrollBy(0, Math.max(1200, window.innerHeight * 1.6))).catch(() => {});
          await page.waitForTimeout(1600);
        }
      }

      if (job.cancelRequested || (job.status as JobStatus) === 'BLOCKED_CAPTCHA') return;

      const finalIds = job.maxReels > 0 ? orderedIds.slice(0, job.maxReels) : orderedIds;
      job.totalDiscovered = finalIds.length;

      if (finalIds.length === 0) {
        job.status = 'NEEDS_LOGIN';
        job.loginRecommended = true;
        job.message = 'Kênh Instagram yêu cầu đăng nhập để xem danh sách Reels. Hãy bấm "Đăng nhập IG" trên thanh công cụ rồi chạy lại.';
        job.completedAt = new Date().toISOString();
        this.appendJobLog(job, job.message);
        return;
      }

      const cookies = await context.cookies('https://www.instagram.com').catch(() => []);
      const cookieHeader = cookies.map((c) => `${c.name}=${c.value}`).join('; ');

      await closeBrowserFn();
      closeBrowserFn = async () => {};

      const existingFileMap = this.indexExistingFilesByReelId(job.outputDir);
      job.status = 'DOWNLOADING';
      job.items = finalIds.map((code, idx) => {
        const cand = harvestCtx.candidates.get(code);
        const item: ReelDownloadItem = {
          index: idx + 1,
          reelId: code,
          reelUrl: `https://www.instagram.com/reel/${code}/`,
          caption: cand?.caption || '',
          viewCountText: cand?.viewCountText || '',
          durationSec: cand?.durationSec || 0,
          publishTime: cand?.publishTime || null,
          qualityLabel: 'Pending',
          width: 0,
          height: 0,
          hasAudio: false,
          sizeBytes: 0,
          sha256: '',
          fileName: `${code}.mp4`,
          filePath: path.join(job.outputDir, `${code}.mp4`),
          status: 'DISCOVERED',
        };
        this.applyItemMetadataAndFileName(item, job.outputDir, existingFileMap);
        return item;
      });

      this.appendJobLog(job, `Bắt đầu tải ${job.items.length} Instagram Reels về ${job.outputDir}...`);
      this.persistJobsToDisk();

      await this.runBatchItemsDownload(job, concurrency, async (item) => {
        const cand = harvestCtx.candidates.get(item.reelId);
        const tmpOut = path.join(job.outputDir, `.tmp_${item.reelId}.mp4`);
        const tmpInfoJson = path.join(job.outputDir, `.tmp_${item.reelId}.info.json`);
        if (fs.existsSync(tmpOut)) {
          try { fs.unlinkSync(tmpOut); } catch {}
        }
        if (fs.existsSync(tmpInfoJson)) {
          try { fs.unlinkSync(tmpInfoJson); } catch {}
        }

        // Strategy 1: Direct CDN MP4 if already harvested from Instagram GraphQL/API
        if (cand?.hdUrl) {
          try {
            await downloadStreamToFile(cand.hdUrl, tmpOut, cookieHeader, 'https://www.instagram.com/');
            const p = await probeLocalMediaFile(tmpOut);
            if (p.hasVideo) {
              if (!item.caption) {
                const meta = await this.fetchMetadataViaYtDlp(item.reelUrl);
                if (meta && meta.caption) {
                  item.caption = meta.caption;
                  item.title = meta.title;
                  item.hashtags = meta.hashtags;
                }
              }
              this.applyItemMetadataAndFileName(item, job.outputDir);
              if (fs.existsSync(item.filePath)) fs.unlinkSync(item.filePath);
              fs.renameSync(tmpOut, item.filePath);
              return;
            }
          } catch {}
        }

        // Strategy 2: yt-dlp with ffmpeg-static & --write-info-json
        await execFileAsync(
          config.ytDlpBin,
          [
            '--ffmpeg-location', FFMPEG_BIN,
            '--merge-output-format', 'mp4',
            '--write-info-json',
            '--no-warnings',
            '-o', tmpOut,
            item.reelUrl,
          ],
          { timeout: 120_000 }
        );

        if (fs.existsSync(tmpInfoJson)) {
          try {
            const infoObj = JSON.parse(fs.readFileSync(tmpInfoJson, 'utf8'));
            const fullDesc = String(infoObj.description || '');
            const fullTitle = String(infoObj.title || '');
            const fullTags = Array.isArray(infoObj.tags) ? infoObj.tags.map(String) : [];
            if (fullDesc || fullTitle) {
              item.caption = fullDesc || fullTitle;
              item.title = fullTitle;
              item.hashtags = fullTags;
            }
          } catch {}
          try { fs.unlinkSync(tmpInfoJson); } catch {}
        }

        this.applyItemMetadataAndFileName(item, job.outputDir);

        if (!fs.existsSync(tmpOut)) {
          throw new Error('Không tải được luồng MP4 từ Instagram.');
        }
        if (fs.existsSync(item.filePath)) fs.unlinkSync(item.filePath);
        fs.renameSync(tmpOut, item.filePath);
      });
    } finally {
      await closeBrowserFn();
      if (usedPersistentProfile) {
        this.profileBusy = false;
      }
    }
  }

  // ============================================================================
  // SHARED BATCH RUNNER FOR YOUTUBE & INSTAGRAM
  // ============================================================================
  private async runBatchItemsDownload(
    job: ReelsDownloadJob,
    concurrency: number,
    downloadFn: (item: ReelDownloadItem) => Promise<void>
  ) {
    const existingFileMap = this.indexExistingFilesByReelId(job.outputDir);
    const existingManifestMap = new Map<string, any>();
    if (fs.existsSync(job.manifestPath)) {
      try {
        const oldManifest = JSON.parse(fs.readFileSync(job.manifestPath, 'utf8'));
        if (Array.isArray(oldManifest?.reels)) {
          for (const r of oldManifest.reels) {
            if (r?.reelId) existingManifestMap.set(String(r.reelId), r);
          }
        }
      } catch {}
    }

    let cursor = 0;
    const workerCount = Math.max(1, Math.min(concurrency, 4, job.items.length));

    const updateJobProgress = () => {
      job.processedCount = job.downloadedCount + job.skippedCount + job.failedCount;
      job.progressPercent =
        job.totalDiscovered > 0 ? Math.min(100, Math.round((job.processedCount * 100) / job.totalDiscovered)) : 0;
      job.message = `Đang tải [${job.platform.toUpperCase()}]: ${job.processedCount}/${job.totalDiscovered} (Mới: ${job.downloadedCount}, Đã có sẵn: ${job.skippedCount}, Lỗi: ${job.failedCount})`;
      job.updatedAt = new Date().toISOString();
    };

    const writeManifestSnapshot = () => {
      try {
        const mergedReelsMap = new Map<string, any>(existingManifestMap);
        for (const item of job.items) {
          mergedReelsMap.set(item.reelId, {
            index: item.index,
            reelId: item.reelId,
            reelUrl: item.reelUrl,
            fileName: item.fileName,
            filePath: item.filePath,
            status: item.status,
            title: item.title || '',
            caption: item.caption,
            hashtags: item.hashtags || [],
            viewCountText: item.viewCountText,
            durationSec: item.durationSec,
            publishTime: item.publishTime,
            qualityLabel: item.qualityLabel,
            width: item.width,
            height: item.height,
            hasAudio: item.hasAudio,
            sizeBytes: item.sizeBytes,
            sha256: item.sha256,
            downloadedAt: item.downloadedAt || null,
          });
        }
        const mergedList = Array.from(mergedReelsMap.values()).map((r, i) => ({
          ...r,
          index: i + 1,
        }));
        const manifestData = {
          schemaVersion: '1.2',
          platform: job.platform,
          pageSlug: job.pageSlug,
          pageTitle: job.pageTitle,
          inputUrl: job.inputUrl,
          normalizedPageUrl: job.normalizedPageUrl,
          reelsTabUrl: job.reelsTabUrl,
          outputDir: job.outputDir,
          updatedAt: new Date().toISOString(),
          hasReachedEndOfPage: job.hasReachedEndOfPage,
          stats: {
            totalDiscovered: mergedList.length,
            downloadedCount: job.downloadedCount,
            skippedCount: job.skippedCount,
            failedCount: job.failedCount,
          },
          reels: mergedList,
        };
        fs.writeFileSync(job.manifestPath, JSON.stringify(manifestData, null, 2), 'utf8');
      } catch {}
    };

    const runWorker = async () => {
      while (!job.cancelRequested) {
        const idx = cursor++;
        if (idx >= job.items.length) break;
        const item = job.items[idx];
        const prev = existingManifestMap.get(item.reelId);
        if (!item.caption && prev?.caption) {
          item.caption = prev.caption;
          item.title = prev.title;
          item.hashtags = prev.hashtags;
        }
        this.applyItemMetadataAndFileName(item, job.outputDir, existingFileMap);

        if (!job.forceRedownload && fs.existsSync(item.filePath)) {
          const stat = fs.statSync(item.filePath);
          if (stat.size > 25 * 1024) {
            if (!item.caption && item.reelUrl) {
              const fetched = await this.fetchMetadataViaYtDlp(item.reelUrl);
              if (fetched && fetched.caption) {
                item.caption = fetched.caption;
                item.title = fetched.title;
                item.hashtags = fetched.hashtags;
                this.applyItemMetadataAndFileName(item, job.outputDir, existingFileMap);
              }
            }
            const probe = prev?.width
              ? {
                  hasVideo: true,
                  hasAudio: prev.hasAudio ?? true,
                  width: prev.width,
                  height: prev.height,
                  durationSec: prev.durationSec || item.durationSec,
                }
              : await probeLocalMediaFile(item.filePath);
            if (probe.hasVideo) {
              item.status = 'SKIPPED_EXISTING';
              item.sizeBytes = stat.size;
              item.width = probe.width;
              item.height = probe.height;
              item.hasAudio = probe.hasAudio;
              item.durationSec = probe.durationSec || item.durationSec;
              item.qualityLabel =
                prev?.qualityLabel || (Math.max(probe.width, probe.height) >= 1080 ? '1080p Full HD' : '720p HD');
              item.sha256 = prev?.sha256 || (await computeFileSha256(item.filePath));
              item.downloadedAt = prev?.downloadedAt || stat.mtime.toISOString();
              job.skippedCount++;
              updateJobProgress();
              continue;
            }
          }
        }

        item.status = 'DOWNLOADING';
        try {
          await downloadFn(item);
          const probe = await probeLocalMediaFile(item.filePath);
          if (!probe.hasVideo) {
            throw new Error('File tải về không hợp lệ.');
          }
          const stat = fs.statSync(item.filePath);
          const maxDim = Math.max(probe.width, probe.height);
          item.width = probe.width;
          item.height = probe.height;
          item.hasAudio = probe.hasAudio;
          item.durationSec = probe.durationSec || item.durationSec;
          item.sizeBytes = stat.size;
          item.qualityLabel =
            maxDim >= 1080 ? '1080p Full HD' : maxDim >= 720 ? '720p HD' : `${Math.min(probe.width, probe.height) || 480}p MP4`;
          item.sha256 = await computeFileSha256(item.filePath);
          item.downloadedAt = new Date().toISOString();
          item.status = 'COMPLETED';

          job.downloadedCount++;
          updateJobProgress();
          this.appendJobLog(
            job,
            `[#${item.index}/${job.totalDiscovered}] Đã tải ${item.fileName} (${item.qualityLabel}, ${(stat.size / 1048576).toFixed(2)} MB, Audio: ${item.hasAudio ? 'OK' : 'No'})`
          );
          if (job.processedCount % 2 === 0) {
            writeManifestSnapshot();
            this.persistJobsToDisk();
          }
        } catch (err: any) {
          item.status = 'FAILED';
          item.error = err?.message || 'Lỗi tải video';
          job.failedCount++;
          updateJobProgress();
          this.appendJobLog(job, `[#${item.index}/${job.totalDiscovered}] Lỗi ${item.reelId}: ${item.error}`);
        }
      }
    };

    await Promise.all(Array.from({ length: workerCount }, () => runWorker()));
    writeManifestSnapshot();

    if (job.cancelRequested) {
      job.status = 'CANCELLED';
      job.message = `Đã dừng theo yêu cầu. Đã lưu ${job.downloadedCount + job.skippedCount}/${job.totalDiscovered} video tại ${job.outputDir}`;
    } else if (job.failedCount > 0 && job.downloadedCount + job.skippedCount > 0) {
      job.status = 'PARTIAL';
      job.message = `Hoàn tất ${job.downloadedCount + job.skippedCount}/${job.totalDiscovered} video (Mới: ${job.downloadedCount}, Có sẵn: ${job.skippedCount}, Lỗi: ${job.failedCount}).`;
    } else if (job.downloadedCount + job.skippedCount === 0 && job.failedCount > 0) {
      job.status = 'FAILED';
      job.message = `Không tải được các video (${job.failedCount} lỗi).`;
    } else {
      job.status = 'COMPLETED';
      job.progressPercent = 100;
      job.message = `Hoàn tất 100%! Tổng cộng ${job.downloadedCount + job.skippedCount}/${job.totalDiscovered} video của "${job.pageTitle}" đã có trong máy (${job.downloadedCount} tải mới, ${job.skippedCount} đã có sẵn).`;
    }
    job.completedAt = new Date().toISOString();
    this.appendJobLog(job, job.message);
  }

  // ============================================================================
  // PLATFORM 3: FACEBOOK PAGE / REELS ENGINE
  // ============================================================================
  private async executeFacebookJob(job: ReelsDownloadJob, target: NormalizedChannelTarget, concurrency: number) {
    fs.mkdirSync(job.outputDir, { recursive: true });
    const existingDiskIds = this.getExistingDownloadedIdsOnDisk(job.outputDir);

    let closeBrowserFn: () => Promise<void> = async () => {};
    let context: BrowserContext;
    let usedPersistentProfile = false;

    if (!this.profileBusy) {
      this.profileBusy = true;
      usedPersistentProfile = true;
      fs.mkdirSync(config.browserProfileDir, { recursive: true });
      context = await this.launchPersistentChrome(config.browserProfileDir, true);
      closeBrowserFn = async () => {
        try { await context.close(); } catch {}
        this.profileBusy = false;
      };
    } else {
      this.appendJobLog(job, 'Profile chính đang bận; khởi chạy phiên Chrome độc lập.');
      const eph = await this.launchEphemeralChrome();
      context = eph.context;
      closeBrowserFn = eph.closeAll;
    }

    try {
      job.status = 'SCANNING';
      job.message = `Đang mở trang Reels: ${target.reelsTabUrl}`;
      this.persistJobsToDisk();

      const harvestCtx: HarvestContext = {
        candidates: new Map<string, ReelStreamCandidate>(),
      };

      const orderedReelIds: string[] = [];
      const orderedSet = new Set<string>();
      const pushOrderedId = (rid: string, views?: string, thumb?: string) => {
        if (!rid || !/^\d{8,25}$/.test(rid)) return;
        if (!orderedSet.has(rid)) {
          orderedSet.add(rid);
          orderedReelIds.push(rid);
        }
        mergeCandidate(harvestCtx.candidates, {
          reelId: rid,
          reelUrl: `https://www.facebook.com/reel/${rid}/`,
          viewCountText: views,
          thumbnailUrl: thumb,
        });
      };

      const page = context.pages()[0] || (await context.newPage());

      page.on('response', async (res) => {
        try {
          const rUrl = res.url();
          if (rUrl.includes('fbcdn.net')) return;
          const ct = (res.headers()['content-type'] || '').toLowerCase();
          if (
            rUrl.includes('graphql') ||
            (rUrl.includes('facebook.com') &&
              (ct.includes('json') || ct.includes('javascript') || ct.includes('html') || ct.includes('text')))
          ) {
            const txt = await res.text();
            harvestPayloadText(txt, harvestCtx);
          }
        } catch {}
      });

      if (target.isSingleReel && target.singleReelId) {
        pushOrderedId(target.singleReelId);
        await page.goto(target.reelsTabUrl, { waitUntil: 'domcontentloaded', timeout: 35_000 });
        await page.waitForTimeout(2500);
        const capCheck = await detectCaptchaOrCheckpoint(page);
        if (capCheck.blocked) {
          job.status = 'BLOCKED_CAPTCHA';
          job.message = capCheck.reason;
          job.completedAt = new Date().toISOString();
          this.appendJobLog(job, capCheck.reason);
          this.persistJobsToDisk();
          return;
        }
        const html = await page.content();
        harvestPayloadText(html, harvestCtx);
      } else {
        await page.goto(target.reelsTabUrl, { waitUntil: 'domcontentloaded', timeout: 35_000 });
        await page.waitForTimeout(3000);

        const capCheck = await detectCaptchaOrCheckpoint(page);
        if (capCheck.blocked) {
          job.status = 'BLOCKED_CAPTCHA';
          job.message = capCheck.reason;
          job.completedAt = new Date().toISOString();
          this.appendJobLog(job, capCheck.reason);
          this.persistJobsToDisk();
          return;
        }

        const rawPageTitle = await page.title().catch(() => '');
        if (rawPageTitle && !rawPageTitle.toLowerCase().startsWith('log in') && rawPageTitle !== 'Facebook') {
          job.pageTitle = rawPageTitle
            .replace(/\s*Reels\s*\|\s*Facebook$/i, '')
            .replace(/\s*\|\s*Facebook$/i, '')
            .trim() || job.pageTitle;
        }

        const initialHtml = await page.content();
        harvestPayloadText(initialHtml, harvestCtx);
        if (harvestCtx.pageTitle) {
          job.pageTitle = harvestCtx.pageTitle;
        }

        const closeOverlayBtn = page.locator('[role="dialog"] [aria-label="Close"], [role="dialog"] [aria-label="Đóng"]').first();
        if ((await closeOverlayBtn.count().catch(() => 0)) > 0) {
          await closeOverlayBtn.click({ timeout: 2000 }).catch(() => {});
          await page.waitForTimeout(800);
        }

        let idleRounds = 0;
        let scrollRound = 0;
        const maxScrollRounds = 160;

        while (scrollRound < maxScrollRounds && !job.cancelRequested) {
          scrollRound++;

          const stepCaptcha = await detectCaptchaOrCheckpoint(page);
          if (stepCaptcha.blocked) {
            job.status = 'BLOCKED_CAPTCHA';
            job.message = stepCaptcha.reason;
            this.appendJobLog(job, stepCaptcha.reason);
            break;
          }

          const domCards = await page.evaluate(() => {
            const results: Array<{ id: string; views: string; thumb: string }> = [];
            const seen = new Set<string>();
            const anchors = Array.from(document.querySelectorAll('a[href]'));
            for (const a of anchors) {
              const href = (a as HTMLAnchorElement).href || '';
              const m = href.match(/\/reel\/(\d{8,25})/);
              if (m && m[1] && !seen.has(m[1])) {
                seen.add(m[1]);
                const views = ((a as HTMLElement).innerText || '').trim().replace(/\s+/g, ' ').slice(0, 40);
                const img = a.querySelector('img');
                const thumb = img ? img.src || '' : '';
                results.push({ id: m[1], views, thumb });
              }
            }
            return results;
          }).catch(() => []);

          const beforeCount = orderedReelIds.length;
          for (const card of domCards) {
            pushOrderedId(card.id, card.views, card.thumb);
          }
          for (const candId of harvestCtx.candidates.keys()) {
            if (!orderedSet.has(candId)) {
              pushOrderedId(candId);
            }
          }

          const afterCount = orderedReelIds.length;
          job.totalDiscovered = afterCount;
          if (harvestCtx.pageTitle) {
            job.pageTitle = harvestCtx.pageTitle;
          }

          if (afterCount > beforeCount) {
            idleRounds = 0;
            job.message = `Đang quét danh sách Reels trên Page (${afterCount} video đã tìm thấy)...`;
            this.appendJobLog(job, `Cuộn trang #${scrollRound}: phát hiện ${afterCount} Reels (+${afterCount - beforeCount}).`);
            this.persistJobsToDisk();
          } else {
            idleRounds++;
          }

          if (job.maxReels > 0 && afterCount >= job.maxReels) {
            this.appendJobLog(job, `Đã đạt giới hạn yêu cầu maxReels=${job.maxReels}.`);
            break;
          }

          // Smart Incremental Mode: if scanning a Favorite Page that already has videos on disk, stop scrolling once we hit 8 consecutive already-downloaded Reels
          if (job.incrementalOnly && existingDiskIds.size > 0 && job.maxReels === 0) {
            let consecutiveExisting = 0;
            for (const rid of orderedReelIds) {
              if (existingDiskIds.has(rid)) {
                consecutiveExisting++;
                if (consecutiveExisting >= 8) break;
              } else {
                consecutiveExisting = 0;
              }
            }
            if (consecutiveExisting >= 8) {
              this.appendJobLog(
                job,
                `Đã gặp chuỗi 8 Reels cũ đã có sẵn trong máy (Incremental Mode) — dừng cuộn sớm để xử lý ngay các video mới!`
              );
              break;
            }
          }

          if (harvestCtx.latestHasNextPage === false && idleRounds >= 2) {
            job.hasReachedEndOfPage = true;
            this.appendJobLog(job, `Đã chạm đáy danh sách Reels của Page (has_next_page=false, tổng ${afterCount} Reels).`);
            break;
          }

          if (idleRounds >= 6) {
            if (harvestCtx.latestHasNextPage === true) {
              job.loginRecommended = true;
              this.appendJobLog(
                job,
                `Facebook ngừng tải thêm phân trang sau ${afterCount} Reels (có thể giới hạn khách chưa đăng nhập). Đã gom toàn bộ ${afterCount} Reels khả dụng.`
              );
            } else {
              job.hasReachedEndOfPage = true;
              this.appendJobLog(job, `Hoàn tất cuộn trang sau ${afterCount} Reels.`);
            }
            break;
          }

          const dialogClose = page.locator('[role="dialog"] [aria-label="Close"], [role="dialog"] [aria-label="Đóng"]').first();
          if ((await dialogClose.count().catch(() => 0)) > 0) {
            await dialogClose.click({ timeout: 1200 }).catch(() => {});
          }

          await page.evaluate(() => {
            window.scrollBy({ top: Math.max(1300, window.innerHeight * 1.75), behavior: 'instant' as ScrollBehavior });
          }).catch(() => {});
          await page.waitForTimeout(1600);
        }
      }

      if (job.cancelRequested || job.status === 'BLOCKED_CAPTCHA') {
        this.persistJobsToDisk();
        return;
      }

      const finalIds = job.maxReels > 0 ? orderedReelIds.slice(0, job.maxReels) : orderedReelIds;
      job.totalDiscovered = finalIds.length;

      if (finalIds.length === 0) {
        const curUrl = page.url().toLowerCase();
        if (curUrl.includes('/login')) {
          job.status = 'NEEDS_LOGIN';
          job.loginRecommended = true;
          job.message = 'Page yêu cầu đăng nhập Facebook để xem tab Reels. Hãy bấm "Đăng nhập FB" trên thanh công cụ rồi chạy lại.';
        } else {
          job.status = 'COMPLETED';
          job.message = 'Không tìm thấy video Reels công khai nào trên Page này.';
        }
        job.completedAt = new Date().toISOString();
        this.appendJobLog(job, job.message);
        this.persistJobsToDisk();
        return;
      }

      const cookies = await context.cookies('https://www.facebook.com').catch(() => []);
      const cookieHeader = cookies.map((c) => `${c.name}=${c.value}`).join('; ');

      const unresolvedIds = finalIds.filter((rid) => {
        if (!job.forceRedownload && existingDiskIds.has(rid)) return false;
        const c = harvestCtx.candidates.get(rid);
        return !c || (!c.hdUrl && !c.sdUrl && !c.dashVideoUrl);
      });

      if (unresolvedIds.length > 0) {
        this.appendJobLog(job, `Đang giải mã luồng trực tiếp cho ${unresolvedIds.length} Reels chưa có sẵn link trong lưới...`);
        for (const rid of unresolvedIds) {
          if (job.cancelRequested) break;
          try {
            const resp = await context.request.get(`https://www.facebook.com/reel/${rid}/`, {
              headers: {
                'User-Agent': DESKTOP_UA,
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'Accept-Language': 'vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7',
                'Sec-Fetch-Dest': 'document',
                'Sec-Fetch-Mode': 'navigate',
                'Sec-Fetch-Site': 'none',
              },
              timeout: 20_000,
            });
            if (resp.ok()) {
              const reelHtml = await resp.text();
              harvestPayloadText(reelHtml, harvestCtx);
            }
          } catch {}
        }
      }

      await closeBrowserFn();
      closeBrowserFn = async () => {};

      const existingFileMap = this.indexExistingFilesByReelId(job.outputDir);
      const existingManifestMap = new Map<string, any>();
      if (fs.existsSync(job.manifestPath)) {
        try {
          const oldManifest = JSON.parse(fs.readFileSync(job.manifestPath, 'utf8'));
          if (Array.isArray(oldManifest?.reels)) {
            for (const r of oldManifest.reels) {
              if (r?.reelId) existingManifestMap.set(String(r.reelId), r);
            }
          }
        } catch {}
      }

      job.status = 'DOWNLOADING';
      job.items = finalIds.map((rid, idx) => {
        const cand = harvestCtx.candidates.get(rid);
        const prev = existingManifestMap.get(rid);
        const item: ReelDownloadItem = {
          index: idx + 1,
          reelId: rid,
          reelUrl: `https://www.facebook.com/reel/${rid}/`,
          caption: cand?.caption || prev?.caption || '',
          viewCountText: cand?.viewCountText || prev?.viewCountText || '',
          durationSec: cand?.durationSec || prev?.durationSec || 0,
          publishTime: cand?.publishTime || prev?.publishTime || null,
          qualityLabel: 'Pending',
          width: 0,
          height: 0,
          hasAudio: false,
          sizeBytes: 0,
          sha256: '',
          fileName: `${rid}.mp4`,
          filePath: path.join(job.outputDir, `${rid}.mp4`),
          status: 'DISCOVERED',
        };
        this.applyItemMetadataAndFileName(item, job.outputDir, existingFileMap);
        return item;
      });

      job.message = `Bắt đầu tải ${finalIds.length} Reels về ${job.outputDir}...`;
      this.appendJobLog(job, job.message);
      this.persistJobsToDisk();

      let cursor = 0;
      const workerCount = Math.max(1, Math.min(concurrency, 5, finalIds.length));

      const updateJobProgress = () => {
        job.processedCount = job.downloadedCount + job.skippedCount + job.failedCount;
        job.progressPercent =
          job.totalDiscovered > 0 ? Math.min(100, Math.round((job.processedCount * 100) / job.totalDiscovered)) : 0;
        job.message = `Đang tải [FACEBOOK]: ${job.processedCount}/${job.totalDiscovered} Reels (Mới: ${job.downloadedCount}, Đã có sẵn: ${job.skippedCount}, Lỗi: ${job.failedCount})`;
        job.updatedAt = new Date().toISOString();
      };

      const writeManifestSnapshot = () => {
        try {
          const mergedReelsMap = new Map<string, any>(existingManifestMap);
          for (const item of job.items) {
            mergedReelsMap.set(item.reelId, {
              index: item.index,
              reelId: item.reelId,
              reelUrl: item.reelUrl,
              fileName: item.fileName,
              filePath: item.filePath,
              status: item.status,
              title: item.title || '',
              caption: item.caption,
              hashtags: item.hashtags || [],
              viewCountText: item.viewCountText,
              durationSec: item.durationSec,
              publishTime: item.publishTime,
              qualityLabel: item.qualityLabel,
              width: item.width,
              height: item.height,
              hasAudio: item.hasAudio,
              sizeBytes: item.sizeBytes,
              sha256: item.sha256,
              downloadedAt: item.downloadedAt || null,
            });
          }
          const mergedList = Array.from(mergedReelsMap.values()).map((r, i) => ({
            ...r,
            index: i + 1,
          }));
          const manifestData = {
            schemaVersion: '1.2',
            platform: job.platform,
            pageSlug: job.pageSlug,
            pageTitle: job.pageTitle,
            inputUrl: job.inputUrl,
            normalizedPageUrl: job.normalizedPageUrl,
            reelsTabUrl: job.reelsTabUrl,
            outputDir: job.outputDir,
            updatedAt: new Date().toISOString(),
            hasReachedEndOfPage: job.hasReachedEndOfPage,
            stats: {
              totalDiscovered: mergedList.length,
              downloadedCount: job.downloadedCount,
              skippedCount: job.skippedCount,
              failedCount: job.failedCount,
            },
            reels: mergedList,
          };
          fs.writeFileSync(job.manifestPath, JSON.stringify(manifestData, null, 2), 'utf8');
        } catch {}
      };

      const runWorker = async () => {
        while (!job.cancelRequested) {
          const myIdx = cursor++;
          if (myIdx >= job.items.length) break;

          const item = job.items[myIdx];
          const cand = harvestCtx.candidates.get(item.reelId);
          if (cand?.caption && !item.caption) {
            item.caption = cand.caption;
          }
          this.applyItemMetadataAndFileName(item, job.outputDir, existingFileMap);

          if (!job.forceRedownload && fs.existsSync(item.filePath)) {
            const stat = fs.statSync(item.filePath);
            if (stat.size > 25 * 1024) {
              const prev = existingManifestMap.get(item.reelId);
              const mediaInfo = prev?.width
                ? {
                    hasVideo: true,
                    hasAudio: prev.hasAudio ?? true,
                    width: prev.width,
                    height: prev.height,
                    durationSec: prev.durationSec || item.durationSec,
                  }
                : await probeLocalMediaFile(item.filePath);

              if (mediaInfo.hasVideo) {
                item.status = 'SKIPPED_EXISTING';
                item.sizeBytes = stat.size;
                item.width = mediaInfo.width;
                item.height = mediaInfo.height;
                item.hasAudio = mediaInfo.hasAudio;
                item.durationSec = mediaInfo.durationSec || item.durationSec;
                item.qualityLabel = prev?.qualityLabel || (mediaInfo.height >= 1080 || mediaInfo.width >= 1080 ? '1080p Full HD' : '720p HD');
                item.sha256 = prev?.sha256 || (await computeFileSha256(item.filePath));
                item.downloadedAt = prev?.downloadedAt || stat.mtime.toISOString();
                job.skippedCount++;
                updateJobProgress();
                continue;
              }
            }
          }

          if (!cand || (!cand.hdUrl && !cand.sdUrl && !cand.dashVideoUrl)) {
            item.status = 'FAILED';
            item.error = 'Không tìm thấy luồng MP4 trực tiếp cho Reel này.';
            job.failedCount++;
            this.appendJobLog(job, `[#${item.index}] Thất bại ${item.reelId}: ${item.error}`);
            updateJobProgress();
            continue;
          }

          item.status = 'DOWNLOADING';
          const tmpVideo = path.join(job.outputDir, `.tmp_${item.reelId}_v.mp4`);
          const tmpAudio = path.join(job.outputDir, `.tmp_${item.reelId}_a.m4a`);
          const tmpFinal = path.join(job.outputDir, `.tmp_${item.reelId}_final.mp4`);

          try {
            let finalReady = false;
            const maxDashDim = Math.max(cand.dashVideoWidth || 0, cand.dashVideoHeight || 0);

            if (
              job.preferQuality === '1080p' &&
              cand.dashVideoUrl &&
              cand.dashAudioUrl &&
              maxDashDim >= 1080
            ) {
              try {
                await Promise.all([
                  downloadStreamToFile(cand.dashVideoUrl, tmpVideo, cookieHeader),
                  downloadStreamToFile(cand.dashAudioUrl, tmpAudio, cookieHeader),
                ]);
                item.status = 'MUXING_AUDIO';
                const muxOk = await muxVideoAndAudioFiles(tmpVideo, tmpAudio, tmpFinal);
                if (muxOk) {
                  finalReady = true;
                  item.qualityLabel = '1080p Full HD (Muxed)';
                }
              } catch {}
            }

            if (!finalReady) {
              const primaryUrl =
                job.preferQuality === 'sd'
                  ? cand.sdUrl || cand.hdUrl || cand.dashVideoUrl!
                  : cand.hdUrl || cand.sdUrl || cand.dashVideoUrl!;

              await downloadStreamToFile(primaryUrl, tmpVideo, cookieHeader);
              const vProbe = await probeLocalMediaFile(tmpVideo);

              if (!vProbe.hasVideo) {
                throw new Error('File tải về không chứa luồng hình ảnh hợp lệ.');
              }

              if (!vProbe.hasAudio && cand.dashAudioUrl) {
                item.status = 'MUXING_AUDIO';
                await downloadStreamToFile(cand.dashAudioUrl, tmpAudio, cookieHeader);
                const muxOk = await muxVideoAndAudioFiles(tmpVideo, tmpAudio, tmpFinal);
                if (muxOk) {
                  finalReady = true;
                }
              }

              if (!finalReady) {
                if (fs.existsSync(tmpFinal)) fs.unlinkSync(tmpFinal);
                fs.renameSync(tmpVideo, tmpFinal);
                finalReady = true;
              }
            }

            const finalProbe = await probeLocalMediaFile(tmpFinal);
            if (!finalProbe.hasVideo) {
              throw new Error('Kiểm tra hậu kỳ MP4 thất bại.');
            }

            if (fs.existsSync(item.filePath)) {
              fs.unlinkSync(item.filePath);
            }
            fs.renameSync(tmpFinal, item.filePath);

            const stat = fs.statSync(item.filePath);
            const maxDim = Math.max(finalProbe.width, finalProbe.height);
            item.width = finalProbe.width;
            item.height = finalProbe.height;
            item.hasAudio = finalProbe.hasAudio;
            item.durationSec = finalProbe.durationSec || item.durationSec;
            item.sizeBytes = stat.size;
            item.qualityLabel =
              maxDim >= 1080
                ? '1080p Full HD'
                : maxDim >= 720
                ? '720p HD'
                : maxDim > 0
                ? `${Math.min(finalProbe.width, finalProbe.height)}p MP4`
                : cand.hdUrl
                ? 'HD MP4'
                : 'SD MP4';
            item.sha256 = await computeFileSha256(item.filePath);
            item.downloadedAt = new Date().toISOString();
            item.status = 'COMPLETED';

            job.downloadedCount++;
            updateJobProgress();
            this.appendJobLog(
              job,
              `[#${item.index}/${job.totalDiscovered}] Đã tải ${item.fileName} (${item.qualityLabel}, ${(stat.size / 1048576).toFixed(2)} MB, Audio: ${item.hasAudio ? 'OK' : 'No'})`
            );

            if (job.processedCount % 3 === 0) {
              writeManifestSnapshot();
              this.persistJobsToDisk();
            }
          } catch (dlErr: any) {
            item.status = 'FAILED';
            item.error = dlErr?.message || 'Lỗi tải luồng video';
            job.failedCount++;
            updateJobProgress();
            this.appendJobLog(job, `[#${item.index}/${job.totalDiscovered}] Lỗi ${item.reelId}: ${item.error}`);
          } finally {
            try { if (fs.existsSync(tmpVideo)) fs.unlinkSync(tmpVideo); } catch {}
            try { if (fs.existsSync(tmpAudio)) fs.unlinkSync(tmpAudio); } catch {}
            try { if (fs.existsSync(tmpFinal)) fs.unlinkSync(tmpFinal); } catch {}
          }
        }
      };

      await Promise.all(Array.from({ length: workerCount }, () => runWorker()));
      writeManifestSnapshot();

      if (job.cancelRequested) {
        job.status = 'CANCELLED';
        job.message = `Đã dừng theo yêu cầu. Đã lưu ${job.downloadedCount + job.skippedCount}/${job.totalDiscovered} Reels tại ${job.outputDir}`;
      } else if (job.failedCount > 0 && job.downloadedCount + job.skippedCount > 0) {
        job.status = 'PARTIAL';
        job.message = `Hoàn tất ${job.downloadedCount + job.skippedCount}/${job.totalDiscovered} Reels (Mới: ${job.downloadedCount}, Có sẵn: ${job.skippedCount}, Lỗi: ${job.failedCount}).`;
      } else if (job.downloadedCount + job.skippedCount === 0 && job.failedCount > 0) {
        job.status = 'FAILED';
        job.message = `Không tải được các Reels (${job.failedCount} lỗi).`;
      } else {
        job.status = 'COMPLETED';
        job.progressPercent = 100;
        job.message = `Hoàn tất 100%! Tổng cộng ${job.downloadedCount + job.skippedCount}/${job.totalDiscovered} Reels của "${job.pageTitle}" đã có trong máy (${job.downloadedCount} tải mới, ${job.skippedCount} đã có sẵn).`;
      }

      job.completedAt = new Date().toISOString();
      this.appendJobLog(job, job.message);
      this.persistJobsToDisk();
    } finally {
      await closeBrowserFn();
      if (usedPersistentProfile) {
        this.profileBusy = false;
      }
    }
  }

  public listLibraryPages() {
    const baseDir = config.videoLibraryDir;
    if (!fs.existsSync(baseDir)) {
      return { libraryRoot: baseDir, pages: [] };
    }
    const entries = fs.readdirSync(baseDir, { withFileTypes: true }).filter((d) => d.isDirectory());
    const pages = entries.map((dir) => {
      const pagePath = path.join(baseDir, dir.name);
      const manifestPath = path.join(pagePath, 'manifest.json');
      let manifest: any = null;
      if (fs.existsSync(manifestPath)) {
        try {
          manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
        } catch {}
      }
      const mp4Files = fs
        .readdirSync(pagePath)
        .filter((f) => f.toLowerCase().endsWith('.mp4') && !f.startsWith('.tmp_'));
      const totalBytes = mp4Files.reduce((acc, f) => {
        try {
          return acc + fs.statSync(path.join(pagePath, f)).size;
        } catch {
          return acc;
        }
      }, 0);

      const inferredPlatform: SupportedPlatform =
        manifest?.platform ||
        (dir.name.startsWith('yt_') ? 'youtube' : dir.name.startsWith('ig_') ? 'instagram' : 'facebook');

      return {
        platform: inferredPlatform,
        pageSlug: dir.name,
        pageTitle: manifest?.pageTitle || dir.name,
        reelsTabUrl: manifest?.reelsTabUrl || '',
        folderPath: pagePath,
        manifestPath: fs.existsSync(manifestPath) ? manifestPath : null,
        videoCount: mp4Files.length,
        totalBytes,
        updatedAt: manifest?.updatedAt || null,
        reels: Array.isArray(manifest?.reels) ? manifest.reels : [],
      };
    });

    return {
      libraryRoot: baseDir,
      pages,
    };
  }
}

export const facebookReelsService = new FacebookReelsService();
