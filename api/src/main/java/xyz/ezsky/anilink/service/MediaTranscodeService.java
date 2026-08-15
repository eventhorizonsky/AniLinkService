package xyz.ezsky.anilink.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.MediaFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Web 播放器服务端转码/秒转封装服务
 *
 * <p>解决浏览器原生播放器不支持的媒体编码问题（如 MKV+HEVC 10bit、FLAC/AC3/DTS 音频）：
 * <ul>
 *   <li>REMUX（秒转封装）：编码本身浏览器可解、仅容器不支持时，用 {@code ffmpeg -c copy}
 *       直接封装为 HLS（mpegts），几乎零 CPU、零质量损失；</li>
 *   <li>TRANSCODE（转码）：编码不支持时，转码为 H.264/AAC 的 HLS 流。</li>
 * </ul>
 * 两种模式都产出 HLS 分片（m3u8 + ts），天然支持拖动 seek，前端用 hls.js 播放。
 * </p>
 *
 * <p>会话以 媒体ID+模式 为粒度复用，ffmpeg 后台持续产出分片，播放结束空闲一段时间后
 * 由定时任务清理进程与分片目录。</p>
 */
@Log4j2
@Service
public class MediaTranscodeService {

    /**
     * 播放模式：
     * DIRECT 直出原始文件；REMUX 视频+音频都原样封装（-c copy）；
     * MIXED 仅音频转码 AAC、视频原样封装（-c:v copy，最省资源）；
     * TRANSCODE 视频+音频全部转码（H.264/AAC）。
     */
    public enum PlayMode { DIRECT, REMUX, MIXED, TRANSCODE }

    /** mpegts 容器可原样封装的视频编码 */
    private static final Set<String> REMUXABLE_VIDEO_CODECS = Set.of("h264", "hevc", "mpeg2video", "mpeg4");
    /** mpegts 容器可原样封装的音频编码（FLAC/Opus/Vorbis 无法进 TS，需转码） */
    private static final Set<String> REMUXABLE_AUDIO_CODECS = Set.of("aac", "mp3", "mp2", "ac3", "eac3", "dts", "dts_hd", "truehd");
    /** 浏览器可直接播放的 MP4 家族容器（ffprobe 对 mp4/mov 常返回 "mov,mp4,m4a,3gp,3g2,mj2"） */
    private static final Set<String> MP4_FAMILY_CONTAINERS = Set.of("mp4", "m4v", "m4a", "mov", "3gp", "3g2", "mj2");
    /** 浏览器可直接播放的独立 webm 容器（注意 ffprobe 对 mkv 也会返回 "matroska,webm"，需按扩展名区分） */
    private static final String WEBM_CONTAINER = "webm";
    @Value("${media.transcode.output-dir:${media.data.root-dir}/transcode}")
    private String transcodeOutputDir;

    @Value("${media.transcode.idle-timeout-minutes:30}")
    private long idleTimeoutMinutes;

    @Value("${media.transcode.segment-seconds:6}")
    private int segmentSeconds;

    @Value("${media.transcode.startup-wait-seconds:120}")
    private long startupWaitSeconds;

    @Value("${media.transcode.segment-wait-seconds:60}")
    private long segmentWaitSeconds;

    @Value("${media.transcode.max-concurrent:1}")
    private int maxConcurrent;

    @Autowired
    private SiteConfigService siteConfigService;

    @Autowired
    private MediaFileService mediaFileService;

    private final Map<String, TranscodeSession> sessions = new ConcurrentHashMap<>();
    private volatile Boolean ffmpegAvailable;
    /** ffmpeg 探测失败时间戳，用于失败后间隔重试，避免一次冷启动超时永久禁用转码 */
    private volatile long ffmpegProbeFailedAt = Long.MIN_VALUE;

    @Value("${media.transcode.probe-timeout-seconds:30}")
    private long probeTimeoutSeconds;

    /**
     * 启动时后台预热 ffmpeg 探测，避免首个播放请求被慢速冷启动阻塞。
     */
    @PostConstruct
    public void warmupFfmpegProbe() {
        Thread warmup = new Thread(() -> {
            try {
                isFfmpegAvailable();
            } catch (Exception e) {
                log.warn("ffmpeg 预热探测异常：{}", e.getMessage());
            }
        }, "transcode-ffmpeg-probe-warmup");
        warmup.setDaemon(true);
        warmup.start();
    }

    /**
     * 转码/秒转会话
     */
    private static final class TranscodeSession {
        final PlayMode mode;
        final Path dir;
        final Process process;
        Thread drainThread;
        volatile long lastAccess = System.currentTimeMillis();
        volatile boolean failed;
        volatile Integer exitCode;

        TranscodeSession(PlayMode mode, Path dir, Process process, Thread drainThread) {
            this.mode = mode;
            this.dir = dir;
            this.process = process;
            this.drainThread = drainThread;
        }
    }

    /**
     * 根据媒体文件元数据给出后端推荐播放模式（前端最终基于浏览器能力决定）。
     */
    public PlayMode recommendMode(MediaFile mediaFile) {
        if (mediaFile == null) {
            return PlayMode.DIRECT;
        }
        if (!isTranscodeEnabled() || !isFfmpegAvailable()) {
            return PlayMode.DIRECT;
        }
        String container = normalize(mediaFile.getContainerFormat());
        if (container == null || container.isEmpty() || isDirectContainer(mediaFile, container)) {
            // 容器浏览器普遍可直接解析；编码是否支持由前端 canPlayType 决定
            return PlayMode.DIRECT;
        }
        if (isRemuxable(mediaFile)) {
            return PlayMode.REMUX;
        }
        if (canCopyVideoToTs(mediaFile)) {
            // 视频可原样封装、仅音频不支持 → 视频直通 + 音频转码
            return PlayMode.MIXED;
        }
        return PlayMode.TRANSCODE;
    }

    /**
     * 判断容器是否属于浏览器原生可解析的容器。
     * ffprobe 的 format_name 常为逗号分隔列表：
     * - mp4/mov 家族 → "mov,mp4,m4a,3gp,3g2,mj2"，命中即浏览器可解析；
     * - mkv 与 webm 都返回 "matroska,webm"，需结合扩展名区分（.mkv 走转码/秒转）。
     */
    private boolean isDirectContainer(MediaFile mediaFile, String container) {
        String fileName = mediaFile.getFileName() == null ? "" : mediaFile.getFileName().toLowerCase(Locale.ROOT);
        boolean hasMatroska = false;
        for (String token : container.split(",")) {
            String t = token.trim();
            if (MP4_FAMILY_CONTAINERS.contains(t)) {
                return true;
            }
            if (WEBM_CONTAINER.equals(t)) {
                return !fileName.endsWith(".mkv");
            }
            if ("matroska".equals(t)) {
                hasMatroska = true;
            }
        }
        return !hasMatroska;
    }

    /**
     * 视频编码能否原样封装进 mpegts（TS 容器可承载的编码：h264/hevc/mpeg2/mpeg4）。
     * 满足时即可用「视频直通 + 音频转码」的混合模式，避免无谓的视频重编码。
     */
    public boolean canCopyVideoToTs(MediaFile mediaFile) {
        if (mediaFile == null) {
            return false;
        }
        String videoCodec = normalize(mediaFile.getVideoCodec());
        return REMUXABLE_VIDEO_CODECS.contains(videoCodec);
    }

    /**
     * 视频与音频编码都可由 ffmpeg 秒转封装为 mpegts（-c copy）。
     * 音频需已知且能进 TS（FLAC/Opus/Vorbis/PCM 需走混合转码），避免 -c copy 失败。
     */
    public boolean isRemuxable(MediaFile mediaFile) {
        if (mediaFile == null || !canCopyVideoToTs(mediaFile)) {
            return false;
        }
        String audioCodec = normalize(mediaFile.getAudioCodec());
        return audioCodec != null && !audioCodec.isEmpty() && REMUXABLE_AUDIO_CODECS.contains(audioCodec);
    }

    /**
     * 是否可作为「视频直通 + 音频转码」候选：视频能进 TS 即可。
     * 音频是否支持由浏览器决定，因此只要视频可 copy 就提供混合模式候选。
     */
    public boolean isMixedCandidate(MediaFile mediaFile) {
        return canCopyVideoToTs(mediaFile);
    }

    public boolean isTranscodeEnabled() {
        return siteConfigService.isWebTranscodeEnabled();
    }

    /**
     * 探测 ffmpeg 是否可用（成功结果缓存）。
     * 失败不长期缓存：间隔一定时间后自动重试，避免冷启动慢速环境（如 NAS/QEMU）
     * 一次超时后永久禁用转码。
     */
    public boolean isFfmpegAvailable() {
        if (ffmpegAvailable != null) {
            return ffmpegAvailable;
        }
        if (ffmpegProbeFailedAt != Long.MIN_VALUE
                && System.currentTimeMillis() - ffmpegProbeFailedAt < FFMPEG_PROBE_RETRY_MS) {
            return false;
        }
        synchronized (this) {
            if (ffmpegAvailable != null) {
                return ffmpegAvailable;
            }
            boolean result = probeFfmpeg();
            if (result) {
                ffmpegAvailable = true;
                ffmpegProbeFailedAt = Long.MIN_VALUE;
            } else {
                ffmpegProbeFailedAt = System.currentTimeMillis();
            }
            return result;
        }
    }

    private static final long FFMPEG_PROBE_RETRY_MS = 10 * 60 * 1000L;

    private boolean probeFfmpeg() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(Math.max(5, probeTimeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ffmpeg 探测超时（>{}s），判定不可用", probeTimeoutSeconds);
                return false;
            }
            boolean available = process.exitValue() == 0;
            log.info("ffmpeg 探测结果：available={} exitCode={}", available, process.exitValue());
            return available;
        } catch (IOException e) {
            log.warn("ffmpeg 探测失败（进程启动异常）：{}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("ffmpeg 探测被中断");
            return false;
        }
    }

    /**
     * 获取（必要时启动）指定媒体与模式的 HLS 会话清单文件路径。
     * 首次调用会同步等待首个分片就绪；后续调用直接返回。
     *
     * @throws IllegalStateException ffmpeg 不可用、转码被禁用、并发超限或启动失败
     */
    public Path getManifest(Long mediaFileId, PlayMode mode) {
        if (mode == PlayMode.DIRECT) {
            throw new IllegalStateException("DIRECT 模式无需转码会话");
        }
        if (!isTranscodeEnabled()) {
            throw new IllegalStateException("服务端转码/秒转已禁用");
        }
        if (!isFfmpegAvailable()) {
            throw new IllegalStateException("服务端未检测到 ffmpeg，无法转码/秒转");
        }

        String key = mode.name() + "/" + mediaFileId;
        TranscodeSession session = acquireSession(key, mediaFileId, mode);
        session.lastAccess = System.currentTimeMillis();

        Path manifest = session.dir.resolve("index.m3u8");
        long waitTimeoutMs = Math.max(10_000, startupWaitSeconds * 1000);
        long deadline = System.currentTimeMillis() + waitTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (session.failed || (!session.process.isAlive() && !Files.exists(manifest))) {
                throw new IllegalStateException("ffmpeg 转码/秒转失败：" + mode.name().toLowerCase());
            }
            if (Files.exists(manifest)) {
                return manifest;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待转码清单被中断");
            }
        }
        throw new IllegalStateException("转码启动超时，未生成分片清单");
    }

    /**
     * 获取（必要时启动/重启）转码会话。
     * 已存在但进程异常退出的会话会被清理重建，避免复用陈旧清单导致分片永久缺失。
     */
    private TranscodeSession acquireSession(String key, Long mediaFileId, PlayMode mode) {
        synchronized (this) {
            TranscodeSession session = sessions.get(key);
            if (session != null && isSessionDead(session)) {
                log.warn("转码会话已异常退出，清理并重建：{}", key);
                sessions.remove(key);
                killSession(session);
                session = null;
            }
            if (session == null) {
                long runningCount = sessions.values().stream()
                        .filter(s -> s.process.isAlive())
                        .count();
                if (runningCount >= maxConcurrent) {
                    throw new IllegalStateException("转码任务并发已达上限(" + maxConcurrent + ")，请稍后再试");
                }
                session = startSession(mediaFileId, mode);
                if (session != null) {
                    sessions.put(key, session);
                }
            }
            if (session == null) {
                throw new IllegalStateException("转码会话启动失败");
            }
            return session;
        }
    }

    /**
     * 判断会话是否已异常死亡（进程退出且退出码非 0）。
     * 正常完成（退出码 0）的会话产物完整，仍可复用，无需重启。
     */
    private boolean isSessionDead(TranscodeSession session) {
        if (session.failed) {
            return true;
        }
        if (session.process.isAlive()) {
            return false;
        }
        if (session.exitCode != null) {
            return session.exitCode != 0;
        }
        try {
            return session.process.exitValue() != 0;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    /**
     * 获取供浏览器播放的 HLS 清单内容。
     *
     * <p>transcode 模式：视频关键帧被强制对齐到分片边界（-force_key_frames），分片时长可以精确预铺，
     * 因此按源时长合成一份带 ENDLIST 的完整 VOD 清单，浏览器一开始就能得到准确总时长，可正常拖动 seek。</p>
     *
     * <p>remux/mixed 模式：分片按源文件关键帧自然切分（-c copy），分片时长与数量无法提前预测，
     * 按固定分片时长估算会得到错误的总时长，且会引用不存在的分片（进度条总时长虚高、拖到末尾
     * 等待 404/提前结束）。因此直接透传 ffmpeg 实时清单（EVENT 类型，随分片产出增长），
     * EXTINF 与总时长始终为真实值。</p>
     */
    public String getManifestContent(Long mediaFileId, PlayMode mode) {
        Path manifest = getManifest(mediaFileId, mode);
        if (mode == PlayMode.TRANSCODE) {
            MediaFile mediaFile = mediaFileService.getMediaFileById(mediaFileId);
            if (mediaFile != null && mediaFile.getDuration() != null && mediaFile.getDuration() > 0) {
                return synthesizePlaylist(mediaFile, mode, manifest);
            }
        }
        return eventizePlaylist(manifest);
    }

    /**
     * 依据源时长合成完整 VOD 清单（含 ENDLIST）。
     * 仅用于 transcode 模式：已产出的分片沿用真实 EXTINF，未产出的按分片时长估算，
     * 最后一段按剩余时长补齐，保证清单总时长与源时长精确一致。
     */
    private String synthesizePlaylist(MediaFile mediaFile, PlayMode mode, Path manifest) {
        double durationSec = mediaFile.getDuration() / 1000.0;
        int segSec = Math.max(2, segmentSeconds);
        List<Double> produced = parseProducedDurations(manifest);
        int producedCount = produced.size();
        // 预估段数；若已产出分片超过预估（源流实际时长略长于元数据），以实际为准，避免遗漏末尾真实分片
        int total = (int) Math.max(1, Math.max(Math.ceil(durationSec / segSec), producedCount));

        double maxDur = segSec;
        for (double d : produced) {
            if (d > maxDur) {
                maxDur = d;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:6\n");
        sb.append("#EXT-X-TARGETDURATION:").append((long) Math.ceil(maxDur)).append("\n");
        sb.append("#EXT-X-MEDIA-SEQUENCE:0\n");
        sb.append("#EXT-X-PLAYLIST-TYPE:VOD\n");
        sb.append("#EXT-X-INDEPENDENT-SEGMENTS\n");

        String base = mode.name().toLowerCase(Locale.ROOT) + "/segments/";
        double accumulated = 0;
        for (int i = 0; i < total; i++) {
            double dur;
            if (i < producedCount) {
                dur = produced.get(i);
            } else if (i == total - 1) {
                // 最后一段按剩余时长补齐，使清单总时长与源时长精确一致
                dur = Math.max(0.1, durationSec - accumulated);
            } else {
                dur = segSec;
            }
            accumulated += dur;
            sb.append(String.format(Locale.ROOT, "#EXTINF:%.6f,\n", dur));
            sb.append(base).append(String.format(Locale.ROOT, "seg_%05d.ts", i)).append("\n");
        }
        sb.append("#EXT-X-ENDLIST\n");
        return sb.toString();
    }

    /**
     * 将 ffmpeg 实时清单转换为 EVENT 类型的增长清单透传：
     * 保留真实 EXTINF 与分片引用（-hls_base_url 已指向本模式的分片接口），
     * 补充 #EXT-X-PLAYLIST-TYPE:EVENT 使 hls.js 持续刷新清单、总时长随分片产出逐步精确到位；
     * ffmpeg 完成时写入的 ENDLIST 原样保留，hls.js 据此停止增长并正常触发播放结束。
     */
    private String eventizePlaylist(Path manifest) {
        List<String> lines;
        try {
            lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取转码清单失败");
        }
        StringBuilder sb = new StringBuilder();
        boolean typeSet = false;
        for (String line : lines) {
            if (!typeSet && line.startsWith("#EXTM3U")) {
                sb.append(line).append('\n');
                sb.append("#EXT-X-PLAYLIST-TYPE:EVENT\n");
                typeSet = true;
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * 解析 ffmpeg 当前清单中已产出分片的真实 EXTINF 时长（按顺序）。
     */
    private List<Double> parseProducedDurations(Path manifest) {
        List<Double> durations = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                if (line.startsWith("#EXTINF:")) {
                    try {
                        String val = line.substring(8).split(",")[0].trim();
                        durations.add(Double.parseDouble(val));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return durations;
    }

    /**
     * 解析会话目录内的分片文件（等待其就绪），防止路径穿越。
     * 转码进行中分片是逐步落盘的，拖动到未生成区域时需等待其产生。
     *
     * @return 分片绝对路径；等待超时或越界返回 null
     */
    public Path resolveSegment(Long mediaFileId, PlayMode mode, String segmentName) {
        long waitTimeoutMs = Math.max(5_000, segmentWaitSeconds * 1000);
        return resolveSegment(mediaFileId, mode, segmentName, waitTimeoutMs);
    }

    private Path resolveSegment(Long mediaFileId, PlayMode mode, String segmentName, long waitTimeoutMs) {
        Path sessionDir = sessionDir(mediaFileId, mode);
        Path segmentsDir = sessionDir.resolve("segments");
        Path resolved = segmentsDir.resolve(segmentName).normalize();
        if (!resolved.startsWith(segmentsDir.normalize())) {
            return null;
        }
        long deadline = System.currentTimeMillis() + waitTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (Files.isRegularFile(resolved)) {
                return resolved;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public Path sessionDir(Long mediaFileId, PlayMode mode) {
        return Paths.get(transcodeOutputDir, mode.name().toLowerCase(Locale.ROOT), String.valueOf(mediaFileId));
    }

    /**
     * 启动 ffmpeg 会话。进程后台运行，持续把分片写入会话目录。
     */
    private TranscodeSession startSession(Long mediaFileId, PlayMode mode) {
        try {
            Path dir = sessionDir(mediaFileId, mode);
            Files.createDirectories(dir);

            MediaFile mediaFile = mediaFileService.getMediaFileById(mediaFileId);
            if (mediaFile == null) {
                log.warn("启动转码失败：媒体文件不存在 id={}", mediaFileId);
                return null;
            }
            Path input = Paths.get(mediaFile.getFilePath());
            if (!Files.isRegularFile(input)) {
                log.warn("启动转码失败：源文件不存在 {}", mediaFile.getFilePath());
                return null;
            }

            Path manifest = dir.resolve("index.m3u8");
            Path segmentsDir = dir.resolve("segments");
            Files.createDirectories(segmentsDir);
            Path segmentPattern = segmentsDir.resolve("seg_%05d.ts");

            ProcessBuilder pb = new ProcessBuilder(buildFfmpegCommand(mode, input, segmentPattern, manifest));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            TranscodeSession session = new TranscodeSession(mode, dir, process, null);
            Thread drainThread = new Thread(() -> drainProcessOutput(process, mediaFileId, mode, session), "transcode-drain-" + mediaFileId);
            drainThread.setDaemon(true);
            session.drainThread = drainThread;
            drainThread.start();

            log.info("启动 ffmpeg 转码会话：mediaId={} mode={} dir={}", mediaFileId, mode, dir);
            return session;
        } catch (IOException e) {
            log.error("启动 ffmpeg 转码会话失败：mediaId={} mode={}", mediaFileId, mode, e);
            return null;
        }
    }

    private java.util.List<String> buildFfmpegCommand(PlayMode mode, Path input, Path segmentPattern, Path manifest) {
        String hlsTime = String.valueOf(Math.max(2, segmentSeconds));
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(input.toString());
        cmd.add("-map");
        cmd.add("0:v:0");
        cmd.add("-map");
        cmd.add("0:a:0?");

        if (mode == PlayMode.REMUX) {
            cmd.add("-c");
            cmd.add("copy");
        } else if (mode == PlayMode.MIXED) {
            // 混合模式：视频原样封装进 TS（-c:v copy），仅音频转码为 AAC，避免视频重编码
            cmd.add("-c:v");
            cmd.add("copy");
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("192k");
            cmd.add("-ac");
            cmd.add("2");
        } else {
            // 统一转码为 H.264 (yuv420p, 主档次) + AAC，最多 1080p 以降低 CPU 开销。
            // HDR 素材未做 tone-mapping，会以 10bit->8bit 转换呈现（v1 限制，后续可用 zscale 优化）。
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("veryfast");
            cmd.add("-crf");
            cmd.add("23");
            cmd.add("-maxrate");
            cmd.add("6M");
            cmd.add("-bufsize");
            cmd.add("12M");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
            cmd.add("-profile:v");
            cmd.add("main");
            cmd.add("-level");
            cmd.add("4.1");
            cmd.add("-vf");
            cmd.add("scale=min(1920\\,iw):-2");
            // 强制每 segmentSeconds 一个关键帧，保证分片边界与"源时长/分片时长"预估精确对齐
            cmd.add("-force_key_frames");
            cmd.add("expr:gte(t,n_forced*" + Math.max(2, segmentSeconds) + ")");
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("192k");
            cmd.add("-ac");
            cmd.add("2");
            cmd.add("-threads");
            cmd.add("0");
        }

        cmd.add("-f");
        cmd.add("hls");
        cmd.add("-hls_time");
        cmd.add(hlsTime);
        cmd.add("-hls_list_size");
        cmd.add("0");
        cmd.add("-hls_flags");
        cmd.add("independent_segments");
        // 让 m3u8 中的分片引用指向 mode 专属子路径，浏览器据此请求到对应的分片接口
        cmd.add("-hls_base_url");
        cmd.add(mode.name().toLowerCase(Locale.ROOT) + "/segments/");
        cmd.add("-hls_segment_filename");
        cmd.add(segmentPattern.toString());
        cmd.add(manifest.toString());
        return cmd;
    }

    private void drainProcessOutput(Process process, Long mediaFileId, PlayMode mode, TranscodeSession session) {
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = process.getInputStream().read(buffer)) != -1) {
                if (read > 0) {
                    String line = new String(buffer, 0, read).trim();
                    if (!line.isEmpty()) {
                        log.warn("[transcode:{}/{}] {}", mediaFileId, mode.name().toLowerCase(), line);
                    }
                }
            }
        } catch (IOException ignored) {
        } finally {
            try {
                int exitCode = process.waitFor();
                session.exitCode = exitCode;
                if (exitCode != 0) {
                    session.failed = true;
                    log.warn("转码进程退出：mediaId={} mode={} exitCode={}", mediaFileId, mode.name().toLowerCase(), exitCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                session.failed = true;
            }
        }
    }

    /**
     * 定时清理：空闲超时的会话（无论是否完成）销毁进程并删除分片目录。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void cleanupIdleSessions() {
        long idleMs = Math.max(60_000, idleTimeoutMinutes * 60_000);
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            TranscodeSession session = entry.getValue();
            boolean idle = now - session.lastAccess > idleMs;
            if (!idle) {
                return false;
            }
            killSession(session);
            log.info("清理空闲转码会话：{}（dir={}）", entry.getKey(), session.dir);
            return true;
        });
    }

    private void killSession(TranscodeSession session) {
        try {
            if (session.process.isAlive()) {
                session.process.destroy();
                if (!session.process.waitFor(3, TimeUnit.SECONDS)) {
                    session.process.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            session.process.destroyForcibly();
        }
        session.failed = true;
        deleteRecursively(session.dir);
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            log.warn("删除转码会话目录失败：{}", dir);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
