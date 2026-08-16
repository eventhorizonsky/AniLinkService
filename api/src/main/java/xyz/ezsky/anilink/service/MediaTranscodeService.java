package xyz.ezsky.anilink.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.MediaFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Web 播放器服务端转码/秒转封装服务
 *
 * <p>解决浏览器原生播放器不支持的媒体编码问题（如 MKV+HEVC 10bit、FLAC/AC3/DTS 音频）：
 * <ul>
 *   <li>REMUX（秒转封装）：编码本身浏览器可解、仅容器不支持时，用 {@code ffmpeg -c copy}
 *       直接封装为 HLS（mpegts），几乎零 CPU、零质量损失；</li>
 *   <li>MIXED：视频原样封装、仅音频转 AAC（-c:v copy，最省资源）；</li>
 *   <li>TRANSCODE（转码）：编码不支持时，转码为 H.264/AAC 的 HLS 流。</li>
 * </ul>
 *
 * <p>「即跳即转」能力：
 * <ul>
 *   <li>transcode 返回带 ENDLIST 的完整 VOD 清单（进度条一开始就是完整时长），按源时长 +
 *       强制关键帧预铺分片；remux/mixed 用 ffprobe 关键帧边界探测精确分片（{@code -skip_frame
 *       nokey} 快速探测），边界就绪后同样返回完整 VOD 清单，就绪前透传 EVENT 清单随产出增长；</li>
 *   <li>拖动到未产出区域时（缺失分片距当前产出前沿超过近前沿余量，或落入向前重锚留下的
 *       未产出空洞），杀掉当前 ffmpeg 重新起转：transcode 用 {@code -ss} 输入流粗定位 +
 *       {@code -ss} 输出精定位（帧精确，消除 A/V 错位）+ {@code -start_number} 对齐分片序号，
 *       从目标位置精确起转，无需从开头线性追赶；</li>
 *   <li>首次清单请求可携带 {@code t} 参数（续播/URL 跳转），ffmpeg 直接起转于目标位置；</li>
 *   <li>转码编码器自动选择硬件加速（QSV/NVENC/VAAPI/AMF），回退 libx264，弱机也能
 *       远超实时速度产出分片。</li>
 * </ul>
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

    @Value("${media.transcode.threads:4}")
    private int ffmpegThreads;

    @Value("${media.transcode.max-width:1920}")
    private int maxTranscodeWidth;

    @Value("${media.transcode.encoder:auto}")
    private String configuredEncoder;

    /**
     * 距当前产出前沿多少个分片内判定为"近前沿"（等待当前 ffmpeg 按序产出即可，不重锚）。
     * 超过该数量的前向缺失分片一律重锚从目标位置起转（即跳即转），
     * 而非从开头线性追赶，避免慢编码时长时间空等卡死。
     */
    @Value("${media.transcode.reanchor-near-segments:3}")
    private int reanchorNearSegments;

    @Value("${media.transcode.vaapi-device:/dev/dri/renderD128}")
    private String vaapiDevice;

    @Autowired
    private SiteConfigService siteConfigService;

    @Autowired
    private MediaFileService mediaFileService;

    private final Map<String, TranscodeSession> sessions = new ConcurrentHashMap<>();

    /**
     * 单飞串行化：key → 启动锁。映射函数只创建空锁对象（瞬时完成），真正的会话启动放在
     * {@code synchronized(lock)} 内并双重检查。不得用 CHM 的 compute/put 系列承载慢启动逻辑：
     * 映射执行期间该 key 的 bin 处于 ReservationNode，Java 17+ 上并发请求再访问同 key
     * 会直接抛 IllegalStateException("Recursive update")（而非等待），导致清单请求 503。
     */
    private final Map<String, Object> startLocks = new ConcurrentHashMap<>();
    /** 并发上限的原子计数：已启动但尚未确认运行中的会话数（用于锁外启动时精确统计） */
    private final AtomicInteger inFlightStarts = new AtomicInteger();

    private volatile Boolean ffmpegAvailable;
    /** ffmpeg 探测失败时间戳，用于失败后间隔重试，避免一次冷启动超时永久禁用转码 */
    private volatile long ffmpegProbeFailedAt = Long.MIN_VALUE;

    @Value("${media.transcode.probe-timeout-seconds:30}")
    private long probeTimeoutSeconds;

    // ==================== 编码器自动选择 ====================

    private enum EncoderKind { LIBX264, QSV, NVENC, VAAPI, AMF, VIDEOTOOLBOX }

    private static final class EncoderProfile {
        final EncoderKind kind;
        final String codec;
        final String label;

        EncoderProfile(EncoderKind kind, String codec, String label) {
            this.kind = kind;
            this.codec = codec;
            this.label = label;
        }
    }

    private static final EncoderProfile LIBX264_PROFILE = new EncoderProfile(EncoderKind.LIBX264, "libx264", "libx264");
    private static final String[] ENCODER_PRIORITY = {"h264_qsv", "h264_nvenc", "h264_vaapi", "h264_amf", "h264_videotoolbox"};
    private static final Map<String, EncoderProfile> ENCODER_PROFILES = Map.of(
            "h264_qsv", new EncoderProfile(EncoderKind.QSV, "h264_qsv", "Intel QSV"),
            "h264_nvenc", new EncoderProfile(EncoderKind.NVENC, "h264_nvenc", "NVIDIA NVENC"),
            "h264_vaapi", new EncoderProfile(EncoderKind.VAAPI, "h264_vaapi", "VAAPI"),
            "h264_amf", new EncoderProfile(EncoderKind.AMF, "h264_amf", "AMD AMF"),
            "h264_videotoolbox", new EncoderProfile(EncoderKind.VIDEOTOOLBOX, "h264_videotoolbox", "VideoToolbox")
    );

    private volatile EncoderProfile activeEncoderProfile;
    private volatile Set<String> availableEncoders;

    private EncoderProfile activeEncoder() {
        EncoderProfile p = activeEncoderProfile;
        if (p != null) {
            return p;
        }
        synchronized (this) {
            p = activeEncoderProfile;
            if (p != null) {
                return p;
            }
            p = resolveEncoder();
            activeEncoderProfile = p;
            log.info("转码编码器：{}（{}）", p.label, p.codec);
            return p;
        }
    }

    private EncoderProfile resolveEncoder() {
        Set<String> avail = probeEncoders();
        String override = configuredEncoder == null ? "" : configuredEncoder.trim().toLowerCase(Locale.ROOT);
        if (!override.isEmpty() && !"auto".equals(override)) {
            if ("libx264".equals(override)) {
                return LIBX264_PROFILE;
            }
            EncoderProfile prof = ENCODER_PROFILES.get(override);
            if (prof != null && avail.contains(override)) {
                return prof;
            }
            if (prof != null) {
                log.warn("指定的编码器 {} 不可用（可用：{}），回退 libx264", override, avail);
            }
        }
        for (String name : ENCODER_PRIORITY) {
            EncoderProfile prof = ENCODER_PROFILES.get(name);
            if (prof != null && avail.contains(name)) {
                return prof;
            }
        }
        return LIBX264_PROFILE;
    }

    private Set<String> probeEncoders() {
        if (availableEncoders != null) {
            return availableEncoders;
        }
        Set<String> names = new HashSet<>();
        try {
            Process p = new ProcessBuilder("ffmpeg", "-hide_banner", "-encoders")
                    .redirectErrorStream(true)
                    .start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            if (p.waitFor(Math.max(5, probeTimeoutSeconds), TimeUnit.SECONDS)) {
                for (String line : sb.toString().split("\n")) {
                    String[] toks = line.trim().split("\\s+");
                    if (toks.length >= 3 && toks[1].startsWith("h264_")) {
                        names.add(toks[1]);
                    }
                }
            } else {
                p.destroyForcibly();
            }
        } catch (Exception e) {
            log.warn("ffmpeg 编码器探测失败：{}", e.getMessage());
        }
        availableEncoders = names;
        log.info("ffmpeg 可用 H.264 编码器：{}", names.isEmpty() ? "（无，回退 libx264）" : names);
        return names;
    }

    /**
     * 启动时后台预热 ffmpeg 可用性与编码器探测，避免首个播放请求被慢速冷启动阻塞。
     */
    @PostConstruct
    public void warmupFfmpegProbe() {
        Thread warmup = new Thread(() -> {
            try {
                isFfmpegAvailable();
                activeEncoder();
            } catch (Exception e) {
                log.warn("ffmpeg 预热探测异常：{}", e.getMessage());
            }
        }, "transcode-ffmpeg-probe-warmup");
        warmup.setDaemon(true);
        warmup.start();
    }

    /**
     * 转码/秒转会话。进程可因跳转重锚被替换（process/generation/anchorIdx 随之更新）。
     */
    private static final class TranscodeSession {
        final PlayMode mode;
        final Path dir;
        final Path input;
        final Path segmentPattern;
        /** 源媒体时长（秒），会话启动时从元数据读取；供清单合成使用，避免每次请求查库 */
        final double durationSec;
        volatile Process process;
        volatile Thread drainThread;
        volatile long lastAccess = System.currentTimeMillis();
        volatile boolean failed;
        volatile Integer exitCode;
        /** 每次重启 ffmpeg 自增，用于让旧进程的 drain 线程不再覆盖新进程的状态 */
        volatile int generation;
        /** 当前 ffmpeg 进程对应输出分片的起始序号（初始 0，跳转重锚后为跳转目标） */
        volatile int anchorIdx;
        /** remux/mixed 关键帧边界（秒，各分片起始时间）；null=尚未探测/不可用 */
        volatile List<Double> boundaries;

        TranscodeSession(PlayMode mode, Path dir, Path input, Path segmentPattern, double durationSec) {
            this.mode = mode;
            this.dir = dir;
            this.input = input;
            this.segmentPattern = segmentPattern;
            this.durationSec = durationSec;
        }
    }

    /** 关键帧边界探测结果缓存（mediaFileId → 分片起始时间序列），上限后整体清空 */
    private final Map<Long, List<Double>> boundaryCache = new ConcurrentHashMap<>();

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
     * 获取（必要时启动）指定媒体与模式的 HLS 会话清单文件路径（从开头起转）。
     * 首次调用会同步等待首个分片就绪；后续调用直接返回。
     *
     * @throws IllegalStateException ffmpeg 不可用、转码被禁用、并发超限或启动失败
     */
    public Path getManifest(Long mediaFileId, PlayMode mode) {
        return getManifest(mediaFileId, mode, 0);
    }

    /**
     * 获取（必要时启动）指定媒体与模式的 HLS 会话清单文件路径。
     * 新会话若指定 {@code initialSeekSec > 0}，ffmpeg 直接起转于该时间位置
     * （续播/URL 跳转场景免去"从 0 起转再重锚"的一轮等待）。
     *
     * @throws IllegalStateException ffmpeg 不可用、转码被禁用、并发超限或启动失败
     */
    public Path getManifest(Long mediaFileId, PlayMode mode, double initialSeekSec) {
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
        TranscodeSession session = acquireSession(key, mediaFileId, mode, initialSeekSec);
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
        // 启动超时：立即清理卡死的孤儿会话（含进程与分片目录），
        // 否则 ffmpeg 会持续空转吃满 CPU/内存，直到空闲回收才被清理，
        // 造成日志中的 HikariPool 线程饥饿与 503 之后服务不可用。
        log.warn("转码启动超时，清理孤儿会话：{}", key);
        killSession(session);
        sessions.remove(key, session);
        throw new IllegalStateException("转码启动超时，未生成分片清单");
    }

    /**
     * 获取（必要时启动/重启）转码会话。
     * 死会话的检测与清理在按 key 的启动锁内执行，保证同一 key 同一时刻至多一个线程
     * 在清理旧会话/启动新会话，避免并发请求误删正在写入的新会话目录导致分片永久缺失。
     */
    private TranscodeSession acquireSession(String key, Long mediaFileId, PlayMode mode) {
        return acquireSession(key, mediaFileId, mode, 0);
    }

    private TranscodeSession acquireSession(String key, Long mediaFileId, PlayMode mode, double initialSeekSec) {
        // 快速路径：现有可用会话直接复用（无锁），播放热路径不再触碰全局锁与数据库
        TranscodeSession session = sessions.get(key);
        if (session != null && !isSessionDead(session)) {
            return session;
        }
        // 单飞（single-flight）：同一 key 的并发请求只允许一个线程真正执行清理+启动逻辑。
        // 锁对象由 CHM.computeIfAbsent 创建（映射函数仅 new Object()，瞬时完成，不会触发
        // CHM 的 ReservationNode/"Recursive update"）；真正的会话启动在 synchronized(lock)
        // 内进行，其余请求阻塞等待并在拿到锁后双重检查复用结果。
        Object lock = startLocks.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (lock) {
                TranscodeSession existing = sessions.get(key);
                if (existing != null && !isSessionDead(existing)) {
                    // 等待期间已有其他线程完成重建，直接复用，不再重复启动
                    return existing;
                }
                // 并发上限校验 + 死会话清理 + 启动占位计数，仅短暂持全局锁；进程启动不阻塞其他 key 的会话
                boolean reserved = false;
                synchronized (this) {
                    if (existing != null) {
                        log.warn("转码会话已异常退出，清理并重建：{}", key);
                        sessions.remove(key, existing);
                        killSession(existing);
                    }
                    long runningCount = sessions.values().stream()
                            .filter(s -> s.process.isAlive())
                            .count() + inFlightStarts.get();
                    if (runningCount >= maxConcurrent) {
                        throw new IllegalStateException("转码任务并发已达上限(" + maxConcurrent + ")，请稍后再试");
                    }
                    inFlightStarts.incrementAndGet();
                    reserved = true;
                }
                try {
                    TranscodeSession created = startSession(mediaFileId, mode, initialSeekSec);
                    if (created == null) {
                        throw new IllegalStateException("转码会话启动失败");
                    }
                    TranscodeSession raced = sessions.putIfAbsent(key, created);
                    if (raced != null) {
                        // 极端竞态兜底：已有会话，仅销毁新进程，不删共享目录（同一目录可能已被复用）
                        killProcess(created.process);
                        return raced;
                    }
                    return created;
                } finally {
                    if (reserved) {
                        inFlightStarts.decrementAndGet();
                    }
                }
            }
        } finally {
            startLocks.remove(key, lock);
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
     * <p>所有模式尽量返回带 ENDLIST 的完整 VOD 清单，进度条一开始就是完整时长：
     * <ul>
     *   <li>transcode：分片按源时长 + 强制关键帧预铺（精确对齐）；</li>
     *   <li>remux/mixed：用 ffprobe 关键帧边界探测得到真实分片边界；探测尚未完成时
     *       降级透传 ffmpeg 实时 EVENT 清单，探测完成后下次请求即切回完整 VOD。</li>
     * </ul></p>
     */
    public String getManifestContent(Long mediaFileId, PlayMode mode) {
        return getManifestContent(mediaFileId, mode, 0);
    }

    /**
     * 获取供浏览器播放的 HLS 清单内容。
     *
     * <p>所有模式尽量返回带 ENDLIST 的完整 VOD 清单，进度条一开始就是完整时长：
     * <ul>
     *      <li>transcode：分片按源时长 + 强制关键帧预铺（精确对齐），合成完整 VOD；</li>
     *      <li>remux/mixed：用 ffprobe 关键帧边界探测得到真实分片边界后合成完整 VOD；
     *          探测尚未完成时透传 ffmpeg 实时 EVENT 清单（仅列已产出分片），探测完成后
     *          下次请求即切回精确清单。等分预估与实际关键帧切分无关，会引用永不产出的
     *          尾部 seg 序号导致拖拽 loading，故边界就绪前不再按源时长铺满。</li>
     * </ul></p>
     *
     * @param initialSeekSec 新会话的初始定位秒数（续播/URL 跳转），仅首个请求生效
     */
    public String getManifestContent(Long mediaFileId, PlayMode mode, double initialSeekSec) {
        Path manifest = getManifest(mediaFileId, mode, initialSeekSec);
        String key = mode.name() + "/" + mediaFileId;
        TranscodeSession session = sessions.get(key);
        double durationSec = session == null ? 0 : session.durationSec;

        if (durationSec <= 0) {
            // 源时长未知（元数据缺失且兜底探测失败，极少数场景）：退化为 EVENT 清单，总时长随分片产出增长
            return eventizePlaylist(manifest);
        }

        int anchorIdx = session == null ? 0 : session.anchorIdx;
        if (mode == PlayMode.TRANSCODE) {
            return synthesizeTranscodePlaylist(durationSec, manifest, anchorIdx);
        }

        // remux/mixed：关键帧边界已就绪时按真实边界精确合成完整 VOD 清单；
        // 尚未就绪时透传 ffmpeg 实时 EVENT 清单（仅列已产出分片，随产出增长），
        // 不按源时长等分铺满——等分预估与实际关键帧切分无关，会引用永不产出的
        // 尾部 seg 序号导致拖到后半段持续 loading，且弱机/大文件上边界探测常需数秒。
        // 配合 -skip_frame nokey 快速探测，边界就绪后下次请求即切回精确完整 VOD。
        List<Double> boundaries = session == null ? null : session.boundaries;
        if (boundaries != null && !boundaries.isEmpty()) {
            return synthesizeCopyPlaylist(durationSec, mode, boundaries);
        }
        return eventizePlaylist(manifest);
    }

    /**
     * 依据源时长合成完整 VOD 清单（含 ENDLIST），用于 transcode 模式。
     * 已产出分片沿用真实 EXTINF，未产出按分片时长估算，最后一段按剩余时长补齐，
     * 保证清单总时长与源时长精确一致。anchorIdx 之后的分片由当前（可能已重锚的）
     * ffmpeg 进程产出，其 EXTINF 从 ffmpeg 实时清单按偏移映射。
     */
    private String synthesizeTranscodePlaylist(double durationSec, Path manifest, int anchorIdx) {
        return synthesizeEstimatePlaylist(durationSec, PlayMode.TRANSCODE, manifest, anchorIdx);
    }

    /**
     * 依据源时长合成完整 VOD 清单（含 ENDLIST），用于 transcode 模式（已产出分片沿真实
     * EXTINF，未产出按分片时长等分预估，最后一段按剩余时长补齐，保证清单总时长与源时长
     * 精确一致）。anchorIdx 之后的分片由当前（可能已重锚的）ffmpeg 进程产出，其 EXTINF
     * 从 ffmpeg 实时清单按偏移映射。
     *
     * <p>仅适用于 transcode：强制关键帧按 segSec 预铺，分片边界与等分预估严格对齐；
     * remux/mixed 的 -c copy 切分取决于源关键帧位置，等分预估会与实际分片数不一致，
     * 故边界就绪前改用 EVENT 清单（见 getManifestContent）。</p>
     */
    private String synthesizeEstimatePlaylist(double durationSec, PlayMode mode, Path manifest, int anchorIdx) {
        int segSec = Math.max(2, segmentSeconds);
        List<Double> produced = parseProducedDurations(manifest);
        int producedStart = anchorIdx;
        int producedEnd = anchorIdx + produced.size();
        // 预估段数；若已产出分片超过预估（源流实际时长略长于元数据），以实际为准，避免遗漏末尾真实分片
        int total = (int) Math.max(1, Math.max(Math.ceil(durationSec / segSec), producedEnd));

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

        String base = modeNamePath(mode) + "/segments/";
        double accumulated = 0;
        for (int i = 0; i < total; i++) {
            double dur;
            if (i >= producedStart && i < producedEnd) {
                dur = produced.get(i - producedStart);
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
     * 依据关键帧边界序列合成完整 VOD 清单（含 ENDLIST），用于 remux/mixed 模式。
     * boundaries 为各分片的起始时间（秒），分片时长 = 下一边界 - 本边界，最后一段到源时长。
     */
    private String synthesizeCopyPlaylist(double durationSec, PlayMode mode, List<Double> boundaries) {
        int n = boundaries.size();
        double maxDur = Math.max(2, segmentSeconds);
        for (int i = 0; i < n; i++) {
            double end = (i < n - 1) ? boundaries.get(i + 1) : durationSec;
            maxDur = Math.max(maxDur, Math.max(0.1, end - boundaries.get(i)));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:6\n");
        sb.append("#EXT-X-TARGETDURATION:").append((long) Math.ceil(maxDur)).append("\n");
        sb.append("#EXT-X-MEDIA-SEQUENCE:0\n");
        sb.append("#EXT-X-PLAYLIST-TYPE:VOD\n");
        sb.append("#EXT-X-INDEPENDENT-SEGMENTS\n");

        String base = modeNamePath(mode) + "/segments/";
        for (int i = 0; i < n; i++) {
            double end = (i < n - 1) ? boundaries.get(i + 1) : durationSec;
            double dur = Math.max(0.1, end - boundaries.get(i));
            sb.append(String.format(Locale.ROOT, "#EXTINF:%.6f,\n", dur));
            sb.append(base).append(String.format(Locale.ROOT, "seg_%05d.ts", i)).append("\n");
        }
        sb.append("#EXT-X-ENDLIST\n");
        return sb.toString();
    }

    /**
     * 将 ffmpeg 实时清单转换为 EVENT 类型的增长清单透传：
     * 保留真实 EXTINF 与分片引用，补充 #EXT-X-PLAYLIST-TYPE:EVENT 使 hls.js 持续刷新清单、
     * 总时长随分片产出逐步精确到位；ffmpeg 完成时写入的 ENDLIST 原样保留。
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
     * 若请求的分片与当前产出前沿差距过大（判定为用户拖动跳转），
     * 触发 ffmpeg 从目标位置重锚（即跳即转），而不是从开头线性追赶。
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
        String key = mode.name() + "/" + mediaFileId;
        int requestedIdx = parseSegmentIndex(segmentName);
        long deadline = System.currentTimeMillis() + waitTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (Files.isRegularFile(resolved)) {
                TranscodeSession s = sessions.get(key);
                if (s != null) {
                    s.lastAccess = System.currentTimeMillis();
                }
                return resolved;
            }
            maybeReanchor(mediaFileId, mode, key, segmentsDir, requestedIdx);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /**
     * 判断缺失分片是否构成跳转并重锚 ffmpeg：
     * <ul>
     *   <li>向后跳入"从未产出的空洞"（前向重锚杀掉旧进程留下的未产出区间）→ 反向重锚；</li>
     *   <li>向前缺失分片距当前产出前沿超过近前沿余量 → 重锚从目标位置起转（即跳即转），
     *       而不是线性追赶，避免慢编码时长时间空等；</li>
     *   <li>近前沿（当前 ffmpeg 即将按序产出）→ 不重锚，等待即可。</li>
     * </ul>
     */
    private void maybeReanchor(Long mediaFileId, PlayMode mode, String key, Path segmentsDir, int requestedIdx) {
        if (requestedIdx < 0) {
            return;
        }
        TranscodeSession session = sessions.get(key);
        if (session == null) {
            return;
        }
        // remux/mixed 在关键帧边界未就绪（EVENT 降级）时不重锚：重锚会破坏 EVENT 清单的
        // 分片序号连续性；此时 -c copy 从开头产出本身很快，等待即可。
        if (session.mode != PlayMode.TRANSCODE
                && (session.boundaries == null || session.boundaries.isEmpty())) {
            return;
        }
        int anchor = session.anchorIdx;
        // ffmpeg 进程已退出（崩溃/被杀）：任何缺失分片都原地重启恢复，否则等待将空转到超时
        if (!session.process.isAlive()) {
            reanchorSession(session, mediaFileId, requestedIdx);
            return;
        }
        // 向后跳到当前锚点之前且分片缺失 → 落在前向重锚留下的空洞，必须反向重锚
        if (requestedIdx < anchor) {
            reanchorSession(session, mediaFileId, requestedIdx);
            return;
        }
        // 向前：当前 ffmpeg 从 anchor 起按序产出。请求超过产出前沿 + 近前沿余量才重锚。
        int frontier = maxProducedSegment(segmentsDir);
        int effective = Math.max(frontier, anchor);
        if (requestedIdx <= effective + reanchorNearSegments()) {
            return;
        }
        reanchorSession(session, mediaFileId, requestedIdx);
    }

    private int reanchorNearSegments() {
        return Math.max(1, Math.min(12, reanchorNearSegments));
    }

    /**
     * 重锚：杀掉当前 ffmpeg，从目标时间/分片序号重新起转，实现拖动即播。
     * 目标与当前锚点相距不超过近前沿余量的前向重锚会被忽略（当前进程按序产出更快，
     * 避免 hls.js 缓冲突发请求触发重锚风暴反复杀进程）。
     * 会话异常退出后同样可通过本方法原地恢复（由缺失分片请求触发）。
     */
    private void reanchorSession(TranscodeSession session, Long mediaFileId, int anchorIdx) {
        synchronized (this) {
            boolean processAlive = session.process.isAlive();
            // 进程存活且目标与当前锚一致 → 已在该位置产出，忽略重复请求
            if (processAlive && session.anchorIdx == anchorIdx) {
                return;
            }
            // 进程存活时前向重锚且距离过近 → 等当前进程按序产出即可
            if (processAlive && anchorIdx > session.anchorIdx
                    && anchorIdx - session.anchorIdx <= reanchorNearSegments()) {
                return;
            }
            double ss = segmentStartTimeSec(session, anchorIdx);
            session.anchorIdx = anchorIdx;
            session.generation++;
            // 重锚/恢复时清除失败态，允许从缺失分片请求处原地重启
            session.failed = false;
            session.exitCode = null;
            killProcess(session.process);
            cleanupPartialSegments(session);
            if (startFfmpegProcess(session, mediaFileId)) {
                log.info("转码会话跳转重锚：mediaId={} mode={} anchorIdx={} ss={}s",
                        mediaFileId, session.mode.name().toLowerCase(), anchorIdx,
                        String.format(Locale.ROOT, "%.1f", ss));
            } else {
                session.failed = true;
                log.error("跳转重锚失败：mediaId={} anchorIdx={}", mediaFileId, anchorIdx);
            }
        }
    }

    /**
     * 清理被杀进程留下的不完整分片：manifest 只登记已完整写完的分片，
     * 凡磁盘上序号大于 manifest 最后一条分片号的文件均为中断写入的半成品，
     * 删除之，避免 hls.js 拉到截断分片导致卡死。
     */
    private void cleanupPartialSegments(TranscodeSession session) {
        Path manifest = session.dir.resolve("index.m3u8");
        Path segmentsDir = session.dir.resolve("segments");
        if (!Files.exists(manifest) || !Files.isDirectory(segmentsDir)) {
            return;
        }
        int lastListed = -1;
        try {
            for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                if (!line.isEmpty() && !line.startsWith("#") && line.endsWith(".ts")) {
                    String name = line.substring(line.lastIndexOf('/') + 1);
                    lastListed = Math.max(lastListed, parseSegmentIndex(name));
                }
            }
        } catch (IOException ignored) {
            return;
        }
        if (lastListed < 0) {
            return;
        }
        int threshold = lastListed;
        try (Stream<Path> stream = Files.list(segmentsDir)) {
            stream.filter(p -> p.getFileName().toString().startsWith("seg_"))
                    .filter(p -> parseSegmentIndex(p.getFileName().toString()) > threshold)
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            log.debug("删除中断写入的不完整分片：{}", p.getFileName());
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public Path sessionDir(Long mediaFileId, PlayMode mode) {
        return Paths.get(transcodeOutputDir, modeNamePath(mode), String.valueOf(mediaFileId));
    }

    private String modeNamePath(PlayMode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 启动 ffmpeg 会话。进程后台运行，持续把分片写入会话目录。
     * remux/mixed 模式额外后台探测关键帧边界，用于合成完整 VOD 清单。
     * {@code initialSeekSec > 0} 时新会话直接从该时间起转（续播/URL 跳转场景即跳即转）。
     */
    private TranscodeSession startSession(Long mediaFileId, PlayMode mode) {
        return startSession(mediaFileId, mode, 0);
    }

    private TranscodeSession startSession(Long mediaFileId, PlayMode mode, double initialSeekSec) {
        try {
            Path dir = sessionDir(mediaFileId, mode);
            // 清理上次异常退出（如 JVM 崩溃）遗留的陈旧分片与清单，避免新会话复用过期产物
            deleteRecursively(dir);
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

            Path segmentsDir = dir.resolve("segments");
            Files.createDirectories(segmentsDir);
            Path segmentPattern = segmentsDir.resolve("seg_%05d.ts");

            double durationSec = mediaFile.getDuration() == null ? 0 : mediaFile.getDuration() / 1000.0;
            if (durationSec <= 0) {
                // 元数据缺失/未探测时兜底：ffprobe 仅读文件头即可拿到时长（毫秒级），
                // 否则 transcode 模式无法合成带完整时长的 VOD 清单，进度条一直很短。
                Double probed = probeDurationSec(input);
                if (probed != null && probed > 0) {
                    durationSec = probed;
                    persistDuration(mediaFile, (long) (probed * 1000));
                }
            }

            TranscodeSession session = new TranscodeSession(mode, dir, input, segmentPattern, durationSec);
            // 仅 transcode 模式支持初始定位直达：分片时间轴均匀（i*segSec），可直接按 t 换算出转起点；
            // remux/mixed 的 -c copy 从 0 起转本身秒级完成，且边界探测未完成前无法精确换算。
            if (initialSeekSec > 0 && durationSec > 0 && mode == PlayMode.TRANSCODE) {
                int segSec = Math.max(2, segmentSeconds);
                int targetIdx = (int) Math.floor(initialSeekSec / segSec);
                int total = (int) Math.ceil(durationSec / segSec);
                session.anchorIdx = Math.max(0, Math.min(targetIdx, Math.max(0, total - 1)));
                if (session.anchorIdx > 0) {
                    log.info("会话按定位参数直接起转：mediaId={} mode={} anchorIdx={} t={}s",
                            mediaFileId, mode.name().toLowerCase(), session.anchorIdx,
                            String.format(Locale.ROOT, "%.1f", initialSeekSec));
                }
            }
            if (!startFfmpegProcess(session, mediaFileId)) {
                return null;
            }
            if (mode != PlayMode.TRANSCODE) {
                scheduleBoundaryProbe(session, mediaFileId, input);
            }

            log.info("启动 ffmpeg 转码会话：mediaId={} mode={} dir={}", mediaFileId, mode, dir);
            return session;
        } catch (IOException e) {
            log.error("启动 ffmpeg 转码会话失败：mediaId={} mode={}", mediaFileId, mode, e);
            return null;
        }
    }

    /**
     * 用 ffprobe 快速读取源文件时长（秒）。仅解析文件头/格式信息，毫秒级完成。
     */
    private Double probeDurationSec(Path input) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "csv=p=0",
                    input.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            boolean finished = p.waitFor(Math.max(10, probeTimeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) {
                return null;
            }
            String raw = sb.toString().trim();
            int comma = raw.indexOf(',');
            if (comma > 0) {
                raw = raw.substring(0, comma);
            }
            double dur = Double.parseDouble(raw);
            return dur > 0 ? dur : null;
        } catch (Exception e) {
            log.warn("ffprobe 时长探测失败：{}", e.getMessage());
            return null;
        }
    }

    private void persistDuration(MediaFile mediaFile, Long durationMs) {
        try {
            if (mediaFile.getDuration() == null || mediaFile.getDuration() <= 0) {
                mediaFile.setDuration(durationMs);
                mediaFileService.updateDuration(mediaFile.getId(), durationMs);
            }
        } catch (Exception e) {
            log.warn("回写媒体时长到数据库失败：id={}", mediaFile.getId(), e);
        }
    }

    /**
     * 启动（或重锚后重启）当前会话的 ffmpeg 进程，并挂载新的 drain 线程。
     */
    private boolean startFfmpegProcess(TranscodeSession session, Long mediaFileId) {
        try {
            Path manifest = session.dir.resolve("index.m3u8");
            ProcessBuilder pb = new ProcessBuilder(buildFfmpegCommand(session, manifest));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            session.process = process;
            int gen = session.generation;
            Thread drainThread = new Thread(
                    () -> drainProcessOutput(process, mediaFileId, session, gen),
                    "transcode-drain-" + mediaFileId);
            drainThread.setDaemon(true);
            session.drainThread = drainThread;
            drainThread.start();
            return true;
        } catch (IOException e) {
            log.error("启动 ffmpeg 进程失败：mediaId={} mode={}", mediaFileId, session.mode, e);
            return false;
        }
    }

    private java.util.List<String> buildFfmpegCommand(TranscodeSession session, Path manifest) {
        PlayMode mode = session.mode;
        Path input = session.input;
        Path segmentPattern = session.segmentPattern;
        int anchorIdx = session.anchorIdx;
        int segSec = Math.max(2, segmentSeconds);

        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        // 禁止读取 stdin，避免非交互环境下 ffmpeg 阻塞等待输入导致首分片迟迟不产出
        cmd.add("-nostdin");
        cmd.add("-y");
        if (mode == PlayMode.TRANSCODE && activeEncoder().kind == EncoderKind.VAAPI) {
            cmd.add("-init_hw_device");
            cmd.add("vaapi=va:" + vaapiDevice);
            cmd.add("-filter_hw_device");
            cmd.add("va");
        }
        if (anchorIdx > 0) {
            // 输入流粗定位（放在 -i 前）：毫秒级快进到目标附近的关键帧，避免从头解码
            cmd.add("-ss");
            cmd.add(String.format(Locale.ROOT, "%.3f", segmentStartTimeSec(session, anchorIdx)));
        }
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
            addAacAudioArgs(cmd);
        } else {
            addTranscodeVideoArgs(cmd, activeEncoder(), segSec);
            addAacAudioArgs(cmd);
        }

        if (anchorIdx > 0 && mode == PlayMode.TRANSCODE) {
            // 输出侧精定位（放在 -i 后）：丢弃输入粗定位落点的多余帧，使输出从目标帧开始。
            // 输入侧 -ss 只落到"目标前最近关键帧"，若视频从该关键帧直接起转，视频会比音频
            // 提前（关键帧间隔）→ 重锚后音画不同步；输出侧 -ss 让视频也精确对齐目标时间，
            // 且配合 -force_key_frames 使首个分片恰好从目标分片边界开始，分片时间轴与
            // 清单预估完全一致，消除跳转后的卡顿与音画错位。
            cmd.add("-ss");
            cmd.add(String.format(Locale.ROOT, "%.3f", segmentStartTimeSec(session, anchorIdx)));
        }

        cmd.add("-f");
        cmd.add("hls");
        cmd.add("-hls_time");
        cmd.add(String.valueOf(segSec));
        cmd.add("-hls_list_size");
        cmd.add("0");
        cmd.add("-hls_flags");
        cmd.add("independent_segments");
        if (anchorIdx > 0) {
            cmd.add("-start_number");
            cmd.add(String.valueOf(anchorIdx));
        }
        // 让 m3u8 中的分片引用指向 mode 专属子路径，浏览器据此请求到对应的分片接口
        cmd.add("-hls_base_url");
        cmd.add(modeNamePath(mode) + "/segments/");
        cmd.add("-hls_segment_filename");
        cmd.add(segmentPattern.toString());
        cmd.add(manifest.toString());
        return cmd;
    }

    private void addAacAudioArgs(java.util.List<String> cmd) {
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("192k");
        cmd.add("-ac");
        cmd.add("2");
    }

    /**
     * 按编码器类型追加转码视频参数。libx264 回退方案限制线程数，避免吃满宿主 CPU。
     */
    private void addTranscodeVideoArgs(java.util.List<String> cmd, EncoderProfile enc, int segSec) {
        int width = Math.max(320, maxTranscodeWidth);
        switch (enc.kind) {
            case QSV:
                cmd.add("-c:v");
                cmd.add("h264_qsv");
                cmd.add("-preset");
                cmd.add("veryfast");
                cmd.add("-global_quality");
                cmd.add("23");
                cmd.add("-pix_fmt");
                cmd.add("nv12");
                cmd.add("-vf");
                cmd.add(String.format("scale=min(%d\\,iw):-2", width));
                addForceKeyFrames(cmd, segSec);
                break;
            case NVENC:
                cmd.add("-c:v");
                cmd.add("h264_nvenc");
                cmd.add("-preset");
                cmd.add("p5");
                cmd.add("-rc");
                cmd.add("vbr");
                cmd.add("-cq");
                cmd.add("23");
                cmd.add("-pix_fmt");
                cmd.add("yuv420p");
                cmd.add("-profile:v");
                cmd.add("main");
                cmd.add("-level");
                cmd.add("4.1");
                cmd.add("-vf");
                cmd.add(String.format("scale=min(%d\\,iw):-2", width));
                addForceKeyFrames(cmd, segSec);
                break;
            case VAAPI:
                cmd.add("-c:v");
                cmd.add("h264_vaapi");
                cmd.add("-global_quality");
                cmd.add("23");
                cmd.add("-vf");
                cmd.add(String.format("format=nv12,hwupload,scale_vaapi=%d:-2", width));
                break;
            case AMF:
                cmd.add("-c:v");
                cmd.add("h264_amf");
                cmd.add("-usage");
                cmd.add("transcoding");
                cmd.add("-quality");
                cmd.add("speed");
                cmd.add("-pix_fmt");
                cmd.add("yuv420p");
                cmd.add("-vf");
                cmd.add(String.format("scale=min(%d\\,iw):-2", width));
                addForceKeyFrames(cmd, segSec);
                break;
            case VIDEOTOOLBOX:
                cmd.add("-c:v");
                cmd.add("h264_videotoolbox");
                cmd.add("-q:v");
                cmd.add("60");
                cmd.add("-pix_fmt");
                cmd.add("yuv420p");
                cmd.add("-vf");
                cmd.add(String.format("scale=min(%d\\,iw):-2", width));
                addForceKeyFrames(cmd, segSec);
                break;
            default:
                // libx264：统一转码为 H.264 (yuv420p, 主档次) + AAC，限定线程数防止 CPU 全核吃满
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
                cmd.add(String.format("scale=min(%d\\,iw):-2", width));
                addForceKeyFrames(cmd, segSec);
                cmd.add("-threads");
                cmd.add(String.valueOf(Math.max(1, ffmpegThreads)));
                break;
        }
    }

    /**
     * 强制每 segSec 一个关键帧，保证分片边界与"源时长/分片时长"预估精确对齐。
     * VAAPI 编码器不保证支持，故省略。
     */
    private void addForceKeyFrames(java.util.List<String> cmd, int segSec) {
        cmd.add("-force_key_frames");
        cmd.add("expr:gte(t,n_forced*" + segSec + ")");
    }

    /**
     * 计算指定分片序号在时间轴上的起始时间（秒）。
     * transcode 按分片时长等分；remux/mixed 有关键帧边界时用真实边界，否则退化为等分估算。
     */
    private double segmentStartTimeSec(TranscodeSession session, int idx) {
        int segSec = Math.max(2, segmentSeconds);
        if (session.mode != PlayMode.TRANSCODE) {
            List<Double> b = session.boundaries;
            if (b != null && !b.isEmpty() && idx >= 0 && idx < b.size()) {
                return b.get(idx);
            }
        }
        return idx * (double) segSec;
    }

    private void drainProcessOutput(Process process, Long mediaFileId, TranscodeSession session, int gen) {
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = process.getInputStream().read(buffer)) != -1) {
                if (read > 0) {
                    String line = new String(buffer, 0, read).trim();
                    if (!line.isEmpty()) {
                        log.warn("[transcode:{}/{}] {}", mediaFileId, session.mode.name().toLowerCase(), line);
                    }
                }
            }
        } catch (IOException ignored) {
        } finally {
            try {
                int exitCode = process.waitFor();
                // 仅当前代进程退出才更新会话状态，避免旧进程被重锚杀掉后覆盖新进程状态
                if (gen == session.generation) {
                    session.exitCode = exitCode;
                    if (exitCode != 0) {
                        session.failed = true;
                        log.warn("转码进程退出：mediaId={} mode={} exitCode={}",
                                mediaFileId, session.mode.name().toLowerCase(), exitCode);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (gen == session.generation) {
                    session.failed = true;
                }
            }
        }
    }

    /**
     * remux/mixed 模式后台探测源视频关键帧时间，模拟 ffmpeg -hls_time 的分片边界，
     * 用于合成完整 VOD 清单。结果按 mediaFileId 缓存。
     */
    private void scheduleBoundaryProbe(TranscodeSession session, Long mediaFileId, Path input) {
        List<Double> cached = boundaryCache.get(mediaFileId);
        if (cached != null && !cached.isEmpty()) {
            session.boundaries = cached;
            return;
        }
        Thread t = new Thread(() -> {
            try {
                List<Double> boundaries = probeKeyframeBoundaries(input);
                if (boundaries != null && !boundaries.isEmpty()) {
                    session.boundaries = boundaries;
                    if (boundaryCache.size() > 128) {
                        boundaryCache.clear();
                    }
                    boundaryCache.put(mediaFileId, boundaries);
                    log.info("关键帧边界探测完成：mediaId={} segments={}", mediaFileId, boundaries.size());
                }
            } catch (Exception e) {
                log.warn("关键帧边界探测失败：mediaId={} msg={}", mediaFileId, e.getMessage());
            }
        }, "transcode-boundary-probe-" + mediaFileId);
        t.setDaemon(true);
        t.start();
    }

    /**
     * 用 ffprobe 提取视频关键帧 pts，模拟 -c copy + -hls_time 的切分规则，
     * 返回各分片起始时间（秒）。
     * {@code -skip_frame nokey} 使 ffprobe 只解析关键帧（跳过非关键帧的解码/读取），
     * 探测速度提升约一个数量级，弱机/大文件上也能在数秒内就绪，缩短 EVENT 降级窗口。
     */
    private List<Double> probeKeyframeBoundaries(Path input) {
        List<Double> keyframes = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-skip_frame", "nokey",
                    "-select_streams", "v:0",
                    "-show_entries", "frame=key_frame,pts_time",
                    "-of", "csv=p=0",
                    input.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            boolean finished = p.waitFor(Math.max(60, probeTimeoutSeconds * 4), TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) {
                return null;
            }
            for (String line : sb.toString().split("\n")) {
                int comma = line.indexOf(',');
                if (comma <= 0) {
                    continue;
                }
                if ("1".equals(line.substring(0, comma).trim())) {
                    try {
                        // ffprobe csv 行形如 "1,12.340000,"，pts 后有尾部逗号，需剔除后再解析
                        String pts = line.substring(comma + 1).trim();
                        int extra = pts.indexOf(',');
                        if (extra >= 0) {
                            pts = pts.substring(0, extra);
                        }
                        keyframes.add(Double.parseDouble(pts));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("ffprobe 关键帧探测失败：{}", e.getMessage());
            return null;
        }
        if (keyframes.isEmpty()) {
            return null;
        }
        return simulateSegmentStarts(keyframes);
    }

    /**
     * 依据关键帧时间模拟 hls 切分：每个分片在下一个 >= 起始+分片时长的关键帧处截止。
     */
    private List<Double> simulateSegmentStarts(List<Double> keyframes) {
        keyframes.sort(null);
        double segSec = Math.max(2, segmentSeconds);
        List<Double> starts = new ArrayList<>();
        double start = 0;
        starts.add(start);
        for (double kf : keyframes) {
            if (kf > start + segSec - 1e-3) {
                starts.add(kf);
                start = kf;
            }
        }
        return starts;
    }

    /**
     * 统计会话目录内已产出分片的最大序号（-1 表示尚无）。
     */
    private int maxProducedSegment(Path segmentsDir) {
        try (Stream<Path> stream = Files.list(segmentsDir)) {
            OptionalInt max = stream
                    .filter(p -> p.getFileName().toString().startsWith("seg_"))
                    .mapToInt(p -> parseSegmentIndex(p.getFileName().toString()))
                    .max();
            return max.orElse(-1);
        } catch (IOException e) {
            return -1;
        }
    }

    private int parseSegmentIndex(String name) {
        try {
            int dot = name.lastIndexOf('.');
            if (dot <= 4 || !name.startsWith("seg_")) {
                return -1;
            }
            return Integer.parseInt(name.substring(4, dot));
        } catch (Exception e) {
            return -1;
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
        killProcess(session.process);
        session.failed = true;
        deleteRecursively(session.dir);
    }

    private void killProcess(Process process) {
        try {
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
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
