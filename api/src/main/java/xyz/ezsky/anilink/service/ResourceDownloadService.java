package xyz.ezsky.anilink.service;

import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TorrentFlags;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.ezsky.anilink.model.dto.ResourceSearchDownloadRequest;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.model.entity.ResourceDownloadTask;
import xyz.ezsky.anilink.model.vo.ResourceSearchVO;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;
import xyz.ezsky.anilink.repository.ResourceDownloadTaskRepository;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@Service
public class ResourceDownloadService {

    private static final int HANDLE_WAIT_SECONDS = 30;
    private static final int DOWNLOAD_TIMEOUT_SECONDS = 12 * 60 * 60;
    private static final int MAX_TERMINAL_TASKS = 300;

    @Autowired
    private ResourceDownloadTaskRepository taskRepository;

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MediaScannerService mediaScannerService;

    @Autowired
    private SiteConfigService siteConfigService;

    private final Object executorLock = new Object();
    private final Object sessionLock = new Object();
    private final SessionManager globalSessionManager = new SessionManager();
    private volatile ThreadPoolExecutor executor;
    private volatile int executorConcurrency = -1;

    private final ConcurrentHashMap<Long, Future<?>> taskFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ActiveDownloadContext> activeContexts = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private static final Set<ResourceDownloadTask.DownloadStatus> ACTIVE_STATUSES = Set.of(
            ResourceDownloadTask.DownloadStatus.PENDING,
            ResourceDownloadTask.DownloadStatus.RUNNING,
            ResourceDownloadTask.DownloadStatus.MOVING,
            ResourceDownloadTask.DownloadStatus.SCANNING
    );

        private static final Set<ResourceDownloadTask.DownloadStatus> TERMINAL_STATUSES = Set.of(
            ResourceDownloadTask.DownloadStatus.COMPLETED,
            ResourceDownloadTask.DownloadStatus.CANCELLED,
            ResourceDownloadTask.DownloadStatus.FAILED
        );

    @PostConstruct
    public void resumeInProgressTasks() {
        synchronized (sessionLock) {
            try {
                startSessionWithContainerSafeDiskIO();
            } catch (Exception e) {
                log.error("Failed to start shared jlibtorrent session", e);
            }
        }

        List<ResourceDownloadTask> resumable = taskRepository.findByStatusInOrderByCreatedAtAsc(new ArrayList<>(ACTIVE_STATUSES));
        if (resumable.isEmpty()) {
            return;
        }
        for (ResourceDownloadTask task : resumable) {
            submitTask(task.getId());
        }
        log.info("Resumed {} in-progress resource download task(s) after startup", resumable.size());
    }

    private void startSessionWithContainerSafeDiskIO() {
        if (!isUnixLikeOs()) {
            globalSessionManager.start();
            return;
        }

        try {
            Class<?> paramsClass = Class.forName("com.frostwire.jlibtorrent.SessionParams");
            Object params = paramsClass.getConstructor().newInstance();

            boolean posixDiskIOEnabled = false;
            try {
                Method noArg = paramsClass.getMethod("setPosixDiskIO");
                noArg.invoke(params);
                posixDiskIOEnabled = true;
            } catch (NoSuchMethodException ignored) {
                // 兼容不同版本签名
            }

            if (!posixDiskIOEnabled) {
                try {
                    Method withBoolean = paramsClass.getMethod("setPosixDiskIO", boolean.class);
                    withBoolean.invoke(params, true);
                    posixDiskIOEnabled = true;
                } catch (NoSuchMethodException ignored) {
                    // 保持降级路径，避免因 API 差异导致启动失败
                }
            }

            if (posixDiskIOEnabled) {
                Method startWithParams = globalSessionManager.getClass().getMethod("start", paramsClass);
                startWithParams.invoke(globalSessionManager, params);
                log.info("Started jlibtorrent session with posix_disk_io enabled (Unix-like OS workaround)");
            } else {
                globalSessionManager.start();
                log.warn("SessionParams.setPosixDiskIO not available in this jlibtorrent version, started with default settings");
            }
        } catch (Exception e) {
            log.warn("Failed to apply posix_disk_io workaround, falling back to default session start", e);
            globalSessionManager.start();
        }
    }

    private boolean isUnixLikeOs() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return !osName.contains("win");
    }

    public ResourceSearchVO.DownloadTask startDownload(ResourceSearchDownloadRequest request) {
        if (request.getLibraryId() == null) {
            throw new IllegalArgumentException("请选择媒体库");
        }
        if (request.getMagnet() == null || request.getMagnet().isBlank()) {
            throw new IllegalArgumentException("磁力链接不能为空");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("资源标题不能为空");
        }

        MediaLibrary library = mediaLibraryRepository.findById(request.getLibraryId())
                .orElseThrow(() -> new IllegalArgumentException("媒体库不存在"));

        ResourceDownloadTask task = new ResourceDownloadTask();
        task.setTitle(request.getTitle());
        task.setMagnet(request.getMagnet());
        task.setPageUrl(request.getPageUrl());
        task.setFileSize(request.getFileSize());
        task.setPublishDate(request.getPublishDate());
        task.setSubgroupName(request.getSubgroupName());
        task.setTypeName(request.getTypeName());
        task.setLibrary(library);
        task.setStatus(ResourceDownloadTask.DownloadStatus.PENDING);
        task.setProgressPercent(0);

        ResourceDownloadTask saved = taskRepository.save(task);
        submitTask(saved.getId());
        ResourceSearchVO.DownloadTask vo = toTaskVO(saved);
        broadcastProgress();
        return vo;
    }

    public List<ResourceSearchVO.DownloadTask> listRecentTasks() {
        return taskRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(this::toTaskVO)
                .collect(Collectors.toList());
    }

    public SseEmitter subscribeTaskProgress() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("download-progress").data(listRecentTasks()));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public ResourceSearchVO.DownloadTask cancelTask(Long taskId) {
        ResourceDownloadTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("下载任务不存在"));

        if (task.getStatus() == ResourceDownloadTask.DownloadStatus.COMPLETED
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.FAILED
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.CANCELLED) {
            return toTaskVO(task);
        }

        ActiveDownloadContext ctx = activeContexts.get(taskId);
        if (ctx != null) {
            ctx.cancelled.set(true);
            // 避免在取消线程中直接移除句柄，防止与下载线程的 status() 轮询并发触发 JNI 崩溃。
            // 统一由下载线程在 finally 中执行 remove(handle) 清理。
        }

        Future<?> future = taskFutures.get(taskId);
        if (future != null) {
            future.cancel(true);
        }

        markCancelled(taskId, "任务已取消");
        ResourceDownloadTask latest = taskRepository.findById(taskId).orElse(task);
        return toTaskVO(latest);
    }

    public ResourceSearchVO.DownloadTask retryTask(Long taskId) {
        ResourceDownloadTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("下载任务不存在"));

        if (task.getStatus() == ResourceDownloadTask.DownloadStatus.RUNNING
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.PENDING
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.MOVING
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.SCANNING) {
            throw new IllegalArgumentException("任务正在执行中，无法重试");
        }

        ResourceSearchDownloadRequest request = new ResourceSearchDownloadRequest(
                task.getTitle(),
                task.getMagnet(),
                task.getPageUrl(),
                task.getFileSize(),
                task.getPublishDate(),
                task.getSubgroupName(),
                task.getTypeName(),
                task.getLibrary() != null ? task.getLibrary().getId() : null
        );
        return startDownload(request);
    }

    public void deleteTask(Long taskId) {
        ResourceDownloadTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("下载任务不存在"));

        if (task.getStatus() != null && ACTIVE_STATUSES.contains(task.getStatus())) {
            throw new IllegalArgumentException("任务正在执行中，请先取消后再删除");
        }

        taskRepository.delete(task);
        broadcastProgress();
    }

    public ResourceSearchVO.BindingStatus getBindingStatus(Long taskId) {
        ResourceDownloadTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("下载任务不存在"));

        MediaFile mediaFile = null;
        if (task.getMediaFileId() != null) {
            mediaFile = mediaFileRepository.findById(task.getMediaFileId()).orElse(null);
        }
        if (mediaFile == null && task.getFinalPath() != null && !task.getFinalPath().isBlank()) {
            String normalized = Paths.get(task.getFinalPath()).normalize().toAbsolutePath().toString();
            mediaFile = mediaFileRepository.findByFilePath(normalized).orElse(null);
            if (mediaFile == null && !normalized.equals(task.getFinalPath())) {
                mediaFile = mediaFileRepository.findByFilePath(task.getFinalPath()).orElse(null);
            }
            if (mediaFile != null) {
                task.setMediaFileId(mediaFile.getId());
                taskRepository.save(task);
            }
        }

        return ResourceSearchVO.BindingStatus.builder()
                .taskId(task.getId())
                .taskStatus(task.getStatus().name())
                .finalPath(task.getFinalPath())
                .mediaFileId(mediaFile != null ? mediaFile.getId() : task.getMediaFileId())
                .mediaFileExists(mediaFile != null)
                .animeId(mediaFile != null ? mediaFile.getAnimeId() : null)
                .animeTitle(mediaFile != null ? mediaFile.getAnimeTitle() : null)
                .episodeId(mediaFile != null ? mediaFile.getEpisodeId() : null)
                .episodeTitle(mediaFile != null ? mediaFile.getEpisodeTitle() : null)
                .matchStatus(mediaFile != null && mediaFile.getMatchStatus() != null ? mediaFile.getMatchStatus().name() : null)
                .build();
    }

    private void executeDownload(Long taskId) {
        ResourceDownloadTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }

        if (task.getStatus() == ResourceDownloadTask.DownloadStatus.CANCELLED
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.FAILED
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.COMPLETED) {
            return;
        }

        ActiveDownloadContext context = new ActiveDownloadContext();
        if (task.getStatus() == ResourceDownloadTask.DownloadStatus.PENDING
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.RUNNING) {
            activeContexts.put(taskId, context);
        }

        try {
            Path movedPath = continueTask(taskId, task, context);
            completeTask(taskId, movedPath);
        } catch (DownloadCancelledException e) {
            markCancelled(taskId, "任务已取消");
        } catch (Exception e) {
            log.error("executeDownload error, taskId={}", taskId, e);
            failTask(taskId, "任务失败: " + e.getMessage());
        } finally {
            activeContexts.remove(taskId);
            taskFutures.remove(taskId);
        }
    }

    private Path continueTask(Long taskId, ResourceDownloadTask task, ActiveDownloadContext context) throws IOException {
        if (task.getStatus() == ResourceDownloadTask.DownloadStatus.MOVING) {
            Path movedPath = moveToLibrary(taskId, true);
            updateStatus(taskId, ResourceDownloadTask.DownloadStatus.SCANNING, "文件迁移完成，开始触发媒体库扫描");
            triggerScan(taskId);
            return movedPath;
        }

        if (task.getStatus() == ResourceDownloadTask.DownloadStatus.SCANNING) {
            triggerScan(taskId);
            return task.getFinalPath() != null ? Paths.get(task.getFinalPath()) : null;
        }

        task.setStatus(ResourceDownloadTask.DownloadStatus.RUNNING);
        if (task.getStartedAt() == null) {
            task.setStartedAt(Timestamp.from(Instant.now()));
        }
        taskRepository.save(task);
        broadcastProgress();

        Path tempDir = resolveTempDir(task);
        task.setTempDir(tempDir.toString());
        taskRepository.save(task);
        broadcastProgress();

        RuntimeLimitSettings limitSettings = loadRuntimeLimitSettings();
        applySessionGlobalRateLimit(limitSettings);
        int seedSeconds = limitSettings.seedSeconds;
        final Path[] movedPathHolder = new Path[1];

        DownloadFinishedHook finishedHook = null;
        if (seedSeconds > 0) {
            finishedHook = () -> {
                if (movedPathHolder[0] != null) {
                    return;
                }
                updateStatus(taskId, ResourceDownloadTask.DownloadStatus.MOVING, "下载完成，开始入库（保留源文件做种）");
                movedPathHolder[0] = moveToLibrary(taskId, false);
                updateStatus(taskId, ResourceDownloadTask.DownloadStatus.SCANNING, "文件入库完成，开始触发媒体库扫描");
                triggerScan(taskId);
                updateStatus(taskId, ResourceDownloadTask.DownloadStatus.RUNNING, "媒体库扫描已触发，继续做种中");
            };
        }

        runJlibtorrentDownload(taskId, appendTrackers(task.getMagnet()), tempDir, context, finishedHook, limitSettings);

        if (seedSeconds <= 0) {
            updateStatus(taskId, ResourceDownloadTask.DownloadStatus.MOVING, "下载完成，开始迁移文件");
            Path movedPath = moveToLibrary(taskId, true);
            updateStatus(taskId, ResourceDownloadTask.DownloadStatus.SCANNING, "文件迁移完成，开始触发媒体库扫描");
            triggerScan(taskId);
            return movedPath;
        }

        deleteDirectoryQuietly(tempDir);
        if (movedPathHolder[0] != null) {
            return movedPathHolder[0];
        }
        ResourceDownloadTask latest = taskRepository.findById(taskId).orElse(null);
        if (latest != null && latest.getFinalPath() != null && !latest.getFinalPath().isBlank()) {
            return Paths.get(latest.getFinalPath());
        }
        return null;
    }

    private Path resolveTempDir(ResourceDownloadTask task) throws IOException {
        if (task.getTempDir() != null && !task.getTempDir().isBlank()) {
            Path existing = Paths.get(task.getTempDir()).toAbsolutePath();
            Files.createDirectories(existing);
            return existing;
        }
        String tempRoot = siteConfigService.getResourceDownloadTempDir();
        if (tempRoot == null || tempRoot.isBlank()) {
            tempRoot = "./data/media-data/download-temp";
        }
        Path tempDir = Paths.get(tempRoot, String.valueOf(task.getId())).toAbsolutePath();
        Files.createDirectories(tempDir);
        return tempDir;
    }

    private void completeTask(Long taskId, Path movedPath) {
        ResourceDownloadTask latest = taskRepository.findById(taskId).orElse(null);
        if (latest == null) {
            return;
        }
        latest.setStatus(ResourceDownloadTask.DownloadStatus.COMPLETED);
        latest.setProgressPercent(100);
        latest.setFinishedAt(Timestamp.from(Instant.now()));
        if (movedPath != null) {
            latest.setOutputMessage(appendMessage(latest.getOutputMessage(), "任务完成: " + movedPath));
        } else {
            latest.setOutputMessage(appendMessage(latest.getOutputMessage(), "任务完成"));
        }
        taskRepository.save(latest);
        broadcastProgress();
        trimTerminalTasksIfNeeded();
    }

    private void runJlibtorrentDownload(Long taskId,
                                        String magnet,
                                        Path tempDir,
                                        ActiveDownloadContext context,
                                        DownloadFinishedHook downloadFinishedHook,
                                        RuntimeLimitSettings limitSettings) {
        try {
            TorrentHandle handle = addTorrentAndWaitHandle(magnet, tempDir);
            if (handle == null || !handle.isValid()) {
                throw new IllegalStateException("jlibtorrent 未能创建下载任务句柄");
            }
            context.handleRef.set(handle);

            long startMs = System.currentTimeMillis();
            int seedSeconds = limitSettings.seedSeconds;
            long finishMs = -1;
            boolean finishedHandled = false;
            while (true) {
                checkCancellation(taskId, context);
                TorrentStatus status;
                try {
                    synchronized (sessionLock) {
                        if (!handle.isValid()) {
                            throw new IllegalStateException("下载句柄已失效");
                        }
                        status = handle.status();
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("读取下载状态失败，下载句柄可能已释放", e);
                }
                int progress = (int) Math.round(status.progress() * 100.0d);
                long downloadedBytes = status.totalDone();
                long totalBytes = status.totalWanted();
                long downloadSpeed = status.downloadRate();
                long uploadSpeed = status.uploadRate();

                updateProgress(taskId, progress, downloadedBytes, totalBytes, downloadSpeed, uploadSpeed);

                if (status.isFinished()) {
                    if (!finishedHandled && downloadFinishedHook != null) {
                        try {
                            downloadFinishedHook.run();
                        } catch (Exception e) {
                            throw new IllegalStateException("下载完成后的入库/扫描处理失败", e);
                        }
                        finishedHandled = true;
                    }
                    if (finishMs < 0) {
                        finishMs = System.currentTimeMillis();
                    }
                    updateProgress(taskId, 100, downloadedBytes, totalBytes, downloadSpeed, uploadSpeed);
                    if (seedSeconds <= 0 || (System.currentTimeMillis() - finishMs) / 1000 >= seedSeconds) {
                        break;
                    }
                }

                if ((System.currentTimeMillis() - startMs) / 1000 > DOWNLOAD_TIMEOUT_SECONDS) {
                    throw new IllegalStateException("下载超时，超过 " + DOWNLOAD_TIMEOUT_SECONDS + " 秒");
                }

                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownloadCancelledException("任务取消", taskId);
        } finally {
            TorrentHandle handle = context.handleRef.get();
            if (handle != null) {
                try {
                    synchronized (sessionLock) {
                        globalSessionManager.remove(handle);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private TorrentHandle addTorrentAndWaitHandle(String magnet, Path tempDir) throws InterruptedException {
        synchronized (sessionLock) {
            List<String> before = listHandleKeys(globalSessionManager.getTorrentHandles());
            globalSessionManager.download(magnet, tempDir.toFile(), TorrentFlags.AUTO_MANAGED);
            return waitForNewHandle(before);
        }
    }

    private TorrentHandle waitForNewHandle(List<String> existingKeys) throws InterruptedException {
        for (int i = 0; i < HANDLE_WAIT_SECONDS; i++) {
            TorrentHandle[] handles = globalSessionManager.getTorrentHandles();
            if (handles != null && handles.length > 0) {
                for (TorrentHandle handle : handles) {
                    if (handle == null || !handle.isValid()) {
                        continue;
                    }
                    String key = handleKey(handle);
                    if (!existingKeys.contains(key)) {
                        return handle;
                    }
                }
            }
            Thread.sleep(1000);
        }
        return null;
    }

    private List<String> listHandleKeys(TorrentHandle[] handles) {
        List<String> keys = new ArrayList<>();
        if (handles == null) {
            return keys;
        }
        for (TorrentHandle handle : handles) {
            if (handle == null || !handle.isValid()) {
                continue;
            }
            keys.add(handleKey(handle));
        }
        return keys;
    }

    private String handleKey(TorrentHandle handle) {
        try {
            return String.valueOf(handle.infoHash());
        } catch (Exception ignored) {
            return String.valueOf(handle);
        }
    }

    private void updateProgress(Long taskId, int progressPercent, long downloadedBytes, long totalBytes, long downloadBytesPerSec, long uploadBytesPerSec) {
        ResourceDownloadTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setProgressPercent(Math.max(0, Math.min(100, progressPercent)));
        task.setDownloadedBytes(downloadedBytes);
        task.setTotalBytes(totalBytes > 0 ? totalBytes : null);
        String down = formatSpeed(downloadBytesPerSec);
        String up = formatSpeed(uploadBytesPerSec);
        task.setSpeedText("↓" + down + " / ↑" + up);
        task.setOutputMessage(appendMessage(task.getOutputMessage(),
                "progress=" + task.getProgressPercent() + "%, downloaded=" + downloadedBytes + " bytes, total=" + totalBytes + " bytes, download=" + down + ", upload=" + up));
        taskRepository.save(task);
        broadcastProgress();
    }

    private String appendTrackers(String magnet) {
        String trackers = siteConfigService.getResourceCustomTrackers();
        if (trackers == null || trackers.isBlank()) {
            return magnet;
        }
        String merged = magnet;
        String[] parts = trackers.split("[\\r\\n,]");
        for (String raw : parts) {
            String tracker = raw == null ? "" : raw.trim();
            if (tracker.isEmpty()) {
                continue;
            }
            String encoded = URLEncoder.encode(tracker, StandardCharsets.UTF_8);
            String marker = "tr=" + encoded;
            if (merged.contains(marker)) {
                continue;
            }
            merged = merged + "&tr=" + encoded;
        }
        return merged;
    }

    private void applySessionGlobalRateLimit(RuntimeLimitSettings limitSettings) {
        int globalDownloadKbps = Math.max(0, limitSettings.downloadLimitKbps);
        int globalUploadKbps = Math.max(0, limitSettings.uploadLimitKbps);

        int downloadBps = globalDownloadKbps > 0 ? globalDownloadKbps * 1024 : -1;
        int uploadBps = globalUploadKbps > 0 ? globalUploadKbps * 1024 : -1;

        synchronized (sessionLock) {
            invokeLimitMethod(globalSessionManager, "setDownloadRateLimit", downloadBps);
            invokeLimitMethod(globalSessionManager, "setUploadRateLimit", uploadBps);
            invokeLimitMethod(globalSessionManager, "setDownloadLimit", downloadBps);
            invokeLimitMethod(globalSessionManager, "setUploadLimit", uploadBps);
        }
    }

    private RuntimeLimitSettings loadRuntimeLimitSettings() {
        return new RuntimeLimitSettings(
                Math.max(0, siteConfigService.getResourceDownloadLimitKbps()),
                Math.max(0, siteConfigService.getResourceUploadLimitKbps()),
                Math.max(0, siteConfigService.getResourceSeedTimeSeconds())
        );
    }

    private void invokeLimitMethod(Object target, String methodName, int limitBytesPerSec) {
        try {
            target.getClass().getMethod(methodName, int.class).invoke(target, limitBytesPerSec);
        } catch (Exception e) {
            log.debug("Skip rate limit method {}, limit={}", methodName, limitBytesPerSec);
        }
    }

    private String formatSpeed(long bytesPerSec) {
        if (bytesPerSec < 1024) {
            return bytesPerSec + " B/s";
        }
        double kb = bytesPerSec / 1024.0d;
        if (kb < 1024) {
            return String.format("%.1f KB/s", kb);
        }
        double mb = kb / 1024.0d;
        return String.format("%.2f MB/s", mb);
    }

    private String appendMessage(String oldMessage, String line) {
        String merged = (oldMessage == null || oldMessage.isBlank()) ? line : oldMessage + "\n" + line;
        if (merged.length() <= 4000) {
            return merged;
        }
        return merged.substring(merged.length() - 4000);
    }

    private void updateStatus(Long taskId, ResourceDownloadTask.DownloadStatus status, String message) {
        ResourceDownloadTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setStatus(status);
        task.setOutputMessage(appendMessage(task.getOutputMessage(), message));
        taskRepository.save(task);
        broadcastProgress();
    }

    private Path moveToLibrary(Long taskId, boolean removeSourceFiles) throws IOException {
        ResourceDownloadTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("下载任务不存在"));

        Path tempDir = Paths.get(task.getTempDir());
        if (!Files.exists(tempDir)) {
            throw new IllegalStateException("暂存目录不存在: " + tempDir);
        }

        List<Path> files;
        try (Stream<Path> stream = Files.walk(tempDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        }

        if (files.isEmpty()) {
            throw new IllegalStateException("未找到可迁移的下载文件");
        }

        Path targetLibrary = Paths.get(task.getLibrary().getPath());
        Files.createDirectories(targetLibrary);

        Path mainFile = files.stream().max(Comparator.comparingLong(this::safeSize)).orElse(files.get(0));
        Path finalMainPath = null;

        for (Path source : files) {
            Path relative = tempDir.relativize(source);
            Path desiredTarget = targetLibrary.resolve(relative);
            Files.createDirectories(desiredTarget.getParent());
            Path target = uniquePath(desiredTarget);
            if (removeSourceFiles) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                linkOrCopy(source, target);
            }
            if (source.equals(mainFile)) {
                finalMainPath = target;
            }
        }

        if (removeSourceFiles) {
            // 删除空目录，避免暂存目录堆积。
            deleteDirectoryQuietly(tempDir);
        }

        if (finalMainPath == null) {
            throw new IllegalStateException("无法确认主文件迁移路径");
        }
        task.setFinalPath(finalMainPath.toAbsolutePath().normalize().toString());
        taskRepository.save(task);
        return finalMainPath;
    }

    private void linkOrCopy(Path source, Path target) throws IOException {
        try {
            Files.createLink(target, source);
        } catch (UnsupportedOperationException | IOException e) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void triggerScan(Long taskId) {
        ResourceDownloadTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        MediaLibrary library = task.getLibrary();
        mediaScannerService.scanLibrary(library);

        if (task.getFinalPath() != null && !task.getFinalPath().isBlank()) {
            String finalPath = Paths.get(task.getFinalPath()).normalize().toAbsolutePath().toString();
            Optional<MediaFile> mediaFileOpt = mediaFileRepository.findByFilePath(finalPath);
            if (mediaFileOpt.isEmpty() && !finalPath.equals(task.getFinalPath())) {
                mediaFileOpt = mediaFileRepository.findByFilePath(task.getFinalPath());
            }
            mediaFileOpt.ifPresent(mediaFile -> {
                task.setMediaFileId(mediaFile.getId());
                taskRepository.save(task);
                broadcastProgress();
            });
        }
    }

    private Path uniquePath(Path path) {
        if (!Files.exists(path)) {
            return path;
        }

        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";

        int index = 1;
        while (true) {
            Path candidate = path.getParent().resolve(base + "_" + index + ext);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private void deleteDirectoryQuietly(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void failTask(Long taskId, String message) {
        ResourceDownloadTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setStatus(ResourceDownloadTask.DownloadStatus.FAILED);
        task.setErrorMessage(message);
        task.setFinishedAt(Timestamp.from(Instant.now()));
        task.setOutputMessage(appendMessage(task.getOutputMessage(), message));
        taskRepository.save(task);
        broadcastProgress();
        trimTerminalTasksIfNeeded();
    }

    private void markCancelled(Long taskId, String message) {
        ResourceDownloadTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setStatus(ResourceDownloadTask.DownloadStatus.CANCELLED);
        task.setFinishedAt(Timestamp.from(Instant.now()));
        task.setOutputMessage(appendMessage(task.getOutputMessage(), message));
        taskRepository.save(task);
        broadcastProgress();
        trimTerminalTasksIfNeeded();
    }

    private void trimTerminalTasksIfNeeded() {
        List<ResourceDownloadTask> terminalTasks = taskRepository.findTop500ByStatusInOrderByCreatedAtDesc(new ArrayList<>(TERMINAL_STATUSES));
        if (terminalTasks.size() <= MAX_TERMINAL_TASKS) {
            return;
        }
        List<ResourceDownloadTask> toDelete = terminalTasks.subList(MAX_TERMINAL_TASKS, terminalTasks.size());
        if (toDelete.isEmpty()) {
            return;
        }
        taskRepository.deleteAll(toDelete);
    }

    private void checkCancellation(Long taskId, ActiveDownloadContext context) {
        if (Thread.currentThread().isInterrupted() || context.cancelled.get()) {
            throw new DownloadCancelledException("任务取消", taskId);
        }
    }

    private void submitTask(Long taskId) {
        Future<?> existing = taskFutures.get(taskId);
        if (existing != null && !existing.isDone()) {
            return;
        }
        ThreadPoolExecutor pool = getExecutor();
        Future<?> future = pool.submit(() -> executeDownload(taskId));
        taskFutures.put(taskId, future);
    }

    private ThreadPoolExecutor getExecutor() {
        int configured = siteConfigService.getResourceDownloadMaxConcurrency();
        configured = Math.max(1, configured);
        synchronized (executorLock) {
            if (executor == null || executor.isShutdown() || executorConcurrency != configured) {
                if (executor != null && !executor.isShutdown()) {
                    executor.shutdownNow();
                }
                executor = new ThreadPoolExecutor(
                        configured,
                        configured,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>()
                );
                executorConcurrency = configured;
                log.info("Resource download concurrency set to {}", configured);
            }
            return executor;
        }
    }

    @PreDestroy
    public void destroy() {
        synchronized (executorLock) {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
        for (ActiveDownloadContext context : new ArrayList<>(activeContexts.values())) {
            context.cancelled.set(true);
        }
        activeContexts.clear();
        synchronized (sessionLock) {
            try {
                globalSessionManager.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private void broadcastProgress() {
        if (emitters.isEmpty()) {
            return;
        }
        List<ResourceSearchVO.DownloadTask> payload = listRecentTasks();
        List<SseEmitter> removed = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("download-progress").data(payload));
            } catch (Exception e) {
                removed.add(emitter);
            }
        }
        if (!removed.isEmpty()) {
            emitters.removeAll(removed);
        }
    }

    private static final class ActiveDownloadContext {
        private final AtomicReference<TorrentHandle> handleRef = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
    }

    private static final class DownloadCancelledException extends RuntimeException {
        private final Long taskId;

        private DownloadCancelledException(String message, Long taskId) {
            super(message);
            this.taskId = taskId;
        }

        @SuppressWarnings("unused")
        public Long getTaskId() {
            return taskId;
        }
    }

    @FunctionalInterface
    private interface DownloadFinishedHook {
        void run() throws Exception;
    }

    private static final class RuntimeLimitSettings {
        private final int downloadLimitKbps;
        private final int uploadLimitKbps;
        private final int seedSeconds;

        private RuntimeLimitSettings(int downloadLimitKbps, int uploadLimitKbps, int seedSeconds) {
            this.downloadLimitKbps = downloadLimitKbps;
            this.uploadLimitKbps = uploadLimitKbps;
            this.seedSeconds = seedSeconds;
        }
    }

    private ResourceSearchVO.DownloadTask toTaskVO(ResourceDownloadTask task) {
        String downloadSpeedText = null;
        String uploadSpeedText = null;
        String mergedSpeed = task.getSpeedText();
        if (mergedSpeed != null && mergedSpeed.contains("/")) {
            String[] parts = mergedSpeed.split("/");
            if (parts.length >= 2) {
                downloadSpeedText = parts[0].replace("↓", "").trim();
                uploadSpeedText = parts[1].replace("↑", "").trim();
            }
        }
        if (downloadSpeedText == null || downloadSpeedText.isBlank()) {
            downloadSpeedText = mergedSpeed;
        }
        if (uploadSpeedText == null || uploadSpeedText.isBlank()) {
            uploadSpeedText = "0 B/s";
        }

        return ResourceSearchVO.DownloadTask.builder()
                .id(task.getId())
                .title(task.getTitle())
                .magnet(task.getMagnet())
                .pageUrl(task.getPageUrl())
                .fileSize(task.getFileSize())
                .publishDate(task.getPublishDate())
                .subgroupName(task.getSubgroupName())
                .typeName(task.getTypeName())
                .libraryId(task.getLibrary() != null ? task.getLibrary().getId() : null)
                .libraryName(task.getLibrary() != null ? task.getLibrary().getName() : null)
                .status(task.getStatus() != null ? task.getStatus().name() : null)
                .progressPercent(task.getProgressPercent())
                .downloadedBytes(task.getDownloadedBytes())
                .totalBytes(task.getTotalBytes())
                .downloadSpeedText(downloadSpeedText)
                .uploadSpeedText(uploadSpeedText)
                .speedText(task.getSpeedText())
                .outputMessage(task.getOutputMessage())
                .errorMessage(task.getErrorMessage())
                .tempDir(task.getTempDir())
                .finalPath(task.getFinalPath())
                .mediaFileId(task.getMediaFileId())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
