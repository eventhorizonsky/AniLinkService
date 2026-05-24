package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.MediaFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 播放进度缩略图（VTT雪碧图）生成服务
 *
 * 在元数据扫描完成后，使用 FFmpeg 从视频中按固定间隔截取帧并拼接为雪碧图，
 * 同时生成 WebVTT 文件描述每帧的时间范围和坐标，供 ArtPlayer 的
 * artplayer-plugin-vtt-thumbnail 插件使用。
 *
 * 输出目录：{thumbnailOutputDir}/vtt/{mediaFileId}/
 *   - sprite.jpg     雪碧图
 *   - thumbnails.vtt 时间-坐标映射
 */
@Log4j2
@Service
public class MediaVttThumbnailService {

    private static final int THUMB_WIDTH = 160;
    private static final int THUMB_HEIGHT = 90;
    private static final int MAX_THUMBS = 100;
    private static final int MIN_INTERVAL_SEC = 5;

    @Value("${media.thumbnail.output-dir:./data/thumbnails}")
    private String thumbnailOutputDir;

    /**
     * 为指定媒体文件生成 VTT 雪碧图缩略图（幂等：目录已存在则跳过）
     *
     * @param mediaFile 已完成元数据提取的 MediaFile
     */
    public void generateVttThumbnails(MediaFile mediaFile) {
        if (mediaFile.getId() == null || mediaFile.getFilePath() == null) {
            return;
        }

        Path vttDir = Paths.get(thumbnailOutputDir, "vtt", String.valueOf(mediaFile.getId()));
        Path spriteFile = vttDir.resolve("sprite.jpg");
        Path vttFile = vttDir.resolve("thumbnails.vtt");

        if (Files.exists(spriteFile) && Files.exists(vttFile)) {
            log.debug("VTT thumbnails already exist, skipping: {}", vttDir);
            return;
        }

        long durationMs = mediaFile.getDuration() != null ? mediaFile.getDuration() : 0;
        if (durationMs <= 0) {
            log.warn("Cannot generate VTT thumbnails for media [{}]: unknown duration", mediaFile.getId());
            return;
        }

        long durationSec = durationMs / 1000;
        int interval = (int) Math.max(MIN_INTERVAL_SEC, durationSec / MAX_THUMBS);
        int thumbCount = Math.min(MAX_THUMBS, (int) Math.max(1, durationSec / interval));
        int cols = (int) Math.ceil(Math.sqrt(thumbCount));
        int rows = (int) Math.ceil((double) thumbCount / cols);

        try {
            Files.createDirectories(vttDir);
        } catch (IOException e) {
            log.error("Failed to create VTT thumbnail directory: {}", vttDir, e);
            return;
        }

        String inputPath = mediaFile.getFilePath();

        try {
            // 使用 FFmpeg tile 滤镜生成雪碧图
            // fps=1/{interval} 每秒取一帧，tile={cols}x{rows} 拼接成网格
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-ss", "0",
                    "-i", inputPath,
                    "-vf", String.format("fps=1/%d,scale=%d:%d,tile=%dx%d",
                            interval, THUMB_WIDTH, THUMB_HEIGHT, cols, rows),
                    "-vframes", "1",
                    "-q:v", "5",
                    "-y",
                    spriteFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            int exitCode = process.waitFor();

            if (exitCode != 0 || !Files.exists(spriteFile) || Files.size(spriteFile) == 0) {
                log.warn("FFmpeg sprite generation failed for media [{}], exit code: {}", mediaFile.getId(), exitCode);
                cleanup(vttDir);
                return;
            }

            // 生成 WebVTT 文件
            generateVttFile(vttFile, thumbCount, cols, interval);

            log.info("VTT thumbnails generated for media [{}]: {} thumbs, {}x{} grid, interval {}s",
                    mediaFile.getId(), thumbCount, cols, rows, interval);
        } catch (IOException | InterruptedException e) {
            log.error("Error generating VTT thumbnails for media [{}]: {}", mediaFile.getId(), inputPath, e);
            cleanup(vttDir);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void generateVttFile(Path vttFile, int thumbCount, int cols, int intervalSec) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("WEBVTT\n\n");

        for (int i = 0; i < thumbCount; i++) {
            long startSec = (long) i * intervalSec;
            long endSec = startSec + intervalSec;

            int col = i % cols;
            int row = i / cols;
            int x = col * THUMB_WIDTH;
            int y = row * THUMB_HEIGHT;

            sb.append(formatTimestamp(startSec))
                    .append(" --> ")
                    .append(formatTimestamp(endSec))
                    .append("\n");
            sb.append("sprite.jpg#xywh=")
                    .append(x).append(",")
                    .append(y).append(",")
                    .append(THUMB_WIDTH).append(",")
                    .append(THUMB_HEIGHT)
                    .append("\n\n");
        }

        Files.writeString(vttFile, sb.toString(), StandardCharsets.UTF_8);
    }

    private String formatTimestamp(long totalSec) {
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return String.format("%02d:%02d:%02d.000", h, m, s);
    }

    private void cleanup(Path vttDir) {
        try {
            Files.deleteIfExists(vttDir.resolve("sprite.jpg"));
            Files.deleteIfExists(vttDir.resolve("thumbnails.vtt"));
            Files.deleteIfExists(vttDir);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    /**
     * 检查指定媒体文件是否存在 VTT 缩略图
     */
    public boolean hasVttThumbnails(Long mediaFileId) {
        if (mediaFileId == null) return false;
        Path vttFile = Paths.get(thumbnailOutputDir, "vtt", String.valueOf(mediaFileId), "thumbnails.vtt");
        return Files.exists(vttFile);
    }

    /**
     * 获取 VTT 缩略图目录路径
     */
    public Path getVttThumbnailDir(Long mediaFileId) {
        return Paths.get(thumbnailOutputDir, "vtt", String.valueOf(mediaFileId));
    }
}
