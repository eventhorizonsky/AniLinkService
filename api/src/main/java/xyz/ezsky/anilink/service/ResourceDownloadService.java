package xyz.ezsky.anilink.service;

import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TorrentFlags;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.ezsky.anilink.model.dto.ResourceSearchDownloadRequest;
import xyz.ezsky.anilink.model.dto.ResourceSearchBatchDownloadRequest;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.model.entity.ResourceDownloadTask;
import xyz.ezsky.anilink.model.vo.CombinedTrackerListVO;
import xyz.ezsky.anilink.model.vo.ResourceSearchVO;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;
import xyz.ezsky.anilink.repository.ResourceDownloadTaskRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
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
    private static final int MAX_TERMINAL_TASKS = 300;
    private static final int SHUTDOWN_GRACE_SECONDS = 30;

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

    @Autowired
    private TrackerListService trackerListService;

    private final Object executorLock = new Object();
    private final Object sessionLock = new Object();
    private final SessionManager globalSessionManager = new SessionManager();
    private volatile ThreadPoolExecutor executor;
    private volatile int executorConcurrency = -1;

    // 做种专用线程池:下载完成后做种交接到这里,不占用下载并发槽位。
    // 做种任务每秒轮询(非空闲),cached 线程池不会因空闲被回收。
    private final ThreadPoolExecutor seedingExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "anilink-seeding-" + r.hashCode());
        thread.setDaemon(true);
        return thread;
    });

    private final ConcurrentHashMap<Long, Future<?>> taskFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ActiveDownloadContext> activeContexts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, long[]> activeSpeeds = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private static final Set<ResourceDownloadTask.DownloadStatus> ACTIVE_STATUSES = Set.of(
            ResourceDownloadTask.DownloadStatus.PENDING,
            ResourceDownloadTask.DownloadStatus.RUNNING,
            ResourceDownloadTask.DownloadStatus.SEEDING,
            ResourceDownloadTask.DownloadStatus.MOVING,
            ResourceDownloadTask.DownloadStatus.SCANNING
    );

        private static final Set<ResourceDownloadTask.DownloadStatus> TERMINAL_STATUSES = Set.of(
            ResourceDownloadTask.DownloadStatus.COMPLETED,
            ResourceDownloadTask.DownloadStatus.CANCELLED,
            ResourceDownloadTask.DownloadStatus.FAILED,
            ResourceDownloadTask.DownloadStatus.STALLED
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

    public List<ResourceSearchVO.DownloadTaskSummary> listRecentTasksSummary(int limit) {
        int n = Math.max(1, Math.min(limit, 20));
        return taskRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .limit(n)
                .map(this::toTaskSummaryVO)
                .collect(Collectors.toList());
    }

    public SseEmitter subscribeTaskProgress() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("download-progress").data(progressPayload()));
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
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.CANCELLED
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.STALLED) {
            return toTaskVO(task);
        }

        ActiveDownloadContext ctx = activeContexts.get(taskId);
        if (ctx != null) {
            ctx.cancelled.set(true);
            // 不要调用 future.cancel(true)：Thread.interrupt() 对 H2 内嵌模式（MVStore/FileChannel）不安全，
            // 中断正在执行数据库读写的下载线程会关闭整个 H2 库（90098）或引发行锁超时（50200）。
            // 取消仅依赖 ctx.cancelled 协作式标记，由下载线程在轮询中检查并自行收尾，
            // 同时避免在取消线程中直接移除句柄，防止与 status() 轮询并发触发 JNI 崩溃。
        }

        markCancelled(taskId, "任务已取消");
        ResourceDownloadTask latest = taskRepository.findById(taskId).orElse(task);
        return toTaskVO(latest);
    }

    public ResourceSearchVO.DownloadTask retryTask(Long taskId) {
        ResourceDownloadTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("下载任务不存在"));

        if (ACTIVE_STATUSES.contains(task.getStatus())) {
            throw new IllegalArgumentException("任务正在执行中，无法重试");
        }
        if (task.getStatus() == ResourceDownloadTask.DownloadStatus.COMPLETED) {
            throw new IllegalArgumentException("任务已完成，无需重试");
        }

        // 等上一轮线程完全收尾，避免新旧任务并发访问同一 jlibtorrent 句柄/暂存目录。
        Future<?> existing = taskFutures.get(taskId);
        if (existing != null && !existing.isDone()) {
            throw new IllegalArgumentException("任务正在收尾中，请稍后再试");
        }

        // 文件已成功迁入媒体库（如失败发生在扫描/入库阶段）：只需补触发扫描并完成任务，不再重复下载。
        String finalPath = task.getFinalPath();
        if (finalPath != null && !finalPath.isBlank() && Files.exists(Paths.get(finalPath))) {
            task.setStatus(ResourceDownloadTask.DownloadStatus.SCANNING);
            task.setErrorMessage(null);
            task.setStartedAt(null);
            task.setFinishedAt(null);
            task.setOutputMessage(appendMessage(task.getOutputMessage(), "任务重试：文件已存在，重新触发扫描入库"));
            taskRepository.save(task);
            submitTask(task.getId());
            broadcastProgress();
            return toTaskVO(task);
        }

        // 正常断点续传：复用原任务与原暂存目录重新添加同一磁链，
        // jlibtorrent 会校验已下载的 piece 并只补齐缺失部分，避免从头下载与孤儿文件堆积。
        task.setStatus(ResourceDownloadTask.DownloadStatus.PENDING);
        task.setProgressPercent(0);
        task.setDownloadedBytes(0L);
        task.setTotalBytes(null);
        task.setSpeedText(null);
        task.setErrorMessage(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setFinalPath(null);
        task.setMediaFileId(null);
        task.setOutputMessage(appendMessage(task.getOutputMessage(), "任务重试：复用原暂存目录断点续传"));
        taskRepository.save(task);

        submitTask(task.getId());
        broadcastProgress();
        return toTaskVO(task);
    }

    public void deleteTask(Long taskId) {
        ResourceDownloadTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("下载任务不存在"));

        if (task.getStatus() != null && ACTIVE_STATUSES.contains(task.getStatus())) {
            throw new IllegalArgumentException("任务正在执行中，请先取消后再删除");
        }

        // 等上一轮线程完全收尾，避免并发访问同一 jlibtorrent 句柄/暂存目录。
        Future<?> existing = taskFutures.get(taskId);
        if (existing != null && !existing.isDone()) {
            throw new IllegalArgumentException("任务正在收尾中，请稍后再删除");
        }

        // 暂存目录与任务强绑定：删除任务即清理暂存文件；媒体库中的已入库文件不受影响。
        if (task.getTempDir() != null && !task.getTempDir().isBlank()) {
            try {
                deleteDirectoryQuietly(Paths.get(task.getTempDir()));
            } catch (Exception e) {
                log.warn("清理暂存目录失败, taskId={}, dir={}", taskId, task.getTempDir(), e);
            }
        }

        activeSpeeds.remove(taskId);
        taskRepository.delete(task);
        broadcastProgress();
    }

    public ResourceSearchVO.DownloadTaskPageResult listTasks(int page, int size, String status, String keyword) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        List<ResourceDownloadTask.DownloadStatus> statuses = null;
        if (status != null && !status.isBlank()) {
            if ("active".equalsIgnoreCase(status)) {
                statuses = new ArrayList<>(ACTIVE_STATUSES);
            } else {
                try {
                    statuses = List.of(ResourceDownloadTask.DownloadStatus.valueOf(status));
                } catch (IllegalArgumentException ignored) {
                    statuses = null;
                }
            }
        }
        String safeKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);

        Page<ResourceDownloadTask> pageResult = statuses == null
                ? taskRepository.searchTasks(null, safeKeyword, pageable)
                : taskRepository.searchTasksInStatuses(statuses, safeKeyword, pageable);

        List<ResourceSearchVO.DownloadTask> items = pageResult.getContent().stream()
                .map(this::toTaskVO)
                .collect(Collectors.toList());

        return ResourceSearchVO.DownloadTaskPageResult.builder()
                .items(items)
                .total(pageResult.getTotalElements())
                .page(safePage)
                .size(safeSize)
                .hasMore(safePage < pageResult.getTotalPages())
                .stats(buildStats())
                .build();
    }

    public ResourceSearchVO.DownloadTaskStats buildStats() {
        long pending = taskRepository.countByStatus(ResourceDownloadTask.DownloadStatus.PENDING);
        long running = taskRepository.countByStatus(ResourceDownloadTask.DownloadStatus.RUNNING);
        long seeding = taskRepository.countByStatus(ResourceDownloadTask.DownloadStatus.SEEDING);
        long moving = taskRepository.countByStatus(ResourceDownloadTask.DownloadStatus.MOVING);
        long scanning = taskRepository.countByStatus(ResourceDownloadTask.DownloadStatus.SCANNING);
        long completed = taskRepository.countByStatus(ResourceDownloadTask.DownloadStatus.COMPLETED);
        long failed = taskRepository.countByStatus(ResourceDownloadTask.DownloadStatus.FAILED);
        long cancelled = taskRepository.countByStatus(ResourceDownloadTask.DownloadStatus.CANCELLED);
        long stalled = taskRepository.countByStatus(ResourceDownloadTask.DownloadStatus.STALLED);

        Timestamp startOfToday = Timestamp.valueOf(LocalDate.now().atStartOfDay());

        long downloadBps = 0;
        long uploadBps = 0;
        for (long[] speeds : activeSpeeds.values()) {
            downloadBps += speeds[0];
            uploadBps += speeds[1];
        }

        return ResourceSearchVO.DownloadTaskStats.builder()
                .pending(pending)
                .running(running)
                .seeding(seeding)
                .moving(moving)
                .scanning(scanning)
                .completed(completed)
                .failed(failed)
                .cancelled(cancelled)
                .stalled(stalled)
                .active(pending + running + seeding + moving + scanning)
                .todayCompleted(taskRepository.countByStatusFinishedAfter(ResourceDownloadTask.DownloadStatus.COMPLETED, startOfToday))
                .todayFailed(taskRepository.countByStatusFinishedAfter(ResourceDownloadTask.DownloadStatus.FAILED, startOfToday))
                .todayCancelled(taskRepository.countByStatusFinishedAfter(ResourceDownloadTask.DownloadStatus.CANCELLED, startOfToday))
                .downloadBps(downloadBps)
                .uploadBps(uploadBps)
                .build();
    }

    public ResourceSearchVO.BatchDownloadResult startDownloadBatch(ResourceSearchBatchDownloadRequest request) {
        if (request.getLibraryId() == null) {
            throw new IllegalArgumentException("请选择媒体库");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("没有待下载的资源");
        }

        List<String> errors = new ArrayList<>();
        int created = 0;
        int duplicated = 0;
        for (ResourceSearchDownloadRequest item : request.getItems()) {
            String title = item.getTitle() == null || item.getTitle().isBlank() ? "(无标题)" : item.getTitle();
            try {
                if (item.getMagnet() == null || item.getMagnet().isBlank()) {
                    errors.add(title + ": 磁力链接无效");
                    continue;
                }
                if (taskRepository.existsByMagnet(item.getMagnet())) {
                    duplicated++;
                    continue;
                }
                item.setLibraryId(request.getLibraryId());
                startDownload(item);
                created++;
            } catch (Exception e) {
                log.warn("批量下载失败, title={}", title, e);
                errors.add(title + ": " + e.getMessage());
            }
        }

        return ResourceSearchVO.BatchDownloadResult.builder()
                .created(created)
                .duplicated(duplicated)
                .errors(errors)
                .build();
    }

    private Map<String, Object> progressPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tasks", listRecentTasks());
        payload.put("stats", buildStats());
        return payload;
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
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.COMPLETED
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.STALLED) {
            return;
        }

        ActiveDownloadContext context = new ActiveDownloadContext();
        if (task.getStatus() == ResourceDownloadTask.DownloadStatus.PENDING
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.RUNNING
                || task.getStatus() == ResourceDownloadTask.DownloadStatus.SEEDING) {
            activeContexts.put(taskId, context);
        }

        try {
            Path movedPath = continueTask(taskId, task, context);
            if (!context.transferred.get()) {
                completeTask(taskId, movedPath);
            }
        } catch (DownloadCancelledException e) {
            markCancelled(taskId, "任务已取消");
        } catch (DownloadStalledException e) {
            markStalled(taskId, e.getMessage());
        } catch (Exception e) {
            log.error("executeDownload error, taskId={}", taskId, e);
            failTask(taskId, "任务失败: " + e.getMessage());
        } finally {
            if (!context.transferred.get()) {
                activeContexts.remove(taskId);
                taskFutures.remove(taskId);
            }
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

        if (task.getStatus() != ResourceDownloadTask.DownloadStatus.SEEDING) {
            task.setStatus(ResourceDownloadTask.DownloadStatus.RUNNING);
        }
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
                updateStatus(taskId, ResourceDownloadTask.DownloadStatus.SEEDING, "媒体库扫描已触发，继续做种中");
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

        // seedSeconds > 0 时,下载完成后已把做种交接给独立做种线程,由它负责清理暂存目录并完成任务
        if (context.transferred.get()) {
            return null;
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
        activeSpeeds.remove(taskId);
        latest.setSpeedText(null);
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
            TorrentHandle handle = addTorrentAndWaitHandle(magnet, tempDir, taskId, context);
            if (handle == null || !handle.isValid()) {
                throw new IllegalStateException("jlibtorrent 未能创建下载任务句柄");
            }
            context.handleRef.set(handle);

            int seedSeconds = limitSettings.seedSeconds;
            long finishMs = -1;
            boolean finishedHandled = false;
            int stallTimeoutSeconds = siteConfigService.getResourceDownloadStallTimeoutSeconds();
            long lastProgressMs = System.currentTimeMillis();
            long lastDownloadedBytes = -1;
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

                if (!status.isFinished()) {
                    if (downloadedBytes > lastDownloadedBytes || downloadSpeed > 0) {
                        lastProgressMs = System.currentTimeMillis();
                        lastDownloadedBytes = downloadedBytes;
                    } else if (stallTimeoutSeconds > 0
                            && System.currentTimeMillis() - lastProgressMs >= stallTimeoutSeconds * 1000L) {
                        throw new DownloadStalledException("下载停滞: 超过 " + stallTimeoutSeconds + " 秒无新进度，请检查做种情况或重试");
                    }
                }

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
                    if (seedSeconds <= 0) {
                        break;
                    }
                    // 需要做种:交接给独立做种线程,立即释放下载并发槽位
                    if (!context.transferred.get()) {
                        context.transferred.set(true);
                        Future<?> seedingFuture = seedingExecutor.submit(() -> executeSeeding(taskId, context, seedSeconds));
                        taskFutures.put(taskId, seedingFuture);
                        break;
                    }
                    if ((System.currentTimeMillis() - finishMs) / 1000 >= seedSeconds) {
                        break;
                    }
                }

                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownloadCancelledException("任务取消", taskId);
        } finally {
            TorrentHandle handle = context.handleRef.get();
            if (handle != null && !context.transferred.get()) {
                try {
                    synchronized (sessionLock) {
                        globalSessionManager.remove(handle);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 做种阶段:在独立线程池中运行,不占用下载并发槽位。
     * 持有交接过来的 torrent 句柄,周期性更新进度/速度,做种时长耗尽后清理暂存目录并完成任务。
     */
    private void executeSeeding(Long taskId, ActiveDownloadContext context, int seedSeconds) {
        long finishMs = System.currentTimeMillis();
        try {
            while (true) {
                checkCancellation(taskId, context);
                TorrentStatus status;
                synchronized (sessionLock) {
                    TorrentHandle handle = context.handleRef.get();
                    if (handle == null || !handle.isValid()) {
                        throw new IllegalStateException("做种句柄已失效");
                    }
                    status = handle.status();
                }
                updateProgress(taskId, 100, status.totalDone(), status.totalWanted(),
                        status.downloadRate(), status.uploadRate());
                if ((System.currentTimeMillis() - finishMs) / 1000 >= seedSeconds) {
                    break;
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markCancelled(taskId, "任务已取消");
            return;
        } catch (DownloadCancelledException e) {
            markCancelled(taskId, "任务已取消");
            return;
        } catch (Exception e) {
            log.error("executeSeeding error, taskId={}", taskId, e);
            failTask(taskId, "做种失败: " + e.getMessage());
            return;
        } finally {
            synchronized (sessionLock) {
                TorrentHandle handle = context.handleRef.get();
                if (handle != null) {
                    try {
                        globalSessionManager.remove(handle);
                    } catch (Exception ignored) {
                    }
                }
            }
            activeContexts.remove(taskId);
            taskFutures.remove(taskId);
        }

        // 做种时长耗尽,正常收尾
        try {
            ResourceDownloadTask latest = taskRepository.findById(taskId).orElse(null);
            if (latest != null && latest.getTempDir() != null && !latest.getTempDir().isBlank()) {
                deleteDirectoryQuietly(Paths.get(latest.getTempDir()));
            }
            Path finalPath = latest != null && latest.getFinalPath() != null
                    ? Paths.get(latest.getFinalPath())
                    : null;
            completeTask(taskId, finalPath);
        } catch (Exception e) {
            log.error("做种完成收尾失败, taskId={}", taskId, e);
            failTask(taskId, "做种完成但收尾失败: " + e.getMessage());
        }
    }

    private TorrentHandle addTorrentAndWaitHandle(String magnet, Path tempDir, Long taskId, ActiveDownloadContext context) throws InterruptedException {        synchronized (sessionLock) {
            List<String> before = listHandleKeys(globalSessionManager.getTorrentHandles());
            globalSessionManager.download(magnet, tempDir.toFile(), TorrentFlags.AUTO_MANAGED);
            return waitForNewHandle(before, taskId, context);
        }
    }

    private TorrentHandle waitForNewHandle(List<String> existingKeys, Long taskId, ActiveDownloadContext context) throws InterruptedException {
        for (int i = 0; i < HANDLE_WAIT_SECONDS; i++) {
            checkCancellation(taskId, context);
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
        activeSpeeds.put(taskId, new long[]{downloadBytesPerSec, uploadBytesPerSec});
        String down = formatSpeed(downloadBytesPerSec);
        String up = formatSpeed(uploadBytesPerSec);
        task.setSpeedText("↓" + down + " / ↑" + up);
        task.setOutputMessage(appendMessage(task.getOutputMessage(),
                "progress=" + task.getProgressPercent() + "%, downloaded=" + downloadedBytes + " bytes, total=" + totalBytes + " bytes, download=" + down + ", upload=" + up));
        taskRepository.save(task);
        broadcastProgress();
    }

    private String appendTrackers(String magnet) {
        Set<String> candidates = collectCombinedTrackers();
        if (candidates.isEmpty()) {
            return magnet;
        }

        Set<String> existing = parseMagnetTrackerValues(magnet);
        StringBuilder merged = new StringBuilder(magnet);
        for (String tracker : candidates) {
            String key = tracker.toLowerCase(Locale.ROOT);
            if (existing.contains(key)) {
                continue;
            }
            existing.add(key);
            merged.append("&tr=").append(percentEncodeTracker(tracker));
        }
        return merged.toString();
    }

    /**
     * 收集新下载任务会附加的所有 Tracker：自定义 Tracker + 订阅 Tracker 列表，大小写不敏感去重。
     */
    private Set<String> collectCombinedTrackers() {
        Set<String> seen = new HashSet<>();
        Set<String> candidates = new LinkedHashSet<>();
        String custom = siteConfigService.getResourceCustomTrackers();
        if (custom != null && !custom.isBlank()) {
            for (String raw : custom.split("[\\r\\n,]")) {
                String tracker = raw == null ? "" : raw.trim();
                if (!tracker.isEmpty() && seen.add(tracker.toLowerCase(Locale.ROOT))) {
                    candidates.add(tracker);
                }
            }
        }
        for (String tracker : trackerListService.getSubscribedTrackers()) {
            if (seen.add(tracker.toLowerCase(Locale.ROOT))) {
                candidates.add(tracker);
            }
        }
        return candidates;
    }

    /**
     * 查看新下载任务最终会附加的 Tracker 列表（自定义 + 订阅，大小写不敏感去重）。
     */
    public CombinedTrackerListVO getCombinedTrackerList() {
        Set<String> custom = new LinkedHashSet<>();
        String configured = siteConfigService.getResourceCustomTrackers();
        if (configured != null && !configured.isBlank()) {
            for (String raw : configured.split("[\\r\\n,]")) {
                String tracker = raw == null ? "" : raw.trim();
                if (!tracker.isEmpty()) {
                    custom.add(tracker);
                }
            }
        }
        List<String> subscribed = new ArrayList<>(trackerListService.getSubscribedTrackers());

        Set<String> seen = new HashSet<>();
        List<String> combined = new ArrayList<>();
        for (String tracker : custom) {
            if (seen.add(tracker.toLowerCase(Locale.ROOT))) {
                combined.add(tracker);
            }
        }
        for (String tracker : subscribed) {
            if (seen.add(tracker.toLowerCase(Locale.ROOT))) {
                combined.add(tracker);
            }
        }
        return new CombinedTrackerListVO(new ArrayList<>(custom), subscribed, combined);
    }

    /**
     * 解析磁力链接中已有的 tr 参数值（百分号解码后转小写），用于精确去重。
     * 相比子串 contains 匹配，可避免前缀子串误判与大小写不一致导致的重复。
     */
    private Set<String> parseMagnetTrackerValues(String magnet) {
        Set<String> values = new HashSet<>();
        int queryStart = magnet.indexOf('?');
        if (queryStart < 0) {
            return values;
        }
        String query = magnet.substring(queryStart + 1);
        for (String param : query.split("[&;]")) {
            if (param.length() <= 3 || !param.regionMatches(true, 0, "tr=", 0, 3)) {
                continue;
            }
            values.add(percentDecode(param.substring(3)).toLowerCase(Locale.ROOT));
        }
        return values;
    }

    /**
     * RFC 3986 百分号编码（保留 -._~ 与字母数字，空格编码为 %20）。
     */
    private String percentEncodeTracker(String value) {
        StringBuilder sb = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return sb.toString();
    }

    /**
     * 仅解码百分号序列，保留 + 字面值（与磁力链接的 RFC 3986 语义一致）。
     */
    private String percentDecode(String value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) | lo);
                    i += 2;
                    continue;
                }
            }
            byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            out.write(bytes, 0, bytes.length);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
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
        activeSpeeds.remove(taskId);
        task.setSpeedText(null);
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
        if (task.getStatus() == ResourceDownloadTask.DownloadStatus.CANCELLED) {
            return;
        }
        task.setStatus(ResourceDownloadTask.DownloadStatus.CANCELLED);
        task.setFinishedAt(Timestamp.from(Instant.now()));
        activeSpeeds.remove(taskId);
        task.setSpeedText(null);
        task.setOutputMessage(appendMessage(task.getOutputMessage(), message));
        taskRepository.save(task);
        broadcastProgress();
        trimTerminalTasksIfNeeded();
    }

    private void markStalled(Long taskId, String message) {
        ResourceDownloadTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        if (task.getStatus() == ResourceDownloadTask.DownloadStatus.STALLED) {
            return;
        }
        task.setStatus(ResourceDownloadTask.DownloadStatus.STALLED);
        task.setFinishedAt(Timestamp.from(Instant.now()));
        task.setErrorMessage(message);
        activeSpeeds.remove(taskId);
        task.setSpeedText(null);
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
        // 暂存目录与任务强绑定：被裁剪的任务记录同时清理对应暂存文件。
        for (ResourceDownloadTask task : toDelete) {
            if (task.getTempDir() != null && !task.getTempDir().isBlank()) {
                deleteDirectoryQuietly(Paths.get(task.getTempDir()));
            }
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
        // 阶段 1：通知所有在途下载任务停止（协作式标记，不中断线程）。
        // 避免 shutdownNow() 的 Thread.interrupt() 在停机期间再次触发 H2 内嵌库的中断不安全问题。
        for (ActiveDownloadContext context : new ArrayList<>(activeContexts.values())) {
            context.cancelled.set(true);
        }

        // 阶段 2：拒绝新任务，等待在途任务在取消标记下自行收尾（轮询 <=1s，句柄等待循环也会检查取消）。
        ThreadPoolExecutor executorToStop = null;
        synchronized (executorLock) {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                executorToStop = executor;
            }
        }
        if (executorToStop != null) {
            try {
                if (!executorToStop.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("下载线程在 {}s 内未全部退出，强制中断兜底", SHUTDOWN_GRACE_SECONDS);
                    executorToStop.shutdownNow();
                    executorToStop.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executorToStop.shutdownNow();
            }
        }

        // 阶段 2.5：等待做种线程退出（取消标记已置位，做种线程会在下一个轮询点退出）
        seedingExecutor.shutdown();
        try {
            if (!seedingExecutor.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                seedingExecutor.shutdownNow();
                seedingExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            seedingExecutor.shutdownNow();
        }
        activeContexts.clear();

        // 阶段 3：此刻无任何线程再访问 jlibtorrent 会话或数据库，才安全停止会话。
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
        Map<String, Object> payload = progressPayload();
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
        private final AtomicBoolean transferred = new AtomicBoolean(false);
    }

    private static final class DownloadStalledException extends RuntimeException {
        private DownloadStalledException(String message) {
            super(message);
        }
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

    private String[] resolveSpeeds(ResourceDownloadTask task) {
        // 终态任务不再展示速度：speedText 记录的是完成前的瞬时速度,展示无意义
        if (task.getStatus() != null && TERMINAL_STATUSES.contains(task.getStatus())) {
            return new String[]{null, null};
        }
        String mergedSpeed = task.getSpeedText();
        String downloadSpeedText = null;
        String uploadSpeedText = null;
        // speedText 形如 "↓901.7 KB/s / ↑33.0 KB/s",分隔符是 " / ",不能按 "/" 切分(会切坏 KB/s)
        if (mergedSpeed != null && mergedSpeed.contains(" / ")) {
            String[] parts = mergedSpeed.split(" / ");
            if (parts.length >= 2) {
                downloadSpeedText = parts[0].replace("↓", "").replace("↑", "").trim();
                uploadSpeedText = parts[1].replace("↓", "").replace("↑", "").trim();
            }
        }
        if (downloadSpeedText == null || downloadSpeedText.isBlank()) {
            downloadSpeedText = mergedSpeed == null ? null : mergedSpeed.replace("↓", "").replace("↑", "").trim();
        }
        if (uploadSpeedText == null || uploadSpeedText.isBlank()) {
            uploadSpeedText = "0 B/s";
        }
        return new String[]{downloadSpeedText, uploadSpeedText};
    }

    private ResourceSearchVO.DownloadTask toTaskVO(ResourceDownloadTask task) {
        String[] speeds = resolveSpeeds(task);
        String downloadSpeedText = speeds[0];
        String uploadSpeedText = speeds[1];

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

    private ResourceSearchVO.DownloadTaskSummary toTaskSummaryVO(ResourceDownloadTask task) {
        String[] speeds = resolveSpeeds(task);
        String downloadSpeedText = speeds[0];
        String uploadSpeedText = speeds[1];

        return ResourceSearchVO.DownloadTaskSummary.builder()
                .id(task.getId())
                .title(task.getTitle())
                .status(task.getStatus() != null ? task.getStatus().name() : null)
                .progressPercent(task.getProgressPercent())
                .downloadedBytes(task.getDownloadedBytes())
                .totalBytes(task.getTotalBytes())
                .downloadSpeedText(downloadSpeedText)
                .uploadSpeedText(uploadSpeedText)
                .fileSize(task.getFileSize())
                .publishDate(task.getPublishDate())
                .subgroupName(task.getSubgroupName())
                .typeName(task.getTypeName())
                .libraryId(task.getLibrary() != null ? task.getLibrary().getId() : null)
                .libraryName(task.getLibrary() != null ? task.getLibrary().getName() : null)
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .build();
    }
}
