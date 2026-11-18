import { facebookReelsService } from '../services/facebookReels.service';

async function main() {
  const args = process.argv.slice(2);
  let targetUrl = '';
  let maxReels = 0;
  let preferQuality: '1080p' | 'hd' | 'sd' = '1080p';
  let forceRedownload = false;
  let incrementalOnly = false;
  let addFavorite = false;
  let outputDir: string | undefined;

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--max' && args[i + 1]) {
      maxReels = Number(args[++i] || 0);
    } else if (arg === '--quality' && args[i + 1]) {
      const q = args[++i].toLowerCase();
      preferQuality = q === 'sd' ? 'sd' : q === 'hd' || q === '720p' ? 'hd' : '1080p';
    } else if (arg === '--force') {
      forceRedownload = true;
    } else if (arg === '--incremental' || arg === '--new-only') {
      incrementalOnly = true;
    } else if (arg === '--favorite' || arg === '--watch') {
      addFavorite = true;
    } else if (arg === '--out' && args[i + 1]) {
      outputDir = args[++i];
    } else if (!arg.startsWith('--') && !targetUrl) {
      targetUrl = arg;
    }
  }

  if (!targetUrl) {
    console.error(
      'Usage: npm run download-page -- "<Facebook/Instagram/YouTube URL>" [--max N] [--quality 1080p|hd|sd] [--incremental] [--favorite] [--force] [--out DIR]'
    );
    process.exit(1);
  }

  console.log('===================================================================');
  console.log('Share Now - Multi-Platform Channel Downloader CLI (FB / IG / YT)');
  console.log(`Input URL    : ${targetUrl}`);
  console.log(`Max Videos   : ${maxReels > 0 ? maxReels : 'ALL (Toàn bộ Kênh/Page)'}`);
  console.log(`Quality      : ${preferQuality}`);
  console.log(`Mode         : ${incrementalOnly ? 'INCREMENTAL (Chỉ tải video mới)' : 'FULL SCAN'}`);
  console.log('===================================================================');

  if (addFavorite) {
    const { favorite: fav } = facebookReelsService.addOrUpdateFavorite({
      url: targetUrl,
      preferQuality,
      enabled: true,
    });
    console.log(`[Favorite] Đã lưu "${fav.title}" (${fav.platform.toUpperCase()}) vào danh sách tự quét hàng ngày.`);
  }

  const job = facebookReelsService.startPageDownloadJob({
    url: targetUrl,
    maxReels,
    preferQuality,
    forceRedownload,
    incrementalOnly,
    outputDir,
  });

  const completed = await facebookReelsService.waitForJobCompletion(job.jobId, 3_600_000);
  console.log('\n==================== KẾT QUẢ HOÀN TẤT ====================');
  console.log(
    JSON.stringify(
      {
        jobId: completed.jobId,
        platform: completed.platform,
        status: completed.status,
        pageTitle: completed.pageTitle,
        reelsTabUrl: completed.reelsTabUrl,
        outputDir: completed.outputDir,
        manifestPath: completed.manifestPath,
        totalDiscovered: completed.totalDiscovered,
        downloadedCount: completed.downloadedCount,
        skippedCount: completed.skippedCount,
        failedCount: completed.failedCount,
        hasReachedEndOfPage: completed.hasReachedEndOfPage,
        message: completed.message,
      },
      null,
      2
    )
  );

  process.exit(completed.status === 'COMPLETED' || completed.status === 'PARTIAL' ? 0 : 2);
}

main().catch((err) => {
  console.error('Fatal CLI error:', err);
  process.exit(1);
});
