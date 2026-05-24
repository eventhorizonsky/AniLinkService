package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.MediaFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * 播放进度缩略图（VTT雪碧图）生成服务
 *
 * 使用并行 seek 方式从视频中按固定间隔提取单帧，再用 Java 拼接为雪碧图，
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
    private static final int FRAME_EXTRACT_TIMEOUT_SEC = 30;
    private static final int OVERALL_TIMEOUT_MIN = 10;

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
        Path tempDir = vttDir.resolve("temp");

        try {
            cleanupTempDir(tempDir);
            Files.createDirectories(tempDir);

            // 并行 seek 提取单帧 — 大幅快于逐帧解码全片
            int parallelism = Math.max(1, Math.min(thumbCount,
                    Runtime.getRuntime().availableProcessors() * 2));
            ExecutorService executor = Executors.newFixedThreadPool(parallelism);
            List<Future<Boolean>> futures = new ArrayList<>(thumbCount);

            for (int i = 0; i < thumbCount; i++) {
                final int index = i;
                final long timestamp = (long) i * interval;
                futures.add(executor.submit(() -> extractFrame(inputPath, tempDir, index, timestamp)));
            }

            executor.shutdown();
            executor.awaitTermination(OVERALL_TIMEOUT_MIN, TimeUnit.MINUTES);

            int successCount = 0;
            for (int i = 0; i < futures.size(); i++) {
                try {
                    if (futures.get(i).isDone() && futures.get(i).get()) {
                        successCount++;
                    }
                } catch (Exception e) {
                    log.debug("Thumbnail {} extraction failed for media [{}]", i, mediaFile.getId());
                }
            }

            if (successCount == 0) {
                log.warn("All thumbnail extractions failed for media [{}]", mediaFile.getId());
                cleanup(vttDir);
                return;
            }

            log.debug("Extracted {}/{} thumbnails for media [{}]", successCount, thumbCount, mediaFile.getId());

            if (!stitchSprite(tempDir, thumbCount, cols, rows, spriteFile)) {
                log.warn("Failed to stitch sprite for media [{}]", mediaFile.getId());
                cleanup(vttDir);
                return;
            }

            generateVttFile(vttFile, thumbCount, cols, interval);

            log.info("VTT thumbnails generated for media [{}]: {} thumbs, {}x{} grid, interval {}s",
                    mediaFile.getId(), thumbCount, cols, rows, interval);
        } catch (IOException | InterruptedException e) {
            log.error("Error generating VTT thumbnails for media [{}]: {}", mediaFile.getId(), inputPath, e);
            cleanup(vttDir);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    /**
     * 使用 FFmpeg seek 快速提取单帧缩略图。
     * -ss 在 -i 之前使用关键帧跳转，-vframes 1 拿到一帧即停止。
     */
    private boolean extractFrame(String inputPath, Path tempDir, int index, long timestampSec) {
        Path thumbFile = tempDir.resolve(String.format("thumb_%04d.jpg", index));
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-ss", String.valueOf(timestampSec),
                    "-i", inputPath,
                    "-vframes", "1",
                    "-s", THUMB_WIDTH + "x" + THUMB_HEIGHT,
                    "-q:v", "5",
                    "-y",
                    thumbFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            boolean finished = process.waitFor(FRAME_EXTRACT_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0 && Files.exists(thumbFile) && Files.size(thumbFile) > 0;
        } catch (Exception e) {
            log.debug("Frame extraction failed for index {} at {}s: {}", index, timestampSec, e.getMessage());
            return false;
        }
    }

    /**
     * 将独立缩略图拼接为雪碧图，失败的格位填充黑色。
     */
    private boolean stitchSprite(Path tempDir, int thumbCount, int cols, int rows, Path spriteFile) {
        int spriteWidth = cols * THUMB_WIDTH;
        int spriteHeight = rows * THUMB_HEIGHT;
        BufferedImage sprite = new BufferedImage(spriteWidth, spriteHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sprite.createGraphics();

        int successCount = 0;
        for (int i = 0; i < thumbCount; i++) {
            Path thumbFile = tempDir.resolve(String.format("thumb_%04d.jpg", i));
            int col = i % cols;
            int row = i / cols;
            int x = col * THUMB_WIDTH;
            int y = row * THUMB_HEIGHT;

            try {
                if (Files.exists(thumbFile) && Files.size(thumbFile) > 0) {
                    BufferedImage thumb = ImageIO.read(thumbFile.toFile());
                    if (thumb != null) {
                        g.drawImage(thumb, x, y, null);
                        successCount++;
                        continue;
                    }
                }
            } catch (IOException ignored) {
            }
            g.setColor(Color.BLACK);
            g.fillRect(x, y, THUMB_WIDTH, THUMB_HEIGHT);
        }
        g.dispose();

        if (successCount == 0) return false;

        try {
            return ImageIO.write(sprite, "jpg", spriteFile.toFile());
        } catch (IOException e) {
            log.error("Failed to write sprite image: {}", spriteFile, e);
            return false;
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
        cleanupTempDir(vttDir.resolve("temp"));
        try {
            Files.deleteIfExists(vttDir.resolve("sprite.jpg"));
            Files.deleteIfExists(vttDir.resolve("thumbnails.vtt"));
            Files.deleteIfExists(vttDir);
        } catch (IOException ignored) {
        }
    }

    private void cleanupTempDir(Path tempDir) {
        if (!Files.exists(tempDir)) return;
        try (Stream<Path> files = Files.list(tempDir)) {
            files.forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                }
            });
            Files.deleteIfExists(tempDir);
        } catch (IOException ignored) {
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
